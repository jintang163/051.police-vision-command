package com.police.vision.control.config.intelligence;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchConfig {

    private String[] hosts = {"127.0.0.1:9200"};

    private String scheme = "http";

    private String username;

    private String password;

    private int connectTimeout = 5000;

    private int socketTimeout = 60000;

    private String caseIndex = "police_cases";

    private String opinionIndex = "public_opinion";

    private boolean enabled = true;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        if (!enabled) {
            log.warn(">>>>>>>>>>> Elasticsearch client disabled");
            return null;
        }
        try {
            HttpHost[] httpHosts = new HttpHost[hosts.length];
            for (int i = 0; i < hosts.length; i++) {
                String[] parts = hosts[i].split(":");
                httpHosts[i] = new HttpHost(parts[0], Integer.parseInt(parts[1]), scheme);
            }

            RestClientBuilder builder = RestClient.builder(httpHosts);

            if (username != null && !username.isEmpty() && password != null) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password));
                builder.setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
            }

            builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder
                            .setConnectTimeout(connectTimeout)
                            .setSocketTimeout(socketTimeout));

            ElasticsearchTransport transport = new RestClientTransport(
                    builder.build(), new JacksonJsonpMapper());

            log.info(">>>>>>>>>>> Elasticsearch client initialized, hosts={}", String.join(",", hosts));
            return new ElasticsearchClient(transport);
        } catch (Exception e) {
            log.error(">>>>>>>>>>> Failed to initialize Elasticsearch client", e);
            return null;
        }
    }
}
