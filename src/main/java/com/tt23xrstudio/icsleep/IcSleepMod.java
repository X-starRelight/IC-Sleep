package com.tt23xrstudio.icsleep;

import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import com.tt23xrstudio.icsleep.command.SleepCommand;
import com.tt23xrstudio.icsleep.persistence.TaskStore;
import com.tt23xrstudio.icsleep.scheduler.SleepScheduler;
import com.tt23xrstudio.icroot.ICMod;
import com.tt23xrstudio.icroot.ICSeries;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ICSl 模组入口。
 */
public class IcSleepMod implements ModInitializer {

    public static final String MOD_ID = "ic-sleep";
    /** 在 IC-Root 注册表中的 Mod 名，对应命令路径 /ic run <name> */
    public static final String SLEEP_IC_NAME = "sleep";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final SleepScheduler SCHEDULER = new SleepScheduler();
    private static final TaskStore TASK_STORE = new TaskStore();

    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        // 加载配置
        IcSleepConfig.load();

        // 注册到 IC-Root（统一入口 /ic）。必须在初始化阶段完成：
        // 服务器启动构建命令树时 ic-root 会挂载到 /ic run sleep 下并冻结注册表。
        // 命令树根字面量为配置中的 commandAlias（默认 sleep），
        // 因此完整调用路径为 /ic run sleep sleep <time> run <command>。
        ICMod mod = ICSeries.register(SLEEP_IC_NAME);
        mod.RegMod(SleepCommand::register);

        // 服务端生命周期
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            onServerStart(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // 停止前保存任务
            if (IcSleepConfig.get().persistTasks) {
                TASK_STORE.save(SCHEDULER);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            onServerStop();
        });

        // 每 tick 调度
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (IcSleepConfig.get().enabled) {
                SCHEDULER.tick();
            }
        });

        LOGGER.info("Improved Commands - Sleep 已加载。");
    }

    private void onServerStart(MinecraftServer server) {
        IcSleepMod.server = server;
        IcSleepConfig cfg = IcSleepConfig.get();
        SCHEDULER.onServerStart(server);
        SCHEDULER.resetIdAllocator(cfg.taskIdStart);
        // 绑定持久化路径并加载
        if (cfg.persistTasks) {
            TASK_STORE.bind(server.getWorldPath(LevelResource.ROOT), cfg.persistFile);
            TaskStore.LoadResult result = TASK_STORE.load();
            if (result.nextTaskId > 0) {
                SCHEDULER.setNextTaskId(result.nextTaskId);
            }
            if (!result.formatIncompatible) {
                // 回填任务的 ID 需经过分配器，但恢复是直接放入队列，需确保不冲突
                SCHEDULER.restore(result.tasks);
            } else {
                TASK_STORE.save(SCHEDULER);
            }
        }
    }

    private void onServerStop() {
        SCHEDULER.onServerStop();
        server = null;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static SleepScheduler getScheduler() {
        return SCHEDULER;
    }

    public static TaskStore getTaskStore() {
        return TASK_STORE;
    }
}