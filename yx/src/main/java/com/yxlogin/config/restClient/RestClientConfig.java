package com.yxlogin.config.restClient;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient restClient(){
        ClientHttpRequestFactorySettings settings=ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        ClientHttpRequestFactory requestFactory= ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
