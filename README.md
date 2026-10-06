# Improved Commands - Sleep

Improved Commands - Sleep（IC-Sleep）是一个 Fabric Mod，为命令执行流程引入基于游戏刻的延迟：`/ic run sleep sleep <time> run <command>` 在指定延迟后以原始执行上下文执行命令，数据包函数内裸写 `/sleep <time>` 可暂停函数、延迟后从下一条命令继续，并附带任务 ID 返回值、队列限额、来源失效自动取消与 JSON 持久化。命令统一挂载在 IC-Root 的 `/ic run sleep` 下。

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
| 构建 | Gradle + fabric-loom |

## 特性

- **统一入口**：命令树挂载在 `/ic run sleep` 下，不污染全局命令空间，权限与反馈由 IC-Root 统一把控
- **两种延迟模式**：`run` 延迟执行单条命令（玩家 / 控制台 / 命令方块 / 函数均可发起）；函数内 `/sleep <time>` 暂停并从下一条命令继续
- **时间参数**：`20` / `20t` / `1s` / `1m` / `1h` / `1d`，默认单位与最大延迟可配
- **任务 ID 驱动**：创建成功返回任务 ID（从 2 起，避开 0/1），可用 `/execute store result` 捕获，配合 `clear score` / `clear storage` 程序化清除
- **上下文还原**：按玩家 / 命令方块 / 函数 / 服务端快照重建执行上下文，延迟后的命令与原版直接执行一致
- **来源失效自动取消**：玩家离线、命令方块被破坏（可配 `keep`）、维度消失时自动取消任务
- **队列限额**：全局 10000、每来源 100、每 tick 1000，防止滥用刷任务
- **JSON 持久化**：`world/icsl_tasks.json` 原子写入，服务器重启后未执行任务继续倒计时
- **管理命令**：`list` 分页列出、`info` 查看详情、`clear` 支持全部 / 指定 / 最近 / 记分板 / storage 五种清除方式

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

函数内暂停（写在 `.mcfunction` 中，**裸写别名、不带 `/ic run` 前缀**）：

```mcfunction
/sleep 20
say 20 tick 后继续
```

```text
# 函数执行到 /sleep 20 时暂停，20 tick 后输出：
20 tick 后继续
```

## 快速上手

### 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 0.160.0+26.2 |
| IC-Root | >= 0.1.0 |
| Java | 25 |

### 构建

```bash
gradlew build
```

产物在 `build/libs/` 下。本项目通过 `deps/ic-root/ic-root-0.1.0.jar` 引用 IC-Root，构建前请先在 IC-Root 项目执行 `gradlew build` 并把产物放到该目录（本仓库已附带）。

### 安装

`ic-sleep` 硬依赖 `ic-root`，两个 jar 都要放进 `mods/`：

```text
mods/
├── ic-root-0.1.0.jar
└── ic-sleep-0.1.0.jar
```

> **开发时不要使用 `modImplementation`** —— Minecraft 26.2 为非混淆版本，Loom 不做 remap，`modImplementation` 配置不存在会直接报错；本项目用普通 `implementation files("./deps/ic-root/ic-root-0.1.0.jar")` 引用。

### 快速使用

延迟执行一条命令（任意来源）：

```text
/ic run sleep sleep 1s give @p minecraft:apple 1
```

在函数中暂停（`my:wait.mcfunction`）：

```mcfunction
/sleep 20
say 继续执行
```

捕获任务 ID 供后续使用：

```mcfunction
/execute store result score @s icsl.last_id run ic run sleep sleep 20 run say hello
```

管理任务：

```text
/ic run sleep list
/ic run sleep info 2
/ic run sleep clear last
```

完整上手流程见 [docs_sleep/getting-started.md](docs_sleep/getting-started.md)。

## 项目结构

```text
IC-Sleep/
├── src/main/java/com/tt23xrstudio/icsleep/
│   ├── IcSleepMod.java           # 入口：注册到 IC-Root、生命周期与每 tick 调度
│   ├── command/SleepCommand.java # 命令树与实现：run / list / info / clear
│   ├── time/TimeParser.java      # 时间参数解析（20 / 20t / 1s / 1m / 1h / 1d）
│   ├── scheduler/                # 调度器、任务模型、上下文快照、ID 分配
│   ├── persistence/TaskStore.java # JSON 持久化（原子写入）
│   ├── config/IcSleepConfig.java # 配置 config/icsleep.json
│   └── mixin/                    # 拦截函数调用，实现模式 B 暂停
├── src/gametest/                 # 服务端 GameTest
├── src/test/                     # 单元测试（时间解析 / ID 分配 / JSON）
├── docs_sleep/                   # 使用文档
├── deps/ic-root/                 # IC-Root 构建产物（编译依赖）
├── build.gradle                   # Fabric Loom 构建配置
└── .github/workflows/            # CI 自动构建
```

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs_sleep/getting-started.md](docs_sleep/getting-started.md) | 从零上手：环境要求、构建与安装、第一条延迟命令、游戏内验证 |
| [docs_sleep/commands.md](docs_sleep/commands.md) | 命令参考：run / 函数暂停 / list / info / clear 的语义、消息、返回值、权限 |
| [docs_sleep/config.md](docs_sleep/config.md) | 配置参考：`config/icsleep.json` 全部配置项与时间参数格式 |
| [docs_sleep/persistence.md](docs_sleep/persistence.md) | 持久化：`world/icsl_tasks.json` 结构、保存时机、恢复与降级规则 |
| [docs_sleep/faq.md](docs_sleep/faq.md) | 常见问题：函数写法、任务消失、ID 从 2 开始、已知限制 |

## 许可证

本项目采用 [GNU GPL-3.0](LICENSE) 许可证。
