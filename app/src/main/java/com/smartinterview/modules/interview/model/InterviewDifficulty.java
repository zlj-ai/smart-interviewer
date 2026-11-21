package com.smartinterview.modules.interview.model;

public enum InterviewDifficulty {

  EASY("简单"),
  MEDIUM("中等"),
  HARD("困难");

  private final String description;

  InterviewDifficulty(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
