package com.riotanalizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean wildcard = allowedOrigins.length == 1 && "*".equals(allowedOrigins[0]);
        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
        if (wildcard) {
            // allowCredentials cannot be combined with a wildcard origin
            mapping.allowedOriginPatterns("*").allowCredentials(false);
        } else {
            mapping.allowedOrigins(allowedOrigins).allowCredentials(true);
        }
    }
}
