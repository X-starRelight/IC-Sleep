# 配置参考

`ic-sleep` 的配置文件为 `config/icsleep.json`（相对游戏目录）。

- 文件不存在时自动按默认值创建。
- 文件损坏时回退到默认配置并记录日志。
- 加载时缺失字段自动补默认值，非法值（负数、`< 1` 的限额、空字符串等）自动兜底修正。
- 每次启动加载后会把**修正后的完整配置写回文件**，因此所有配置项都会出现在文件中。

```json
{
  "enabled": true,
  "commandAlias": "sleep",
  "defaultUnit": "tick",
  "maxDelayTicks": 72000,
  "maxGlobalTasks": 10000,
  "maxTasksPerSource": 100,
  "maxTasksPerTick": 1000,
  "persistTasks": true,
  "persistFile": "icsl_tasks.json",
  "saveIntervalTicks": 6000,
  "commandBlockPolicy": "cancel",
  "enableFunctionPause": true,
  "maxNestedDepth": 10,
  "returnTaskId": true,
  "showTaskIdInFeedback": true,
  "taskIdStart": 2,
  "keepTaskHistory": false,
  "taskHistoryLimit": 100,
  "feedbackLevel": "normal",
  "logLevel": "info"
}
```

## 配置项

### 开关

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `enabled` | 是否启用模组。`false` 时不再调度 tick，且所有创建请求直接失败 | `true` |
| `commandAlias` | 命令别名：IC 命令树的根字面量（`/ic run sleep <alias> ...`），同时是函数内模式 B 的匹配前缀 | `sleep` |
| `enableFunctionPause` | 是否启用函数暂停（模式 B）。`false` 时函数中的 `/sleep` 条目不拦截，剩余命令立即继续 | `true` |
| `persistTasks` | 是否持久化 `run` 模式任务（函数暂停任务始终不持久化） | `true` |

### 时间

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `defaultUnit` | 无后缀时间的默认单位：`tick` / `second` / `minute` / `hour` / `day` | `tick` |
| `maxDelayTicks` | 最大延迟（tick），超出即拒绝创建任务 | `72000`（1 小时） |

### 限额

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `maxGlobalTasks` | 全局最大待执行任务数 | `10000` |
| `maxTasksPerSource` | 单个来源（玩家 / 命令方块坐标 / 函数执行体等）最大任务数 | `100` |
| `maxTasksPerTick` | 每 tick 最大执行任务数，超出的任务留到下一 tick 执行 | `1000` |
| `maxNestedDepth` | 函数暂停的最大嵌套深度，超出则放弃暂停并记 warn 日志 | `10` |

### 任务 ID 与反馈

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `taskIdStart` | 任务 ID 起始值，最小 `2`（`0` 表示失败、`1` 表示成功，不占用） | `2` |
| `returnTaskId` | 成功创建任务时，命令返回值用任务 ID（`true`）还是固定 `1`（`false`） | `true` |
| `showTaskIdInFeedback` | 反馈消息中是否显示任务 ID | `true` |

### 来源与持久化

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `commandBlockPolicy` | 命令方块被破坏时的策略：`cancel` 取消任务 / `keep` 保留任务 | `cancel` |
| `persistFile` | 持久化文件名，位于世界存档目录 | `icsl_tasks.json` |
| `saveIntervalTicks` | 定期保存间隔（tick） | `6000`（5 分钟） |

### 历史与保留字段

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `keepTaskHistory` | 是否在内存中记录已完成任务历史 | `false` |
| `taskHistoryLimit` | 历史保留条数 | `100` |
| `feedbackLevel` | **保留字段，当前版本未生效** | `normal` |
| `logLevel` | **保留字段，当前版本未生效**（日志输出由 SLF4J 决定） | `info` |

> `keepTaskHistory` 开启后历史仅存于内存，当前版本没有查询历史的命令（`info` 只能查待执行任务）。

## 时间参数 `<time>`

### 格式与换算

| 格式 | 含义 | 换算 |
|---|---|---|
| `20` | 无后缀，按 `defaultUnit` | 默认 `tick` → 20 tick |
| `20t` | 游戏刻 | 20 tick |
| `1s` | 秒 | 1 × 20 = 20 tick |
| `1m` | 分钟 | 1 × 1200 = 1200 tick |
| `1h` | 小时 | 1 × 72000 = 72000 tick |
| `1d` | 游戏日 | 1 × 24000 = 24000 tick |

- 不支持小数、不支持负数。
- 最小 `0`：下一 tick 执行。
- 最大为 `maxDelayTicks`（默认 `72000` = 1 小时）。
- `defaultUnit` 可取 `tick` / `second` / `minute` / `hour` / `day`（也接受 `s` / `m` / `h` / `d` 与复数形式），无法识别时按 tick 处理。

### 错误反馈

```text
无效时间：abc。示例：20、20t、1s、1m。
时间超出范围：允许 0 到 72000 tick。
```

### 示例

```text
/ic run sleep sleep 20 run say hi        # 20 tick 后（1 秒）
/ic run sleep sleep 5s run say hi        # 100 tick 后
/ic run sleep sleep 1m run say hi        # 1200 tick 后
/ic run sleep sleep 1d run say hi        # 24000 tick 后（1 游戏日）
/ic run sleep sleep 0 run say hi         # 下一 tick 后
```

## 任务 ID 规则

- 从 `taskIdStart`（默认 `2`）开始全局递增，**不占用 `0` 和 `1`**（`0` = 失败、`1` = 成功）。
- 达到 `2147483647` 后回绕到起始值，并自动跳过仍被待执行任务占用的 ID。
- `nextTaskId` 随持久化文件保存，重启后继续递增、不与恢复的任务冲突。
- 任务执行完成或被取消后 ID 立即失效。

## 常用配置组合

```json
{
  "defaultUnit": "second",
  "maxDelayTicks": 24000,
  "maxGlobalTasks": 500,
  "maxTasksPerSource": 20,
  "returnTaskId": false,
  "showTaskIdInFeedback": false,
  "commandBlockPolicy": "keep"
}
```

- 时间按秒写：`/ic run sleep sleep 30 run say hi`
- 更严格的队列限额，适合公共服务器
- 不返回 / 不显示任务 ID，适合纯人工使用
- 命令方块被破坏后任务仍保留（到期时若方块不存在则不执行）
