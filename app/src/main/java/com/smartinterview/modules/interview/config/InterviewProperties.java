package com.smartinterview.modules.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.interview")
public record InterviewProperties(
    int questionCount
) {
}
