package com.riotanalizer;

import com.riotanalizer.config.RiotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RiotProperties.class)
public class RiotAnalizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiotAnalizerApplication.class, args);
    }
}
