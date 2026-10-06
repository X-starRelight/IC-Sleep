# IC-Sleep 使用文档

Improved Commands - Sleep（IC-Sleep）是一个 Fabric Mod，为命令执行流程引入基于游戏刻的延迟：`/ic run sleep sleep <time> run <command>` 在指定延迟后以原始执行上下文执行命令，数据包函数内裸写 `/sleep <time>` 可暂停函数、延迟后从下一条命令继续。

## 基本信息

| 项 | 值 |
|---|---|
| Mod ID | `ic-sleep` |
| 包名 | `com.tt23xrstudio.icsleep` |
| 开发者 | tt23xrstudio |
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 0.160.0+26.2 |
| IC-Root | >= 0.1.0 |
| Java | 25 |

## 特性

- **统一入口**：命令树挂载在 `/ic run sleep` 下，不污染全局命令空间，权限与反馈由 IC-Root 统一把控
- **两种延迟模式**：`run` 延迟执行单条命令（任意来源）；函数内 `/sleep <time>` 暂停并从下一条命令继续
- **时间参数**：`20` / `20t` / `1s` / `1m` / `1h` / `1d`，默认单位与最大延迟可配
- **任务 ID 驱动**：创建成功返回任务 ID（从 2 起），可用 `/execute store result` 捕获，配合 `clear score` / `clear storage` 程序化清除
- **上下文还原**：按玩家 / 命令方块 / 函数 / 服务端快照重建执行上下文
- **来源失效自动取消**：玩家离线、命令方块被破坏（可配 `keep`）、维度消失时自动取消任务
- **队列限额**：全局 10000、每来源 100、每 tick 1000，防止滥用刷任务
- **JSON 持久化**：`world/icsl_tasks.json` 原子写入，服务器重启后未执行任务继续倒计时

## 效果速览

```text
/ic run sleep sleep 1s run say hello
# 已安排任务 ID: 2，将在 20 tick 后执行 /say hello。          （返回 2）

/ic run sleep list
# 待执行任务（第 1/1 页，共 1 个）：
#   #2  剩余 15t  来源 player  say hello                     （返回 1）

/ic run sleep clear all
# 已清除 1 个任务。                                          （返回 1）
```

## 最小示例

```mcfunction
# 延迟执行单条命令（任意来源可用；20 tick 后执行）
/ic run sleep sleep 20 run say hello

# 函数内暂停：裸写别名，不带 /ic run 前缀
/sleep 20
say 延迟后继续
```

```text
/ic run sleep sleep 20 run say hello
# 已安排任务 ID: 2，将在 20 tick 后执行 /say hello。          （返回 2）
```

## 文档导航

| 文档 | 内容 |
|---|---|
| [getting-started.md](getting-started.md) | **从零上手**：环境要求、构建与安装、第一条延迟命令、游戏内验证 |
| [commands.md](commands.md) | **命令参考**：`run` / 函数暂停 / `list` / `info` / `clear` 的语义、消息文案、返回值、权限 |
| [config.md](config.md) | **配置参考**：`config/icsleep.json` 全部配置项、时间参数格式与换算 |
| [persistence.md](persistence.md) | **持久化**：`world/icsl_tasks.json` 结构、保存时机、恢复与降级规则 |
| [faq.md](faq.md) | **常见问题**：函数写法、任务消失、ID 从 2 开始、已知限制 |

## 阅读路径

- **第一次使用**：[getting-started.md](getting-started.md) → [commands.md](commands.md)
- **调整限额、单位、持久化**：[config.md](config.md) → [persistence.md](persistence.md)
- **程序化获取 / 清除任务 ID**：[commands.md](commands.md) § 返回值与 `execute store`
- **遇到报错或任务消失**：[faq.md](faq.md)

## 项目结构

```text
IC-Sleep/
├── docs_sleep/                   # 本文档目录
│   ├── README.md                 # 你在这里
│   ├── getting-started.md
│   ├── commands.md
│   ├── config.md
│   ├── persistence.md
│   └── faq.md
├── src/main/java/com/tt23xrstudio/icsleep/
│   ├── IcSleepMod.java           # 入口：注册到 IC-Root、生命周期与每 tick 调度
│   ├── command/SleepCommand.java # 命令树与实现：run / list / info / clear
│   ├── time/TimeParser.java      # 时间参数解析
│   ├── scheduler/                # 调度器、任务模型、上下文快照、ID 分配
│   ├── persistence/TaskStore.java # JSON 持久化
│   ├── config/IcSleepConfig.java # 配置 config/icsleep.json
│   └── mixin/                    # 拦截函数调用，实现模式 B 暂停
├── src/gametest/                 # 服务端 GameTest
├── src/test/                     # 单元测试
└── deps/ic-root/                 # IC-Root 构建产物（编译依赖）
```
