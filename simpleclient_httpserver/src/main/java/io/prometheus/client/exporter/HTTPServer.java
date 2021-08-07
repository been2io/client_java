package io.prometheus.client.exporter;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.MetricNameFilter;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Expose Prometheus metrics using a plain Java HttpServer.
 * <p>
 * Example Usage:
 * <pre>
 * {@code
 * HTTPServer server = new HTTPServer(1234);
 * }
 * </pre>
 * */
public class HTTPServer {

    static {
        if (!System.getProperties().containsKey("sun.net.httpserver.maxReqTime")) {
            System.setProperty("sun.net.httpserver.maxReqTime", "60");
        }

        if (!System.getProperties().containsKey("sun.net.httpserver.maxRspTime")) {
            System.setProperty("sun.net.httpserver.maxRspTime", "600");
        }
    }

    private static class LocalByteArray extends ThreadLocal<ByteArrayOutputStream> {
        @Override
        protected ByteArrayOutputStream initialValue()
        {
            return new ByteArrayOutputStream(1 << 20);
        }
    }

    /**
     * Handles Metrics collections from the given registry.
     */
    public static class HTTPMetricHandler implements HttpHandler {
        private final CollectorRegistry registry;
        private final LocalByteArray response = new LocalByteArray();
        private final Config config;
        private final static String HEALTHY_RESPONSE = "Exporter is Healthy.";

        HTTPMetricHandler(CollectorRegistry registry) {
            this(registry, null);
        }

        HTTPMetricHandler(CollectorRegistry registry, Config config) {
            this.registry = registry;
            this.config = config == null ? new Config() : config;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getRawQuery();

            String contextPath = t.getHttpContext().getPath();
            ByteArrayOutputStream response = this.response.get();
            response.reset();
            OutputStreamWriter osw = new OutputStreamWriter(response, Charset.forName("UTF-8"));
            if ("/-/healthy".equals(contextPath)) {
                osw.write(HEALTHY_RESPONSE);
            } else {
                String contentType = TextFormat.chooseContentType(t.getRequestHeaders().getFirst("Accept"));
                t.getResponseHeaders().set("Content-Type", contentType);
                MetricNameFilter filter = new MetricNameFilter.Builder()
                        .includeNames(parseQuery(query))
                        .includePrefixes(config.getIncludedPrefixes())
                        .excludePrefixes(config.getExcludedPrefixes())
                        .build();
                TextFormat.writeFormat(contentType, osw, registry.filteredMetricFamilySamples(filter));
            }

            osw.close();

            if (shouldUseCompression(t)) {
                t.getResponseHeaders().set("Content-Encoding", "gzip");
                t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
                final GZIPOutputStream os = new GZIPOutputStream(t.getResponseBody());
                try {
                    response.writeTo(os);
                } finally {
                    os.close();
                }
            } else {
                t.getResponseHeaders().set("Content-Length",
                        String.valueOf(response.size()));
                t.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size());
                response.writeTo(t.getResponseBody());
            }
            t.close();
        }

    }

    protected static boolean shouldUseCompression(HttpExchange exchange) {
        List<String> encodingHeaders = exchange.getRequestHeaders().get("Accept-Encoding");
        if (encodingHeaders == null) return false;

        for (String encodingHeader : encodingHeaders) {
            String[] encodings = encodingHeader.split(",");
            for (String encoding : encodings) {
                if (encoding.trim().equalsIgnoreCase("gzip")) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static Set<String> parseQuery(String query) throws IOException {
        Set<String> names = new HashSet<String>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1 && URLDecoder.decode(pair.substring(0, idx), "UTF-8").equals("name[]")) {
                    names.add(URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            }
        }
        return names;
    }


    static class NamedDaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

        private final int poolNumber = POOL_NUMBER.getAndIncrement();
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadFactory delegate;
        private final boolean daemon;

        NamedDaemonThreadFactory(ThreadFactory delegate, boolean daemon) {
            this.delegate = delegate;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setName(String.format("prometheus-http-%d-%d", poolNumber, threadNumber.getAndIncrement()));
            t.setDaemon(daemon);
            return t;
        }

        static ThreadFactory defaultThreadFactory(boolean daemon) {
            return new NamedDaemonThreadFactory(Executors.defaultThreadFactory(), daemon);
        }
    }

    protected final HttpServer server;
    protected final ExecutorService executorService;

    /**
     * Configure the HTTPServer to include / exclude metrics by name prefix.
     */
    public static class Config {

        private final Collection<String> includedPrefixes;
        private final Collection<String> excludedPrefixes;

        /**
         * Empty config means nothing is filtered; all metrics are exported.
         */
        public Config() {
            includedPrefixes = Collections.emptyList();
            excludedPrefixes = Collections.emptyList();
        }

        /**
         * If {@code includedNamePrefixes} is not empty, only metrics with a name starting with one of these
         * prefixes will be exported. If {@code excludedNamePrefixes} is not empty, metrics with a name
         * starting with one of these prefixes will not be exported. If both parameters are not empty, metrics
         * must match both criteria in order to be exported.
         */
        public Config(Collection<String> includedNamePrefixes, Collection<String> excludedNamePrefixes) {
            this.includedPrefixes = unmodifiableCopy(includedNamePrefixes);
            this.excludedPrefixes = unmodifiableCopy(excludedNamePrefixes);
        }

        private Collection<String> unmodifiableCopy(Collection<String> collection) {
            if (collection == null) {
                return Collections.emptyList();
            } else {
                return Collections.unmodifiableCollection(new ArrayList<String>(collection));
            }
        }

        public Collection<String> getIncludedPrefixes() {
            return includedPrefixes;
        }

        public Collection<String> getExcludedPrefixes() {
            return excludedPrefixes;
        }
    }

    /**
     * Start an HTTP server serving the default Prometheus registry using non-daemon threads.
     */
    public HTTPServer(int port) throws IOException {
        this(port, null);
    }

    /**
     * Like {@link #HTTPServer(int)}, but with an additional {@link Config} parameter to configure
     * which metrics should be exported.
     */
    public HTTPServer(int port, Config config) throws IOException {
        this(port, false, config);
    }

    /**
     * Start an HTTP server serving the default Prometheus registry.
     */
    public HTTPServer(int port, boolean daemon) throws IOException {
        this(port, daemon, null);
    }

    /**
     * Like {@link #HTTPServer(int, boolean)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(int port, boolean daemon, Config config) throws IOException {
        this(new InetSocketAddress(port), CollectorRegistry.defaultRegistry, daemon, config);
    }

    /**
     * Start an HTTP server serving the default Prometheus registry using non-daemon threads.
     */
    public HTTPServer(String host, int port) throws IOException {
        this(host, port, null);
    }

    /**
     * Like {@link #HTTPServer(String, int)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(String host, int port, Config config) throws IOException {
        this(host, port, false, config);
    }

    /**
     * Start an HTTP server serving the default Prometheus registry.
     */
    public HTTPServer(String host, int port, boolean daemon) throws IOException {
        this(host, port, daemon, null);
    }

    /**
     * Like {@link #HTTPServer(String, int, boolean)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(String host, int port, boolean daemon, Config config) throws IOException {
        this(new InetSocketAddress(host, port), CollectorRegistry.defaultRegistry, daemon, config);
    }

    /**
     * Start an HTTP server serving Prometheus metrics from the given registry using non-daemon threads.
     */
    public HTTPServer(InetSocketAddress addr, CollectorRegistry registry) throws IOException {
        this(addr, registry, null);
    }

    /**
     * Like {@link #HTTPServer(InetSocketAddress, CollectorRegistry)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(InetSocketAddress addr, CollectorRegistry registry, Config config) throws IOException {
        this(addr, registry, false, config);
    }

    /**
     * Start an HTTP server serving Prometheus metrics from the given registry.
     */
    public HTTPServer(InetSocketAddress addr, CollectorRegistry registry, boolean daemon) throws IOException {
        this(addr, registry, daemon, null);
    }

    /**
     * Like {@link #HTTPServer(InetSocketAddress, CollectorRegistry, boolean)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(InetSocketAddress addr, CollectorRegistry registry, boolean daemon, Config config) throws IOException {
        this(HttpServer.create(addr, 3), registry, daemon, config);
    }

    /**
     * Start an HTTP server serving Prometheus metrics from the given registry using the given {@link HttpServer}.
     * The {@code httpServer} is expected to already be bound to an address
     */
    public HTTPServer(HttpServer httpServer, CollectorRegistry registry, boolean daemon) throws IOException {
        this(httpServer, registry, daemon, null);
    }


    /**
     * Like {@link #HTTPServer(HttpServer, CollectorRegistry, boolean)}, but with an additional {@link Config}
     * parameter to configure which metrics should be exported.
     */
    public HTTPServer(HttpServer httpServer, CollectorRegistry registry, boolean daemon, Config config) throws IOException {
        if (httpServer.getAddress() == null)
            throw new IllegalArgumentException("HttpServer hasn't been bound to an address");

        server = httpServer;
        HttpHandler mHandler = new HTTPMetricHandler(registry, config);
        server.createContext("/", mHandler);
        server.createContext("/metrics", mHandler);
        server.createContext("/-/healthy", mHandler);
        executorService = Executors.newFixedThreadPool(5, NamedDaemonThreadFactory.defaultThreadFactory(daemon));
        server.setExecutor(executorService);
        start(daemon);
    }

    /**
     * Start a HTTP server by making sure that its background thread inherit proper daemon flag.
     */
    private void start(boolean daemon) {
        if (daemon == Thread.currentThread().isDaemon()) {
            server.start();
        } else {
            FutureTask<Void> startTask = new FutureTask<Void>(new Runnable() {
                @Override
                public void run() {
                    server.start();
                }
            }, null);
            NamedDaemonThreadFactory.defaultThreadFactory(daemon).newThread(startTask).start();
            try {
                startTask.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Unexpected exception on starting HTTPSever", e);
            } catch (InterruptedException e) {
                // This is possible only if the current tread has been interrupted,
                // but in real use cases this should not happen.
                // In any case, there is nothing to do, except to propagate interrupted flag.
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(0);
        executorService.shutdown(); // Free any (parked/idle) threads in pool
    }

    /**
     * Gets the port number.
     */
    public int getPort() {
        return server.getAddress().getPort();
    }
}
