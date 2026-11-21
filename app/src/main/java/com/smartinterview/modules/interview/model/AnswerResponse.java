package com.smartinterview.modules.interview.model;

import java.time.Instant;

public record AnswerResponse(
    Long id,
    Long questionId,
    Long sessionId,
    String answerText,
    Integer score,
    String feedback,
    String strengths,
    String improvements,
    String status,
    Integer attempt,
    Instant createdAt
) {
}
