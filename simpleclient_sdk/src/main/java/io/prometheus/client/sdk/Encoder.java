package io.prometheus.client.sdk;

import io.prometheus.client.Collector;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.prometheus.client.sdk.StringEscapeUtils.escapeJson;

public class Encoder {
    private final HttpClient client;
    private ArrayList<Collector.MetricFamilySamples.Sample> samples;
    private static final Logger logger = Logger.getLogger(Encoder.class.getName());
    private final String url;
    private final int batchSize;
    private final long stepS;
    private final String nid;
    private StringBuilder builder = new StringBuilder();
    private final Map<String, String> tags;
    private String tagStr;

    Encoder(String url, int batchSize, long stepS, String nid, Map<String, String> tags) {
        this.stepS = stepS;
        this.nid = nid;
        this.url = url;
        this.client = HttpClients.createDefault();
        this.batchSize = batchSize;
        this.samples = new ArrayList<>(batchSize);
        this.tags = tags;
        if (this.tags!=null){
            this.tagStr = this.tags.entrySet().stream().map((e) -> {
                return escapeJson(e.getKey()) + "=" + escapeJson(e.getValue());
            }).collect(Collectors.joining(","));
        }



    }


    void add(Collector.MetricFamilySamples.Sample sample) {
        this.samples.add(sample);
    }


    protected Long generateTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    void writeMessage(StringBuilder sb, Collector.MetricFamilySamples.Sample sample,Long timestamp) {
        if (sample.labelNames.size() != sample.labelValues.size()) {
            return;
        }
        String type = "GAUGE";
        String name = sample.name;
        sb.append("{\"").append("timestamp").append("\":").append(timestamp)
                .append(",\"metric\":\"").append(escapeJson(name)).append('"')
                .append(",\"counterType\":\"").append(type).append('"')
                .append(",\"step\":").append(stepS);
        sb.append(",\"nid\":\"").append(nid).append('"');
        writeTags(sb, sample);
        sb.append(",\"value\":");
        sb.append(sample.value);
        sb.append("}");
    }

    void writeTags(StringBuilder sb, Collector.MetricFamilySamples.Sample sample) {
        if (sample.labelNames.size() == 0) {
            return;
        }
        sb.append(",\"tags\":\"");
        sb.append(this.tagStr);
        for (int i = 0; i < sample.labelNames.size(); ++i) {
            sb.append(',').append(escapeJson(sample.labelNames.get(i))).append("=")
                    .append(escapeJson(sample.labelValues.get(i).replace(" ", "-")));
        }
        sb.append('"');
    }

    void end() {
        send();
    }

    String marshalBatch(Long timestamp) {

        builder.append("[");
        int len = this.samples.size();
        for (int i = 0; i < len; i++) {
            if (i != 0) {
                builder.append(",\n");
            }
            writeMessage(builder, this.samples.get(i),timestamp);
        }
        builder.append("]");
        String body = builder.toString();
        builder.setLength(0);
        this.samples.clear();
        return body;
    }

    private void send() {
        Long timestamp = generateTimestamp();
        if (this.samples.size() > 0) {
            String body = marshalBatch(timestamp);
            HttpPost post = new HttpPost(this.url);
            StringEntity requestEntity = new StringEntity(
                    body, ContentType.APPLICATION_JSON);
            post.setEntity(requestEntity);
            try {
                HttpResponse response = client.execute(post);
                final HttpEntity entity = response.getEntity();
                String resp = EntityUtils.toString(entity);
                if (!resp.startsWith("{\"dat\":\"ok\"")) {
                    logger.log(Level.WARNING, "nightingale get response" + resp + " from " + this.url);
                }
                if (entity != null) {
                    try (final InputStream inStream = entity.getContent()) {
                        //inStream.read();

                        // do something useful with the response
                    } catch (final IOException ex) {
                        logger.log(Level.WARNING, "Exception " + ex + " get response from " + this.url, ex);

                        // In case of an IOException the connection will be released
                        // back to the connection manager automatically
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception " + e + " pushing to " + this.url, e);
            }

        }
    }
}
