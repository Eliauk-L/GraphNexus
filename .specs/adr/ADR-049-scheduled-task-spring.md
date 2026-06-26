# ADR-049: 定时任务框架选型 — Spring @Scheduled

- **日期**: 2026-06-23
- **状态**: accepted
- **来源**: `ops-analytics` DESIGN

---

## Context

运营管理模块需要每日凌晨自动采集统计快照（REQUIREMENT AC-9）。项目当前未使用任何定时任务框架，需要首次引入。

可选方案：
1. **Spring `@Scheduled`**：Spring Boot 原生支持，`@EnableScheduling` + `@Scheduled(cron = "...")` 即可，零新依赖
2. **Quartz Scheduler**：独立调度框架，支持任务持久化、失败重试、集群调度
3. **XXL-JOB**：国产分布式任务调度平台，需要独立部署调度中心

## Decision

选择 **Spring `@Scheduled`**。

理由：
- 需求仅一个定时任务（每日凌晨 2:00 全量快照采集），单实例部署，无需分布式调度能力
- 零新 Maven 依赖（`@Scheduled` 由 `spring-boot-starter` 自带），不改 `pom.xml`
- 配置简单：`SchedulingConfig` 配置类 + `@EnableScheduling` + `@Scheduled(cron = "0 0 2 * * ?")`
- 快照采集幂等设计（`snapshot_date` 唯一约束），即使未来多实例重复执行，也仅产生一条有效快照

## Consequences

- **正面**：引入成本最低，不需要新增任何 Maven 依赖、不需要独立服务、不需要学习新框架
- **负面**：无任务失败自动重试（Quartz 有 `refire` 机制），快照采集失败后需等次日自动重跑；无任务执行 Dashboard（XXL-JOB 有），排查需看应用日志
- **迁移路径**：若未来需要分布式调度（多实例部署 + 任务不可重复执行），可引入 ShedLock（基于 Redis/MySQL 的 Spring `@Scheduled` 分布式锁扩展），或迁移到 Quartz。迁移时定时任务业务代码（`SnapshotServiceImpl.takeDailySnapshot()`）不变，只改注解和配置