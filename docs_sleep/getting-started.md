# 快速上手

本指南帮助你构建、安装 `ic-sleep`，发出第一条延迟命令并在游戏内验证。

## 1. 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 0.160.0+26.2 |
| IC-Root | >= 0.1.0 |
| Java | 25 |

`ic-sleep` 是 IC-Root 生态的成员 Mod，`fabric.mod.json` 中硬依赖 `ic-root`，缺少 ic-root 时加载器会直接报错。

## 2. 构建

### 2.1 先准备 IC-Root 产物

本项目通过普通文件依赖引用 IC-Root：

```groovy
// build.gradle
implementation files("./deps/ic-root/ic-root-0.1.0.jar")
```

若 `deps/ic-root/` 下已有 jar（仓库附带），可跳过本步。否则先构建 IC-Root 再复制：

```bash
cd ../IC-Root
gradlew build
# 产物：IC-Root/build/libs/ic-root-0.1.0.jar
```

```bash
copy ..\IC-Root\build\libs\ic-root-0.1.0.jar deps\ic-root\
```

> **重要：不要使用 `modImplementation`**
> Minecraft 26.2 是非混淆版本，Loom 不做 remap，`modImplementation` 配置**不会被创建**，使用会报错：
> `Configuration with name 'modImplementation' not found`。
> 直接用普通 `implementation files(...)` 即可。

### 2.2 构建本项目

```bash
gradlew build
```

产物在 `build/libs/ic-sleep-0.1.0.jar`。

### 2.3 运行测试（可选）

```bash
gradlew test        # 单元测试：时间解析、ID 分配、JSON 持久化
gradlew runGameTest # 服务端 GameTest：run 模式、错误时间、ID、clear
```

## 3. 安装

两个 jar 都放进服务端（或单人游戏）的 `mods/` 目录：

```text
mods/
├── ic-root-0.1.0.jar
└── ic-sleep-0.1.0.jar
```

`ic-sleep` 生效于服务端：单人游戏在集成服务端生效，多人服务器客户端无需安装。

## 4. 第一条延迟命令

进入游戏后需要 OP 或控制台权限（IC-Root 要求 `Permissions.COMMANDS_MODERATOR`，整棵 `/ic` 树不可见也不可执行时先 `/op 你的名字`）。

```text
/ic run sleep sleep 1s run say hello
```

反馈与返回值：

```text
# 已安排任务 ID: 2，将在 20 tick 后执行 /say hello。          （返回 2）
```

1 秒（20 tick）后，服务器以你发起命令时的上下文执行 `/say hello`。

时间参数支持 `20`、`20t`、`1s`、`1m`、`1h`、`1d`，详见 [config.md](config.md) § 时间参数。

## 5. 在函数中暂停（模式 B）

写在数据包函数里，**裸写别名、不带 `/ic run` 前缀**：

```mcfunction
# my:wait.mcfunction
/sleep 20
say 20 tick 后继续
say 函数恢复执行
```

函数执行到 `/sleep 20` 时暂停，先执行完 `/sleep` 之前的条目；20 tick 后从 `say 20 tick 后继续` 继续执行。

> 函数外（聊天栏、控制台、命令方块）不能用 `/sleep <time>`，会收到错误提示并被引导使用 `run` 子命令。
> 函数内延迟单条命令则仍用 `/ic run sleep sleep <time> run <command>`。

## 6. 捕获任务 ID（可选）

任务 ID 从 `2` 开始（避开 `0`=失败、`1`=成功 的语义），是程序化获取任务的唯一途径——反馈消息里的 ID 仅供人工参考。

```mcfunction
# 存入记分板
/execute store result score @s icsl.last_id run ic run sleep sleep 20 run say hello
# 之后清除该任务
/ic run sleep clear score @s icsl.last_id
```

```mcfunction
# 存入 storage，配合宏函数
/execute store result storage icsl:tmp last_id int 1 run ic run sleep sleep 20 run say hello
```

## 7. 游戏内验证

需要 OP 或控制台权限。

| 命令 | 预期消息 | 返回值 |
|---|---|---|
| `/ic run sleep sleep 20 run say hi` | `已安排任务 ID: 2，将在 20 tick 后执行 /say hi。` | `2` |
| `/ic run sleep list` | `待执行任务（第 1/1 页，共 1 个）：` + 任务行 | 任务总数 |
| `/ic run sleep info 2` | `任务 2 详情：` + ID/类型/剩余/来源/命令 | `1` |
| `/ic run sleep clear last` | `已清除你最近创建的任务。` | `1` |
| `/ic run sleep clear all` | `已清除 1 个任务。` | 清除数量 |
| `/ic run sleep sleep abc run say hi` | `无效时间：abc。示例：20、20t、1s、1m。` | 失败，不创建任务 |

补全：`/ic run ` → Tab 选择 `sleep`；再 Tab 查看 `list` / `info` / `clear` 与时间参数提示。

## 8. 下一步

- 命令完整语义与消息文案：[commands.md](commands.md)
- 调整限额、时间单位、反馈与持久化：[config.md](config.md)、[persistence.md](persistence.md)
- 遇到问题：[faq.md](faq.md)
- IC-Root 生态接入（给自己的 Mod 注册命令）：见 IC-Root 文档 `docs_icroot/getting-started.md`
