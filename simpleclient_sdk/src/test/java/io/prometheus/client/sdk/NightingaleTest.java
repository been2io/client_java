package io.prometheus.client.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.prometheus.client.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NightingaleTest {
  @Test
  public  void testMarshalBatch(){
    CollectorRegistry registry = new CollectorRegistry();

    for (int i=0;i<1;i++){
      Gauge labels = Gauge.build().name("labels"+i).help("help").labelNames("l").register(registry);
      labels.labels("fo*o").inc();
    }
    Encoder encoder =new Encoder("dsadf",100,1000,"11",null);
    for (Collector.MetricFamilySamples metricFamilySamples : Collections.list(registry.metricFamilySamples())) {
      for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
        encoder.add(sample);
      }
    }
    String body=encoder.marshalBatch(2212111l);
    System.out.println(body);
  }
  @Test
  public void testPush() throws Exception {
    // Create a metric.
    CollectorRegistry registry = new CollectorRegistry();
    Gauge labels = Gauge.build().name("test_test").help("help").labelNames("l").register(registry);
    labels.labels("fo*o").inc();
    // Push.
    Nightingale g = new Nightingale("http://n9e.example.com/api/transfer/push",10,null);
    g.push(registry);
  }
  @Test
  public void testPushLoop(){
    Metrics metric= new Metrics("infra","metrics","test");
    Gauge labels = Gauge.build().name("test_test3").help("help").labelNames("l").register();
    labels.labels("foo").inc();
    labels.labels("foo").inc();
    labels.labels("foo").inc();
    Histogram s= Histogram.build().name("histogram1").help("help").labelNames("sl").register();
    s.labels("l1").observe(0.88);
    s.labels("l1").observe(0.77);
    s.labels("l2").observe(0.66);
    Gauge labels2 = Gauge.build().name("test_test4").help("help").labelNames("l").register();
    labels2.labels("foo").inc();
    metric.StartPushLoop(1,"http://10.110.20.100:8080/api/transfer/push");
    try {
      Thread.sleep(1000*60);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  @Test
  public void testExport() throws IOException {
    Metrics metric= new Metrics("infra","metrics","test");
    Gauge labels = Gauge.build().name("test_test2").help("help").labelNames("l","l2").register();
    labels.labels("foo","f2").inc();
    metric.StartPullHttpServer(9000);
    try {
      Thread.sleep(1000*60);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
