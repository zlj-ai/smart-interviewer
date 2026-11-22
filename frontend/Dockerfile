# ==============================================================================
# 第一阶段：前端构建（Node 22 + Vite）
# ==============================================================================
FROM docker.m.daocloud.io/node:22-alpine AS builder
WORKDIR /app

# 先装依赖（锁文件不变则命中缓存层）
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund

COPY . .
RUN npm run build

# ==============================================================================
# 第二阶段：Nginx 托管静态资源并反向代理后端
# ==============================================================================
FROM docker.m.daocloud.io/nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
