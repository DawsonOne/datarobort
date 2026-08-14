# DataRobort 部署文档（v1.0）

> 一键部署：`docker compose up -d --build`，启动后访问 `http://localhost`（前端），后端 API `http://localhost:8080`。

## 1. 架构总览

```
浏览器 ──► nginx(:80)  ──► backend(:8080)  ──► mysql(:3306, 平台库+demo_business)
   │            │              │                    └─► redis(:6379, 向量检索)
   │            └─ /api /sse /mcp /reports 反向代理      └─► 宿主 docker.sock（Python 沙箱）
   └─ SSE 流式聊天（proxy_buffering off）
```

- **前端**：Vue3 + Vite 构建产物由 nginx 托管，`/api`、`/sse`、`/mcp`、`/reports`、`/demo`、`/health` 反代后端。
- **后端**：Spring Boot 3.4 WebFlux，fat jar 运行在 `eclipse-temurin:17-jre`。
- **MySQL**：首次启动自动建 `datarobort` 平台库（15 张表）+ `demo_business` 演示库（128 订单/20 客户/17 产品，来自 `docs/sql/demo-data.sql`）。
- **Redis 8**：携带搜索模块，用于知识库/术语向量召回。
- **Python 沙箱**：后端通过挂载的 `docker.sock` 调用宿主 Docker 启动隔离容器（`datarobort-sandbox:latest`），执行图表分析代码。

## 2. 前置条件

| 组件 | 版本 | 说明 |
|---|---|---|
| Docker + Compose v2 | ≥ 24 | `docker compose version` 可验证 |
| Maven | 3.9+ | 仅构建后端 jar 时需要 |
| Java | 17 | 仅构建后端 jar 时需要 |
| Node.js | 20+ | 仅本地开发时需要（镜像内构建不受影响） |
| API Key | — | OpenAI 兼容端点（Qwen / DeepSeek / vLLM） |

## 3. 构建与部署步骤

```bash
# 1) 构建后端 fat jar（也可直接跳过，镜像会 COPY 现成 jar）
cd datarobort
mvn -DskipTests package

# 2) 构建 Python 沙箱镜像（必需，后端运行时会检查）
docker build -f docker/sandbox/Dockerfile -t datarobort-sandbox:latest .

# 3) 准备环境变量
cp .env.example .env
#   编辑 .env，至少填写 OPENAI_API_KEY（生产环境请改 MYSQL_ROOT_PASSWORD / DATAROBORT_CRYPTO_KEY）

# 4) 一键启动
docker compose up -d --build

# 5) 验证
docker compose ps                  # 4 个服务均 healthy
curl http://localhost/health       # {"code":"0",...status:"UP"}
curl -N http://localhost/api/chat/stream -H "Content-Type: application/json" \
     -d '{"question":"统计总订单数","agentId":1}'   # SSE 流式响应
```

**部署后的数据源配置**：compose 部署后，平台库 `datasource` 表中的演示数据源 jdbcUrl 指向的是开发环境地址（`localhost:3307`）。在容器网络内需改为内部地址：

```sql
-- 在 backend 容器内执行（或通过平台界面修改数据源）：
UPDATE datarobort.datasource
SET jdbc_url = 'jdbc:mysql://mysql:3306/demo_business?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
WHERE name LIKE '%演示%' OR id = 1;
-- 然后调用接口重新抓取表元数据：
curl -X POST http://localhost:8080/api/datasources/1/refresh-schema
```

## 4. 常用运维

```bash
docker compose logs -f backend      # 后端日志
docker compose restart backend      # 重启后端（compose up -d 亦可）
docker compose down                 # 停止（保留数据卷）
docker compose down -v              # 停止并删除全部数据（含 mysql 数据卷！）
```

**升级/重灌**：`docker compose down -v` 会清空平台库与演示库；重新 `up` 时 MySQL 会重新初始化（平台表 + demo 数据）。若只想重置演示数据：

```bash
docker exec -i datarobort-mysql mysql -uroot -proot123 demo_business < docs/sql/demo-data.sql
```

## 5. 沙箱镜像构建与安全

沙箱镜像只装 pandas/numpy/matplotlib，**不允许联网拉取**（后端 `--pull never`），只能预先构建。如果宿主缺少镜像，聊天中的图表分析会报错：

```
sandbox image datarobort-sandbox:latest 不存在，请先构建: docker build -t datarobort-sandbox:latest docker/sandbox/
```

构建方式见上文步骤 2。构建后可验证：

```bash
echo 'print(1+1)' | docker run -i --rm datarobort-sandbox:latest
```

## 6. 已知风险清单（v1.0）

| # | 风险 | 说明 | 缓解/计划 |
|---|---|---|---|
| 1 | **无认证体系** | 所有 API（含 `POST /api/chat`、`/api/datasources`、`/api/model-configs`）未鉴权 | 仅限内网/受信环境部署；计划 v1.1 引入 JWT 认证 |
| 2 | **/reports 报告公开** | 生成的 HTML 报告以静态文件公开（URL 含 UUID，难以枚举但不加密） | 不存敏感明细；v1.1 加访问鉴权 |
| 3 | **backend 拥有宿主 Docker 权限** | 沙箱经 `docker.sock` 执行，backend 容器可控制宿主 docker | 沙箱镜像 `--pull never` + `--network none` + `--cap-drop ALL` + 只读根文件系统 + 有界输出 + 并发限制（默认 2）；严禁向不可信网络暴露 8080 |
| 4 | **SQL 白名单依赖元数据快照** | 表名白名单以 `ds_table` 快照为准，新增表需刷新元数据 | 界面「刷新元数据」按钮 / `POST /api/datasources/{id}/refresh-schema` |
| 5 | **默认密钥** | `DATAROBORT_CRYPTO_KEY` 默认值公开 | 生产必须通过环境变量覆盖；已加密数据使用旧密钥将无法解密 |
| 6 | **crypto-key 变更影响** | 模型 API Key 与数据源密码用 AES 加密存储，更换主密钥后旧密文失效 | 换密钥后重新保存模型/数据源 |

## 7. 常见问题（FAQ）

**Q: `OPENAI_API_KEY 必填` 启动失败？**
A: `.env` 未创建或未填 OPENAI_API_KEY。`cp .env.example .env` 后填写。

**Q: 首页可以打开，但问数一直转圈？**
A: 检查后端日志 `docker compose logs backend`。常见原因：沙箱镜像未构建、模型 Key 无效、数据源未指向容器内地址（见第 3 节）。

**Q: SSE 流式回复卡顿/一次性返回？**
A: 确认 nginx 配置包含 `proxy_buffering off`（本部署默认已开启）。若自定义反代，必须对 `/api/chat/stream`、`/sse` 关闭缓冲。

**Q: Windows Docker Desktop 挂载 /var/run/docker.sock 失败？**
A: Docker Desktop 的 socket 在 `C:\ProgramData\Docker\...\named pipe`，compose 的 `/var/run/docker.sock` 挂载在 WSL2 模式下可用（推荐 WSL2 backend）。纯 Windows 容器模式下沙箱不可用，需换用 WSL2。

**Q: 如何接入 Claude Desktop（MCP）？**
```json
{
  "mcpServers": {
    "datarobort": { "url": "http://localhost:8080/sse" }
  }
}
```

## 8. 本地开发模式（双模式）

不依赖 Docker 的开发模式：

```bash
# MySQL 8 + Redis 8 容器（platform 库自动初始化）
docker run -d --name datarobort-mysql8 -p 3307:3306 -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0
docker run -d --name datarobort-redis8 -p 6380:6379 redis:8
docker exec -i datarobort-mysql8 mysql -uroot -proot123 demo_business < docs/sql/demo-data.sql

mvn -DskipTests package -pl datarobort-api -am
java -jar datarobort-api/target/datarobort-api-0.1.0-SNAPSHOT.jar   # 后端 :8080
cd datarobort-frontend && npm run dev                              # 前端 :3000
```
