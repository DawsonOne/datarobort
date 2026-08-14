# DataRobort

> 自然语言问数 → SQL 自动生成 → 图表分析 → 报告导出，基于 LLM Agent 编排的数据分析助手。v1.0

DataRobort 让业务人员用中文提问，系统自动完成「意图识别 → 知识召回 → SQL 生成与安全校验 → 执行查询 → Python 沙箱图表分析 → Markdown 报告 / HTML 报告」的完整链路，支持多轮会话与智能体（数据源 + 知识库 + 角色 Prompt 的组合单元）。

## 核心能力

- **8 节点 Agent 编排**：intent → recall → sql-gen → sql-guard → sql-exec → python → chart → report（StateGraph 顺序执行 + SSE 流式事件推送）
- **SQL 四层安全防护**：AST 校验（仅 SELECT、表名白名单、危险函数黑名单、多语句/写 CTE/INTO OUTFILE 拒绝）→ LLM 修复重试 → 执行前二次校验 + LIMIT 强制 + 只读连接 → Druid WallFilter 兜底
- **Python 隔离沙箱**：Docker 容器（无网络 / 只读 / 裁剪能力 / 512MB 内存 / 64KB 有界输出 / 并发信号量），数据经 base64 注入杜绝代码注入
- **知识增强**：知识库文档（分块向量化）+ 业务术语同义词 + 语义模型（表/字段别名），Redis 8 向量检索
- **智能体 + 会话**：多 Agent（数据源/知识/角色 Prompt 绑定）、多轮上下文（最近 10 条 / 4000 字符）
- **MCP Server**：SSE 协议，可接入 Claude Desktop
- **一键部署**：docker-compose 全栈（mysql + redis + 后端 + 前端 nginx），演示数据开箱即用

## 架构

```
前端 Vue3 (Vite/nginx) ──SSE──► 后端 Spring Boot 3.4 WebFlux ──► MySQL 8（平台库 + 业务库）
                                        │                            └─► Redis 8（向量检索）
                                        └─► Docker 沙箱（宿主 daemon，隔离执行 Python 图表代码）
```

| 模块 | 说明 |
|---|---|
| `datarobort-api` | 启动入口（application.yml 集中配置） |
| `datarobort-web` | Agent 编排节点、服务层、REST/SSE 控制器、MCP Server |
| `datarobort-ai` | 图执行器（StateGraph）、向量检索客户端 |
| `datarobort-core` | 实体、MyBatis Mapper、业务数据源池（Druid + WallFilter） |
| `datarobort-sandbox` | Python 沙箱客户端（Docker CLI 驱动，含加固与并发控制） |
| `datarobort-common` | 通用工具（AES/GCM 加密、错误码） |
| `datarobort-frontend` | Vue3 + Element Plus + ECharts 前端 |

## 快速开始

**生产模式（推荐）**：见 [DEPLOY.md](DEPLOY.md)

```bash
mvn -DskipTests package
docker build -f docker/sandbox/Dockerfile -t datarobort-sandbox:latest .
cp .env.example .env   # 填入 OPENAI_API_KEY
docker compose up -d --build
# 访问 http://localhost
```

**开发模式**：

```bash
docker run -d --name datarobort-mysql8 -p 3307:3306 -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0
docker run -d --name datarobort-redis8 -p 6380:6379 redis:8
docker exec -i datarobort-mysql8 mysql -uroot -proot123 demo_business < docs/sql/demo-data.sql
mvn -DskipTests package -pl datarobort-api -am
java -jar datarobort-api/target/datarobort-api-0.1.0-SNAPSHOT.jar   # 后端 :8080
cd datarobort-frontend && npm run dev                              # 前端 :3000
```

## 文档索引

- [用户手册](USER_GUIDE.md) — 功能总览、问数示例、FAQ
- [部署文档](DEPLOY.md) — 一键部署、运维、已知风险清单
- [演示数据](docs/sql/demo-data.sql) — 128 订单 / 20 客户 / 17 产品
- [平台库表结构](docs/sql/schema.sql) — 15 张表
- [评测集](docs/eval/cases.json) — 30 条端到端用例
- [评测报告](docs/eval/) — SQL 准确率与 LLM Judge 复评
- [压测报告](docs/eval/perf-report.md) — SSE / 沙箱并发

## 测试与质量

- 81 个单元测试（JUnit 5 + Mockito）：SQL 校验器（攻击样本全覆盖）、LIMIT 强制、AES 加密、会话窗口、召回逻辑、报告 XSS 转义、图执行器、沙箱输出有界
- 30 条端到端业务评测：SQL 准确率 100%（25/25），注入攻击 100% 拒绝，LLM judge 复评 29/30 pass
- 并发压测：20 路 SSE 并发执行成功率 100% + 超限快速拒绝（并发上限 8），沙箱图表 6 并发 100% 成功、容器零泄漏

## 已知风险（v1.0）

无认证体系、/reports 报告公开、后端持宿主 Docker 权限（沙箱缓解）。详见 [DEPLOY.md](DEPLOY.md#6-已知风险清单v10)。

## 版本

- v1.0.0（2026-08）— 里程碑 M5：端到端评测、安全整改、压测、一键部署

License: MIT（内部项目）
