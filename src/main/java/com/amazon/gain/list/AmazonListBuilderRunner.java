package com.amazon.gain.list;


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AmazonListBuilderRunner implements CommandLineRunner {

    private final AmazonListBuilderService amazonListBuilderService;

    public AmazonListBuilderRunner(AmazonListBuilderService amazonListBuilderService) {
        this.amazonListBuilderService = amazonListBuilderService;
    }

    @Override
    public void run(String... args) {
        amazonListBuilderService.processOrders();
    }
}
