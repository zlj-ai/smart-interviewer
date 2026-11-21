package com.smartinterview.modules.interview.model;

/**
 * 面试方向。后续可由 Tool Calling Skill 体系替代硬编码枚举（模型自主选择考察范围）。
 */
public enum InterviewDirection {

  JAVA_BACKEND("Java 后端"),
  FRONTEND("前端"),
  ALGORITHM("算法"),
  SYSTEM_DESIGN("系统设计"),
  DATABASE("数据库"),
  DISTRIBUTED_SYSTEMS("分布式系统"),
  PROJECT_DEEP_DIVE("项目深挖"),
  BEHAVIORAL("行为面试");

  private final String description;

  InterviewDirection(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
