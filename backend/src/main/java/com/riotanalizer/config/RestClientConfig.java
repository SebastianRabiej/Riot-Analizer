package com.riotanalizer.config;

import com.riotanalizer.ratelimit.RiotRateLimitObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Two RestClient beans, one per Riot host. The API key header is attached
 * on every request. All outbound calls still go through {@code RiotRateLimiter}
 * inside {@code RiotApiClient}. Every response's rate-limit headers are passed
 * to {@link RiotRateLimitObserver} (Riot's own count, shown in the admin view).
 */
@Configuration
public class RestClientConfig {

    public static final String PLATFORM = "platformRestClient";
    public static final String REGIONAL = "regionalRestClient";

    private final RiotProperties props;
    private final RiotRateLimitObserver rateLimitObserver;

    public RestClientConfig(RiotProperties props, RiotRateLimitObserver rateLimitObserver) {
        this.props = props;
        this.rateLimitObserver = rateLimitObserver;
    }

    private ClientHttpRequestInterceptor observeRateLimits() {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            rateLimitObserver.record(response.getStatusCode().value(), response.getHeaders());
            return response;
        };
    }

    @Bean(PLATFORM)
    public RestClient platformRestClient() {
        return RestClient.builder()
                .baseUrl(props.getPlatformHost())
                .defaultHeader("X-Riot-Token", props.getApiKey() == null ? "" : props.getApiKey())
                .requestInterceptor(observeRateLimits())
                .build();
    }

    @Bean(REGIONAL)
    public RestClient regionalRestClient() {
        return RestClient.builder()
                .baseUrl(props.getRegionalHost())
                .defaultHeader("X-Riot-Token", props.getApiKey() == null ? "" : props.getApiKey())
                .requestInterceptor(observeRateLimits())
                .build();
    }
}
