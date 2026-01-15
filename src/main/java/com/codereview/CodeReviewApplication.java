package com.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.codereview.config.CodeReviewProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(CodeReviewProperties.class)
public class CodeReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewApplication.class, args);
    }
}
