# EV Charge Book 后端设计

版本: v1.1.0

更新时间: 2026-08-25

## 1. 当前阶段

MVP v0.1 不依赖后端。

核心原则:

- Local First
- 离线可用
- 后端不得成为基础记账的单点依赖

---

## 2. 后端启用时机

后端只在以下真实需求出现时启用:

- 多设备同步
- 账号与数据备份
- Web 管理或跨端访问
- AI 分析需要统一服务入口
- 远程配置或服务端聚合

---

## 3. 目标架构

```text
Android App
   |
HTTPS REST API
   |
Spring Boot
   |
PostgreSQL
   |
Redis（按需）
```

不提前拆微服务。

初期建议保持单体 Spring Boot 服务，按领域包组织。

---

## 4. 领域模块

推荐模块:

```text
server
├── auth
├── user
├── vehicle
├── charging
├── sync
├── analysis
└── ai
```

职责:

### auth / user

- 登录身份
- 用户资料
- Token 管理

### vehicle

- 车辆档案云端镜像

### charging

- 充电记录同步
- 服务端查询

### sync

- 增量同步
- 冲突处理
- 多设备数据一致性

### analysis

- 跨周期统计
- 服务端聚合

### ai

- AI 分析统一入口
- Prompt / 模型调用隔离

---

## 5. API 设计原则

- REST 优先
- JSON
- `/api/v1/...`
- ID 使用稳定不可变标识
- 更新接口支持幂等
- 分页仅在真实数据规模需要时加入

示例:

```text
GET    /api/v1/vehicles
POST   /api/v1/vehicles
GET    /api/v1/charging-records
POST   /api/v1/charging-records
PUT    /api/v1/charging-records/{id}
DELETE /api/v1/charging-records/{id}
```

---

## 6. 同步原则

Local First 下，本地 Room 为客户端立即可用的数据源。

后续云同步遵循:

1. 本地先写成功
2. 后台异步同步
3. 服务端返回确认
4. 冲突按明确规则解决

推荐为核心实体预留:

- localId / id
- updatedAt
- deletedAt 或软删除标记
- syncState（客户端）

v0.1 不必全部实现，但数据库设计不能阻断未来同步。

---

## 7. PostgreSQL

服务端 PostgreSQL 作为云端权威数据源，但不替代客户端本地可用性。

原则:

- schema 与 DATABASE.md 保持语义一致
- 金额、电量字段使用合适精度类型
- 明确时区
- 删除行为必须可恢复或可审计时使用软删除

---

## 8. Redis

Redis 不是默认必选项。

仅在有明确需求时使用，例如:

- 短期缓存
- 限流
- AI 任务状态
- 临时同步锁

禁止为了“架构完整”而引入 Redis。

---

## 9. 安全

后端启用后至少满足:

- HTTPS
- 密码安全存储
- Token 过期
- 用户数据隔离
- 输入校验
- 不在日志中打印敏感凭据

---

## 10. 部署方向

后续建议保持与现有项目一致的轻量部署方式:

```text
Docker Compose
├── app
├── postgres
└── redis（可选）
```

配套:

- `.env.example`
- `deploy.sh`
- `docker-compose.yml`
- 健康检查

具体实现由 CI_CD.md 约束。

---

## 11. v0.1 非目标

- 不创建空壳 Spring Boot 服务
- 不做用户系统
- 不做微服务
- 不做 Redis
- 不做消息队列

---

## 12. 变更记录

### v1.1.0

- 明确后端启用时机与非目标
- 明确单体 Spring Boot 领域架构
- 定义未来 API、同步、PostgreSQL 与 Redis 使用原则
- 明确部署方向与 Local First 的关系
