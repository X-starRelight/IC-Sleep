# 常见问题（FAQ）

## 命令写法

### 为什么命令是 `/ic run sleep sleep ...`，有两个 sleep？

第一个 `sleep` 是本 Mod 在 IC-Root 注册表中的名字（`/ic run <name>`），第二个 `sleep` 是命令树根字面量（配置项 `commandAlias`）。两层都是可配置的：改注册名要改代码常量，改根字面量只需改 `config/icsleep.json` 的 `commandAlias`。

### 函数里写 `/ic run sleep sleep 20` 报错「该写法只能用于数据包函数中」？

模式 B（函数暂停）由 `CallFunctionMixin` 拦截**函数条目的原文**并按 `commandAlias` 前缀匹配，必须**裸写别名**：

```mcfunction
# 正确
/sleep 20

# 错误：不会被拦截，会走普通执行路径并报错
/ic run sleep sleep 20
```

### 聊天栏 / 控制台输入 `/sleep 20` 提示 Unknown command？

本 Mod 的命令树**不注册到全局命令空间**，只挂在 IC-Root 的 `/ic run sleep` 下。函数外想延迟一条命令请用：

```text
/ic run sleep sleep 20 run say hello
```

`/sleep <time>`（不带 `run`）只在数据包函数里有意义，其它地方一律报错引导。

### 函数里怎么延迟单条命令？

照常用 `run` 模式（这条不需要裸写别名）：

```mcfunction
/ic run sleep sleep 5s give @p minecraft:apple 1
/sleep 20
say 函数暂停后继续
```

## 权限

### 命令看不见、Tab 没补全？

`/ic` 整棵树要求 moderator 权限（OP / 控制台 / 命令方块）。权限不足时命令**不可见也不可执行**。先 `/op 你的名字` 或在控制台测试。

### `/ic run` 会提升权限吗？

不会。`/ic run` 保留原执行源；延迟执行时也按**创建任务时捕获的上下文**还原权限（玩家用其当前权限、命令方块为 GAMEMASTER、服务端为默认）。不能通过它绕过目标命令的权限检查。

## 任务与返回值

### 任务创建成功了但到期没执行？

按顺序排查：

1. **来源失效**：任务每 20 tick 扫描一次，玩家离线、命令方块被破坏（`commandBlockPolicy=cancel`）、维度消失都会取消任务并写日志。
2. **`enabled=false`**：模组被禁用后不再调度。
3. **执行上限**：`maxTasksPerTick`（默认 1000）满时任务顺延到下一 tick。
4. 到期执行的命令**本身没有输出**（如 `function`、成功的 `setblock`），本 Mod 不追加任何反馈。

日志里搜索 `来源失效` / `执行延迟任务` 可确认。

### 反馈里没有任务 ID？

`showTaskIdInFeedback=false` 时消息为 `已安排任务，将在 {n} tick 后执行 /{cmd}。`。ID 仍会作为返回值返回。

### 返回值是 1 而不是任务 ID？

`returnTaskId=false` 时成功创建固定返回 `1`。改回 `true` 即可。

### 为什么任务 ID 从 2 开始？

命令结果中 `0` 表示失败、`1` 表示成功。任务 ID 会被 `/execute store result` 读取，占用 `0`/`1` 会造成语义冲突，因此起始值最小为 2（`taskIdStart` 会强制 `>= 2`）。

### 反馈消息里的 ID 能被函数读取吗？

不能。聊天反馈仅供人工参考；程序化获取**只能**通过 `/execute store result`：

```mcfunction
/execute store result score @s icsl.last_id run ic run sleep sleep 20 run say hello
/ic run sleep clear score @s icsl.last_id
```

### 函数暂停（模式 B）能拿到任务 ID 吗？

原版函数无法读取命令结果。需要 ID 时改用 `run` 模式配合 `/execute store`（此时函数不会暂停）。

## 持久化

### 服务器重启后任务没了？

- **函数暂停任务本来就不持久化**（剩余函数条目无法序列化），重启后丢弃。
- `persistTasks=false` 时完全不保存。
- 恢复时来源已失效（玩家离线、命令方块消失、维度不存在）的任务会被丢弃并写日志。
- `formatVersion` 不兼容时停止加载全部任务并重写文件（日志有 `任务持久化格式版本不兼容`）。

### 停服期间延迟会走完吗？

不会。剩余时间只在服务器运行时递减，重启后从上次保存的 `remainingTicks` 继续。

### 持久化文件在哪？

`<世界存档目录>/icsl_tasks.json`，可用 `persistFile` 改名。写入是原子的（先写 `.tmp` 再替换），中途断电不会留下半截文件。

## 函数暂停

### 同一个函数里写了两个 `/sleep`，只有第一个生效？

每个函数条目只拦截**第一个** `/sleep <time>`；暂停恢复后剩余条目直接入队执行，不再经过拦截，第二个 `/sleep` 会因命令未挂载到全局而报 Unknown command。

解决办法：把后续的暂停放进被调用的子函数（子函数有自己的 `CallFunction` 调用，会被正常拦截）：

```mcfunction
# my:main.mcfunction
/sleep 20
say 第一段
/function my:second

# my:second.mcfunction
/sleep 20
say 第二段
```

### 暂停后函数里的后续命令全都不执行了？

正常情况下延迟到期后会继续。若以下情况发生，剩余命令会**立即继续**而不暂停：

- `enableFunctionPause=false`
- 队列已满（全局 / 每来源限额）
- 嵌套深度超过 `maxNestedDepth`（日志有 `函数暂停嵌套深度超出限制`）

### 和修改函数执行的 Mod 冲突？

模式 B 通过 Mixin 拦截原版 `CallFunction.execute`，与同样改写函数执行流程的 Mod 可能冲突（表现为函数不暂停或提前执行）。可设置 `enableFunctionPause=false` 关闭该功能，`run` 模式不受影响。

### 支持命令方块链暂停吗？

不支持。第一版仅实现数据包函数暂停；命令方块中请使用 `run` 模式延迟单条命令。

## 构建与安装

### 构建报错 `Configuration with name 'modImplementation' not found`？

Minecraft 26.2 是非混淆版本，Loom 不做 remap，不会创建 `modImplementation` 等 remap 配置。改用普通文件依赖：

```groovy
implementation files("./deps/ic-root/ic-root-0.1.0.jar")
```

### 编译找不到 `com.tt23xrstudio.icroot.*`？

`deps/ic-root/` 下缺少 IC-Root 产物。先在 IC-Root 项目 `gradlew build`，把 `build/libs/ic-root-0.1.0.jar` 复制到本项目 `deps/ic-root/`，再构建。

### 启动时报缺少 ic-root？

`fabric.mod.json` 硬依赖 `ic-root >= 0.1.0`，把 `ic-root-0.1.0.jar` 一起放进 `mods/`。

## 已知限制

- 命令方块链的「暂停后续连锁方块」不支持（仅函数暂停 + `run` 延迟）。
- 同一函数内只有第一个 `/sleep` 生效（见上）。
- 函数暂停任务不持久化，重启即丢。
- `feedbackLevel` / `logLevel` 为保留字段，当前版本未生效。
- `keepTaskHistory` 开启后历史仅存内存，没有查询命令。
