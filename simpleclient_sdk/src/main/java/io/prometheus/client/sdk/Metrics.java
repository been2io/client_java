package io.prometheus.client.sdk;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class Metrics {
    private CollectorRegistry registry;
    private HashMap<String, String> tags;

    public Metrics(String bu, String project, String app, Map<String, String> globalTags) {
        this(bu, project, app, globalTags, CollectorRegistry.defaultRegistry);

    }

    public Metrics(String bu, String project, String app, Map<String, String> globalTags, CollectorRegistry registry) {
        this.registry = registry;
        this.tags = new HashMap<>();
        if (globalTags != null) {
            globalTags.forEach((k, v) -> {
                this.tags.put(k, v);
            });
        }
        this.tags.put("bu", bu);
        this.tags.put("project", project);
        this.tags.put("app", app);
        String podName = System.getenv("MY_POD_NAME");
        if (podName == null) {
            podName = ManagementFactory.getRuntimeMXBean().getName();
        }
        this.tags.put("pid", podName);
        this.registry.setGlobalTags(this.tags);
    }

    public void StartPushLoop(int interval) {
        this.StartPushLoop(interval, Nightingale.defaultUrl);
    }

    public void StartPushLoop(int interval, String url) {
        Nightingale nightingale = new Nightingale(url, interval, this.tags);
        nightingale.start(this.registry, interval);
    }

    public HTTPServer StartPullHttpServer(int port) throws IOException {
        HTTPServer server = new HTTPServer(new InetSocketAddress(port), this.registry, false);
        return server;
    }


}
