# Smart Interviewer

智能 AI 面试平台（个人独立实现）：简历分析、模拟面试、RAG 知识库问答。

## 技术栈

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Java | 25 | 开发语言（虚拟线程） |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | AI 集成框架（OpenAI 兼容接入） |
| PostgreSQL + pgvector | 16 | 关系数据库 + 向量存储 |
| Redis + Redisson | 7 / 4.0.0 | 缓存 + 分布式锁 + Stream 异步队列 |
| Flyway | - | 数据库 schema 版本化管理 |
| MapStruct | 1.6.3 | 对象映射 |
| SpringDoc OpenAPI | 3.0.2 | API 文档 |
| Micrometer | - | 指标埋点（Prometheus） |
| Gradle | 9.6.1 | 构建工具 |

## 快速开始

```bash
# 1. 启动基础设施（PostgreSQL + Redis）
docker compose -f docker-compose.dev.yml up -d

# 2. 配置环境变量
cp .env.example .env   # 填入 AI_API_KEY

# 3. 启动后端
./gradlew :app:bootRun
```

## 常用命令

```bash
./gradlew :app:compileJava          # 编译
./gradlew :app:test --no-daemon     # 测试
./gradlew :app:bootRun              # 启动后端（默认 8080）
```

接口文档：启动后访问 http://localhost:8080/swagger-ui.html

## 项目结构

```
app/src/main/java/com/smartinterview/
├── common/            # 通用能力：统一响应、异常体系、AI 调用、异步模板
├── infrastructure/    # 技术基础设施：文件、Redis、MapStruct 映射
└── modules/           # 业务模块（每个模块自包含 MVC 分层）
app/src/main/resources/
├── application.yml    # 配置
├── db/migration/      # Flyway 迁移脚本
└── prompts/           # StringTemplate Prompt 模板
```

## 开发约定

见 [AGENTS.md](AGENTS.md)：Controller → Service → Repository 分层、事务只放 Service、统一 `Result<T>` 响应、`BusinessException` 异常体系、外部调用不进事务、敏感信息只放 `.env`。
