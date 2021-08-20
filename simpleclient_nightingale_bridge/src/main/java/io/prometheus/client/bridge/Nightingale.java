package io.prometheus.client.bridge;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Export metrics in the Graphite plaintext format.
 * <p>
 * <pre>
 * {@code
 *  Graphite g = new Graphite("localhost", 2003);
 *  // Push the default registry once.
 *  g.push(CollectorRegistry.defaultRegistry);
 *
 *  // Push the default registry every 60 seconds.
 *  Thread thread = g.start(CollectorRegistry.defaultRegistry, 60);
 *  // Stop pushing.
 *  thread.interrupt();
 *  thread.join();
 * }
 * </pre>
 * <p>
 */
public class Nightingale {
    private static final Logger logger = Logger.getLogger(Nightingale.class.getName());

    private final Encoder encoder;
    public Nightingale(String url) {
        this.encoder = new Encoder(url, 100, 10, "sdk");
    }

    /**
     * Push samples from the given registry to Graphite.
     */
    public void push(CollectorRegistry registry)  {
        for (Collector.MetricFamilySamples metricFamilySamples : Collections.list(registry.metricFamilySamples())) {
            for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                this.encoder.add(sample);
            }
        }
        this.encoder.end();
    }

    /**
     * Push samples from the given registry to Graphite every minute.
     */
    public Thread start(CollectorRegistry registry) {
        return start(registry, 60);
    }

    /**
     * Push samples from the given registry to Graphite at the given interval.
     */
    public Thread start(CollectorRegistry registry, int intervalSeconds) {
        Thread thread = new PushThread(registry, intervalSeconds);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private class PushThread extends Thread {
        private final CollectorRegistry registry;
        private final int intervalSeconds;

        PushThread(CollectorRegistry registry, int intervalSeconds) {
            this.registry = registry;
            this.intervalSeconds = intervalSeconds;
        }

        public void run() {
            long waitUntil = System.currentTimeMillis();
            while (true) {
                push(registry);
                long now = System.currentTimeMillis();
                // We may skip some pushes if we're falling behind.
                while (now >= waitUntil) {
                    waitUntil += intervalSeconds * 1000;
                }
                try {
                    Thread.sleep(waitUntil - now);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
