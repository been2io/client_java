package io.prometheus.client.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.prometheus.client.Collector;
import org.junit.Test;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.bridge.Nightingale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;

public class NightingaleTest {
  @Test
  public  void testMarshalBatch(){
    CollectorRegistry registry = new CollectorRegistry();

    for (int i=0;i<1;i++){
      Gauge labels = Gauge.build().name("labels"+i).help("help").labelNames("l").register(registry);
      labels.labels("fo*o").inc();
    }
    Encoder encoder =new Encoder("dsadf",100,1000,"11");
    for (Collector.MetricFamilySamples metricFamilySamples : Collections.list(registry.metricFamilySamples())) {
      for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
        encoder.add(sample);
      }
    }
    String body=encoder.marshalBatch();
    System.out.println(body);
  }
  @Test
  public void testPush() throws Exception {
    // Create a metric.
    CollectorRegistry registry = new CollectorRegistry();
    Gauge labels = Gauge.build().name("labels").help("help").labelNames("l").register(registry);
    labels.labels("fo*o").inc();
    // Push.
    Nightingale g = new Nightingale("http://n9e.example.com/api/transfer/push");
    g.push(registry);

  }
}
