# 命令参考

`ic-sleep` 的命令树挂载在 IC-Root 统一入口下，完整路径为：

```text
/ic run sleep <command> [args...]
```

其中 `sleep` 是本 Mod 在 IC-Root 注册表中的名字（`IcSleepMod.SLEEP_IC_NAME`），命令树根字面量为配置项 `commandAlias`（默认 `sleep`），因此最常用的一条命令完整写作 `/ic run sleep sleep <time> run <command>`。

所有子命令都继承 IC-Root 的权限限制：**OP / 控制台 / 命令方块等具备 moderator 权限的执行源**才能执行与补全，本 Mod 自身不额外添加 `.requires(...)`，且 `/ic run` 不提升执行源权限。

---

## 命令树总览

```text
/ic run sleep <time> run <command>   # 模式 A：延迟执行单条命令
/ic run sleep <time>                 # 模式 B：仅函数内生效（通常裸写 /sleep <time>）
/ic run sleep list [page]            # 列出待执行任务（分页，每页 10 条）
/ic run sleep info <id>              # 查看单个任务详情
/ic run sleep clear [all]            # 清除全部任务
/ic run sleep clear <id>             # 清除指定任务
/ic run sleep clear last             # 清除执行者最近创建的任务
/ic run sleep clear score <target> <objective>          # 从记分板读 ID 并清除
/ic run sleep clear storage <namespace:path> <path>     # 从 storage 读 ID 并清除
```

---

## 模式 A：`/ic run sleep sleep <time> run <command>`

延迟执行单条命令。玩家、控制台、命令方块、函数等**所有来源**均可使用。

### 用法

```text
/ic run sleep sleep <time> run <command>
```

- `<time>`：时间参数，见 [config.md](config.md) § 时间参数。
- `run` 之后为整条命令（greedy 字符串，可含空格）。
- 命令在到期时以**发起者的原始上下文**执行：玩家用其当前位置、维度、权限、朝向；命令方块用方块坐标与 GAMEMASTER 权限；服务端/函数还原捕获时的快照。

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 创建成功 | `已安排任务 ID: {id}，将在 {ticks} tick 后执行 /{command}。` | `sendSuccess` | 任务 ID（`returnTaskId=false` 时为 `1`） |
| 创建成功（`showTaskIdInFeedback=false`） | `已安排任务，将在 {ticks} tick 后执行 /{command}。` | `sendSuccess` | 同上 |
| 时间格式无效 | `无效时间：{input}。示例：20、20t、1s、1m。` | `sendFailure` | `-1` |
| 超出最大延迟 | `时间超出范围：允许 0 到 {maxDelayTicks} tick。` | `sendFailure` | `-1` |
| `run` 后命令为空 | `run 后命令为空。` | `sendFailure` | `-1` |
| 全局队列满 | `队列已满：全局任务数达到上限 {maxGlobalTasks}。` + `任务创建失败，请检查队列是否已满。` | `sendFailure` | `-1` |
| 该来源队列满 | `队列已满：该来源任务数达到上限 {maxTasksPerSource}。` + `任务创建失败，请检查队列是否已满。` | `sendFailure` | `-1` |
| 模组被禁用（`enabled=false`） | `任务创建失败，请检查队列是否已满。` | `sendFailure` | `-1` |
| 到期执行 | 执行 `<command>` 本身的消息（本 Mod 不追加反馈） | — | — |

### 示例

```text
/ic run sleep sleep 20 run say hi
# 已安排任务 ID: 2，将在 20 tick 后执行 /say hi。            （返回 2）

/ic run sleep sleep 1s give @p minecraft:apple 1
# 已安排任务 ID: 3，将在 20 tick 后执行 /give @p minecraft:apple 1。  （返回 3）

/ic run sleep sleep abc run say hi
# 无效时间：abc。示例：20、20t、1s、1m。                     （返回 -1）
```

---

## 模式 B：函数内 `/sleep <time>`（暂停并继续）

**仅在数据包函数中有效**：函数执行到该条目时暂停，保存其后的剩余条目与执行上下文，延迟到期后从下一条命令继续。

### 用法

写在 `.mcfunction` 中，**裸写别名、不带 `/ic run` 前缀**：

```mcfunction
# my:wait.mcfunction
/sleep 20
say 20 tick 后继续
say 函数恢复执行
```

> **为什么不能带前缀**：模式 B 由 `CallFunctionMixin` 拦截**函数条目的原文**并按 `commandAlias` 前缀匹配识别。写成 `/ic run sleep sleep 20` 不会被拦截，会走普通执行路径并命中下方的报错。

### 行为

| 情况 | 消息 / 结果 | 消息类型 | 返回值 |
|---|---|---|---|
| 函数内命中，注册成功 | `函数已暂停，任务 ID: {id}，将在 {ticks} tick 后继续。`（`showTaskIdInFeedback=false` 时无 ID） | `sendSuccess` | 任务 ID |
| 函数外使用（聊天栏 / 控制台 / 命令方块，或写作 `/ic run sleep sleep <time>`） | `该写法只能用于数据包函数中。若只想延迟一条命令，请使用 /ic run sleep sleep <time> run <command>。` | 命令异常（红色） | 失败，不创建任务 |
| 时间解析失败 | 跳过该 `/sleep` 条目，剩余命令继续执行 | — | — |
| 函数暂停被关闭（`enableFunctionPause=false`） | 不暂停，剩余命令立即继续 | — | — |
| 嵌套深度超限（`> maxNestedDepth`） | 不暂停，剩余命令立即继续；日志 `函数暂停嵌套深度超出限制 {n}，取消暂停。` | 日志 warn | — |
| 队列满 | 不暂停，剩余命令立即继续 | — | — |
| 到期恢复 | 从 `/sleep` 的下一条命令继续执行 | — | — |

- 函数暂停任务**仅存在于内存，不参与持久化**，服务器重启后不会恢复（见 [persistence.md](persistence.md)）。
- 每个函数条目只拦截**第一个** `/sleep <time>`；同一函数内后续的 sleep 需放进被调用的子函数（详见 [faq.md](faq.md)）。

---

## 时间参数 `<time>`

| 格式 | 含义 | 换算 |
|---|---|---|
| `20` | 无后缀，按 `defaultUnit`（默认 tick） | 20 tick |
| `20t` | 游戏刻 | 20 tick |
| `1s` | 秒 | 20 tick |
| `1m` | 分钟 | 1200 tick |
| `1h` | 小时 | 72000 tick |
| `1d` | 游戏日 | 24000 tick |

不支持小数与负数；最小 `0`（下一 tick 执行），最大由 `maxDelayTicks`（默认 `72000`）限制。完整说明见 [config.md](config.md)。

---

## `/ic run sleep list [page]`

列出所有待执行任务，每页 10 条。

### 用法

```text
/ic run sleep list
/ic run sleep list <page>    # page >= 1，超出最大页时取最大页
```

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 执行成功 | 首行 `待执行任务（第 {current}/{maxPage} 页，共 {total} 个）：`，随后每任务一行 `  #{id}  剩余 {n}t  来源 {sourceType}  {命令摘要}` | `sendSuccess` | 待执行任务**总数**（与页码无关；空队列为 `0`） |

- 命令摘要超过 40 字符截断为 `…`；函数暂停任务显示 `(函数恢复)`。
- 来源取值：`player` / `server` / `command_block` / `function`。

### 示例

```text
/ic run sleep list
# 待执行任务（第 1/1 页，共 2 个）：
#   #2  剩余 15t  来源 player  say hello
#   #3  剩余 900t  来源 command_block  give @p minecraft:apple 1   （返回 2）
```

---

## `/ic run sleep info <id>`

查看单个待执行任务的详情。

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 找到 | `任务 {id} 详情：` ↵ `  ID: {id}` ↵ `  类型: {run\|function_resume}` ↵ `  剩余: {n} tick` ↵ `  来源: {sourceType} ({sourceKey})` ↵（run 任务）`  命令: {command}` | `sendSuccess` | `1` |
| 未找到 | `未找到任务 {id}。` | `sendFailure` | `-1` |

- `sourceKey` 形如 `player:<uuid>`、`block:<维度>:<x>,<y>,<z>`、`server`、`entity:<uuid>`。
- 只能查询**待执行**任务；已完成 / 已取消的任务不可见（历史默认不保留）。

---

## `/ic run sleep clear ...`

五种清除方式，权限同上。

| 用法 | 行为 | 成功消息 | 返回值 |
|---|---|---|---|
| `clear` / `clear all` | 清除全部待执行任务 | `已清除 {count} 个任务。` | 清除数量（可为 `0`） |
| `clear <id>` | 清除指定任务 | `已清除任务 {id}。`（失败：`未找到任务 {id}。`） | `1` / `-1` |
| `clear last` | 清除**该玩家**最近创建的任务（非玩家执行源无记录） | `已清除你最近创建的任务。`（失败：`未找到你最近创建的任务。`） | `1` / `-1` |
| `clear score <target> <objective>` | 读取记分板分数作为任务 ID 后清除 | 同 `clear <id>`（无分数：`未找到记分板分数。`） | `1` / `-1` |
| `clear storage <namespace:path> <path>` | 读取 storage 中的整数值作为任务 ID 后清除 | 同 `clear <id>`（非整数/不存在：`未找到存储值或值不是整数。`） | `1` / `-1` |

- `clear score` / `clear storage` 的参数错误（记分板不存在、路径非法等）会抛出 Brigadier 参数异常并显示其消息。
- 任务执行完成后 ID 立即失效，`clear <id>` 只能清除**待执行**任务。

### 程序化清除示例

```mcfunction
# 1. 创建任务并把 ID 存入记分板
/execute store result score @s icsl.last_id run ic run sleep sleep 20 run say hello

# 2. 需要时清除
/ic run sleep clear score @s icsl.last_id
```

```mcfunction
# storage + 宏函数
/execute store result storage icsl:tmp last_id int 1 run ic run sleep sleep 20 run say hello

# my:clear_sleep.mcfunction
/ic run sleep clear $(last_id)
```

---

## 来源失效与任务取消

任务每 20 tick 扫描一次来源有效性，失效即取消并记录日志：

| 来源 | 失效条件 | 可配置 |
|---|---|---|
| 玩家 | 玩家离线 | 否（固定取消） |
| 命令方块 | 方块实体不存在（被破坏） | `commandBlockPolicy`：`cancel`（默认）/ `keep` |
| 任意 | 维度不存在 | 否 |
| 服务端 | — | 不失效 |

来源无效的任务在服务器启动恢复时同样会被丢弃。

---

## 返回值汇总

返回值均为**命令返回值**，供命令方块、函数、宏或自动化读取：

| 命令 | 返回值 |
|---|---|
| `sleep <time> run <command>` | 任务 ID（`returnTaskId=true` 默认）；`returnTaskId=false` 时为 `1`；失败 `-1` |
| `sleep <time>`（函数内） | 任务 ID（原版函数无法读取，需 ID 请改用 `run` 模式 + `/execute store`）；函数外失败 |
| `list [page]` | 待执行任务总数（含所有页，空队列 `0`） |
| `info <id>` | 找到 `1`，未找到 `-1` |
| `clear` / `clear all` | 清除数量 |
| `clear <id>` / `clear last` / `clear score` / `clear storage` | 成功 `1`，失败 `-1` |

> IC-Root 会在反馈开关允许时附加 `[IC-Root] Return {Return}` 消息，是否发送见 IC-Root 文档 `docs_icroot/feedback.md`。

## 消息文案一览

| 消息 | 方法 |
|---|---|
| `已安排任务 ID: {id}，将在 {ticks} tick 后执行 /{command}。` | `sendSuccess` |
| `已安排任务，将在 {ticks} tick 后执行 /{command}。` | `sendSuccess` |
| `函数已暂停，任务 ID: {id}，将在 {ticks} tick 后继续。` | `sendSuccess` |
| `该写法只能用于数据包函数中。若只想延迟一条命令，请使用 /ic run sleep sleep <time> run <command>。` | 命令异常 |
| `无效时间：{input}。示例：20、20t、1s、1m。` | `sendFailure` |
| `时间超出范围：允许 0 到 {max} tick。` | `sendFailure` |
| `run 后命令为空。` | `sendFailure` |
| `队列已满：全局任务数达到上限 {n}。` | `sendFailure` |
| `队列已满：该来源任务数达到上限 {n}。` | `sendFailure` |
| `任务创建失败，请检查队列是否已满。` | `sendFailure` |
| `待执行任务（第 {current}/{maxPage} 页，共 {total} 个）：` + 任务行 | `sendSuccess` |
| `任务 {id} 详情：` + 字段行 | `sendSuccess` |
| `未找到任务 {id}。` | `sendFailure` |
| `已清除 {count} 个任务。` / `已清除任务 {id}。` | `sendSuccess` |
| `已清除你最近创建的任务。` | `sendSuccess` |
| `未找到你最近创建的任务。` / `未找到记分板分数。` / `未找到存储值或值不是整数。` | `sendFailure` |
