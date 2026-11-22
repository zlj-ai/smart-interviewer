# Smart Interviewer

智能 AI 面试平台（个人独立实现）：简历分析、模拟面试、RAG 知识库问答、语音面试。

## 功能

- **模拟面试**：八个方向 × 三档难度，大模型异步出题（含参考答案与技能标签），逐题作答后自动评估打分
- **RAG 知识库**：上传文档自动分块向量化，提问时查询改写 + 双路召回融合，回答带来源标注与流式输出
- **简历分析**：上传简历自动解析，大模型输出结构化画像（技能/经历/亮点/短板/建议）
- **语音面试**：WebSocket 双向通道，三级上下文压缩（最近窗口/滚动摘要/字符硬预算）控制长会话成本
- **可靠性设计**：会话创建幂等（三级短路）、异步任务失败分类与 attempt fencing、评估报告 PDF 导出

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 25（虚拟线程）· Spring Boot 4.1 · Spring AI 2.0（OpenAI 兼容）· Spring Security + JWT |
| 数据 | PostgreSQL + pgvector（关系 + 向量一库）· Redis + Redisson（缓存/锁/Stream）· Flyway |
| 前端 | Vue 3 + TypeScript + Vite + TailwindCSS 4 · Pinia · Vue Router |
| AI | DashScope（OpenAI 兼容接入）：qwen-plus 对话 + text-embedding-v3 向量 |
| 可观测 | Micrometer + Prometheus + Grafana + Tempo（OTLP） |
| 部署 | Docker Compose（基础设施 + 可观测栈 + 全栈应用） |

## 快速开始

```bash
# 1. 启动全部基础设施与可观测栈
docker compose -f docker-compose.dev.yml up -d

# 2. 配置环境变量（AI_API_KEY 必填，从百炼控制台获取）
cp .env.example .env

# 3a. 开发模式：后端本地跑 + 前端 Vite 热更新
./gradlew :app:bootRun
cd frontend && npm install && npm run dev   # http://localhost:5173

# 3b. 全栈容器化模式（构建前后端镜像，前端 5173、后端 8080）
docker compose -f docker-compose.dev.yml --profile full up -d --build
```

## 可观测

- Grafana 概览大盘：http://localhost:3000（匿名 Admin，含业务指标面板）
- Prometheus：http://localhost:9090
- 业务指标：`interview_sessions_started_total` / `interview_answer_evaluated_total` / `knowledge_vectorized_total`

## 项目结构

```
app/src/main/java/com/smartinterview/
├── common/            # 统一响应、异常、AI（多Provider/结构化输出/分块）、
│                      # Stream 异步模板、分布式锁、事务模板、安全
├── infrastructure/    # 对象存储（S3 兼容）
└── modules/
    ├── user/          # 注册登录、JWT 双 token、RBAC
    ├── interview/     # 会话幂等、题目生成、回答评估（fencing）、报告导出
    ├── knowledgebase/ # 知识库、文档向量化、RAG 问答
    ├── resume/        # 简历上传分析
    └── voiceinterview/# WebSocket 语音通道、三级上下文压缩
```

## 测试

```bash
./gradlew :app:test --no-daemon       # 127 个单元/集成测试
```
