package com.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RateLimiterApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(RateLimiterApplication.class, arguments);
    }
}
