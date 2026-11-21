package com.smartinterview.modules.voiceinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.voice")
public record VoiceProperties(
    int recentWindow,
    int charBudget
) {
}
