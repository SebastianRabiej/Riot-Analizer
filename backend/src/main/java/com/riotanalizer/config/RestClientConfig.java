package com.riotanalizer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Two RestClient beans, one per Riot host. The API key header is attached
 * on every request. All outbound calls still go through {@code RiotRateLimiter}
 * inside {@code RiotApiClient}.
 */
@Configuration
public class RestClientConfig {

    public static final String PLATFORM = "platformRestClient";
    public static final String REGIONAL = "regionalRestClient";

    private final RiotProperties props;

    public RestClientConfig(RiotProperties props) {
        this.props = props;
    }

    @Bean(PLATFORM)
    public RestClient platformRestClient() {
        return RestClient.builder()
                .baseUrl(props.getPlatformHost())
                .defaultHeader("X-Riot-Token", props.getApiKey() == null ? "" : props.getApiKey())
                .build();
    }

    @Bean(REGIONAL)
    public RestClient regionalRestClient() {
        return RestClient.builder()
                .baseUrl(props.getRegionalHost())
                .defaultHeader("X-Riot-Token", props.getApiKey() == null ? "" : props.getApiKey())
                .build();
    }
}
