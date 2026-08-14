# DataRobort 前端镜像（Vite 构建 → nginx 静态托管 + API 反向代理）
# 构建：cd datarobort && docker build -f docker/frontend/Dockerfile -t datarobort-frontend:0.1.0 .
FROM node:20-alpine AS build
WORKDIR /app
COPY datarobort-frontend/package.json datarobort-frontend/package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY datarobort-frontend/ ./
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
