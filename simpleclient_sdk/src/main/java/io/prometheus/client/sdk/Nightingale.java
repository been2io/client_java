package io.prometheus.client.sdk;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;


public class Nightingale {
    private static final Logger logger = Logger.getLogger(Nightingale.class.getName());
    static final String defaultUrl = "http://localhost:2080/v1/push";
    private final Encoder encoder;

    public Nightingale(String url, int batchSize, int interval, String tenantId, Map<String, String> globalTags) {
        this.encoder = new Encoder(url, 100, interval, tenantId,globalTags);
    }

    public Nightingale(String url,int interval, Map<String, String> globalTags) {
        this(url, 1000, interval, "1", globalTags);
    }


    /**
     * Push samples from the given registry to Graphite.
     */
    public void push(CollectorRegistry registry) {
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
