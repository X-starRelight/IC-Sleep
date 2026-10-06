# 持久化

`ic-sleep` 把**待执行的 `run` 模式任务**写入 JSON 文件，服务器重启后未执行的任务继续倒计时。

## 基本规则

| 项 | 值 |
|---|---|
| 文件位置 | `<世界存档目录>/<persistFile>`，默认 `world/icsl_tasks.json` |
| 格式 | JSON（无注释），`formatVersion` 固定为 `1` |
| 写入方式 | 原子写入：先写 `.tmp` 临时文件，再替换正式文件（不支持原子移动时退化为直接覆盖） |
| 是否启用 | 由 `persistTasks` 控制（默认 `true`），见 [config.md](config.md) |

**只有 `run` 模式任务会持久化**。函数暂停（`function_resume`）任务的剩余函数条目只存在于内存，不序列化、不恢复——重启后被丢弃。

## 保存时机

1. **定期保存**：每 `saveIntervalTicks`（默认 6000 tick = 5 分钟）一次。
2. **服务器停止时**：`SERVER_STOPPING` 事件触发保存。
3. **加载到不兼容文件时**：重新写入一份当前格式的文件。

## 文件结构

```json
{
  "formatVersion": 1,
  "nextTaskId": 124,
  "tasks": [
    {
      "id": 123,
      "remainingTicks": 20,
      "type": "run",
      "command": "say hello",
      "sourceType": "player",
      "sourceKey": "player:00000000-0000-0000-0000-000000000000",
      "permissionLevel": 2,
      "createdAt": 0,
      "context": {
        "dimension": "minecraft:overworld",
        "entityUuid": "00000000-0000-0000-0000-000000000000",
        "x": 0.0,
        "y": 64.0,
        "z": 0.0,
        "yaw": 0.0,
        "pitch": 0.0
      },
      "feedbackTarget": "player",
      "creatorUuid": "00000000-0000-0000-0000-000000000000"
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `formatVersion` | 持久化格式版本，当前 `1` |
| `nextTaskId` | 下一个待分配的任务 ID，重启后据此继续递增、避免冲突 |
| `tasks[].id` | 任务 ID（>= 2） |
| `tasks[].remainingTicks` | 剩余延迟（tick），重启后从该值继续倒计时 |
| `tasks[].type` | 固定 `run`（函数暂停任务不落盘） |
| `tasks[].command` | 到期执行的命令（不含前导 `/`） |
| `tasks[].sourceType` | `player` / `server` / `command_block` / `function` |
| `tasks[].sourceKey` | 来源标识：`player:<uuid>`、`block:<维度>:<x>,<y>,<z>`、`server`、`entity:<uuid>`，用于每来源限额 |
| `tasks[].permissionLevel` | 创建时记录的权限等级（快照信息） |
| `tasks[].createdAt` | 创建时的调度器 tick 计数（仅记录，不参与调度） |
| `tasks[].context` | 执行上下文快照：维度、实体 UUID、命令方块坐标、位置、朝向（按来源不同字段为空） |
| `tasks[].feedbackTarget` | 反馈目标：`player` / `log` / `none` |
| `tasks[].creatorUuid` | 创建者 UUID，供 `clear last` 使用 |

> 命令方块来源的任务，其坐标保存在 `context.blockX/blockY/blockZ`；玩家来源的 UUID 保存在 `context.entityUuid`。

## 加载与恢复

服务器启动（`SERVER_STARTING`）时：

1. 读取 `<存档目录>/<persistFile>`。
2. 恢复 `nextTaskId`，确保新任务 ID 不与恢复的任务冲突。
3. 逐条恢复 `run` 任务，并重新计入每来源限额。
4. 恢复前校验来源有效性：玩家离线、命令方块已不存在（`commandBlockPolicy=cancel`）、维度不存在的任务**直接丢弃**并记录日志。

## 降级与容错

| 情况 | 处理 |
|---|---|
| 文件不存在 | 视为空队列，不报错 |
| JSON 解析失败（文件损坏） | 记录错误日志，跳过加载（不崩溃）；下次保存时写入当前内容 |
| 单条任务记录损坏 | 记录 warn 日志，忽略该条，其余正常恢复 |
| `formatVersion` 不是 `1` | 记录错误日志，**停止加载全部任务**，并用当前格式重写文件 |
| 保存时磁盘/IO 失败 | 记录错误日志，游戏继续运行 |

## 已知边界

- **函数暂停任务重启后不恢复**：剩余函数条目无法序列化，重启即丢失；需要跨重启的延迟请使用 `run` 模式。
- 剩余时间在**停服期间不流逝**，重启后从上次保存的 `remainingTicks` 继续。
- 任务执行完成后不写回历史，ID 立即失效。
