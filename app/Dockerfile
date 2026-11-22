# ==============================================================================
# 第一阶段：构建环境（多阶段构建）
# ==============================================================================
FROM docker.m.daocloud.io/eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# 1. 先复制构建配置：依赖不变则命中缓存层，跳过耗时下载
COPY settings.gradle gradlew ./
COPY gradle gradle
COPY app/build.gradle app/

# 2. 预下载依赖（--no-daemon 适合用完即焚的容器环境；失败不阻断，bootJar 会再校验）
RUN ./gradlew dependencies --no-daemon || true

# 3. 复制源码并构建可执行 JAR（跳过测试，测试由 CI 执行）
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test

# ==============================================================================
# 第二阶段：运行环境（轻量 JRE，只带构建产物）
# ==============================================================================
FROM docker.m.daocloud.io/eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /workspace/app/build/libs/*.jar app.jar

EXPOSE 8080

# 容器内时区与编码，保证日志与报告时间戳正确
ENV TZ=Asia/Shanghai JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
