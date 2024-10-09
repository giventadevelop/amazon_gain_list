package com.amazon.gain.list;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AmazonProperties.class)
public class AmazonGainListApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmazonGainListApplication.class, args);
    }

}
