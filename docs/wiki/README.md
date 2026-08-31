# Cyber Town 本地 Wiki

该目录用于沉淀项目设计、规则、迭代与维护约定。

## 文档索引

- `docs/wiki/iteration-log.md`：迭代记录（按时间倒序）
- `docs/wiki/god-mode-spec.md`：上帝模式语义与字段映射
- `docs/wiki/data-model.md`：实体字段、持久化策略、快照说明
- `docs/wiki/prompt-design.md`：主要 Prompt 约束与演进记录

## 维护约定

- 每次功能变更后，更新 `README.MD` 的能力与接口部分
- 每次规则变更（如学历/公司/工资/期权/世界事件权重）后，在 `iteration-log.md` 记录“旧规则 -> 新规则”
- 每次修复线上 bug 后，记录“复现步骤 + 根因 + 修复点”
