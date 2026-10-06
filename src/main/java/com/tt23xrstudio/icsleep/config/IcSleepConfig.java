package com.tt23xrstudio.icsleep.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tt23xrstudio.icsleep.IcSleepMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ICSl 配置。使用自写 JSON 文件，位于 config/icsleep.json。
 * 加载时缺失字段自动补默认值，损坏时回退到默认配置。
 */
public final class IcSleepConfig {

    /** 默认时间单位：tick / second / minute / hour / day */
    public String defaultUnit = "tick";
    /** 命令别名 */
    public String commandAlias = "sleep";
    /** 最大延迟（tick） */
    public long maxDelayTicks = 72000L;
    /** 全局最大任务数 */
    public int maxGlobalTasks = 10000;
    /** 每来源最大任务数 */
    public int maxTasksPerSource = 100;
    /** 每 tick 最大执行任务数 */
    public int maxTasksPerTick = 1000;
    /** 是否启用模组 */
    public boolean enabled = true;
    /** 是否持久化 run 模式任务 */
    public boolean persistTasks = true;
    /** 持久化文件名 */
    public String persistFile = "icsl_tasks.json";
    /** 持久化保存间隔（tick） */
    public long saveIntervalTicks = 6000L;
    /** 命令方块被破坏策略：cancel / keep */
    public String commandBlockPolicy = "cancel";
    /** 是否支持函数暂停 */
    public boolean enableFunctionPause = true;
    /** 最大嵌套深度 */
    public int maxNestedDepth = 10;
    /** 是否将任务 ID 作为命令结果返回 */
    public boolean returnTaskId = true;
    /** 反馈中是否显示任务 ID */
    public boolean showTaskIdInFeedback = true;
    /** ID 起始值 */
    public int taskIdStart = 2;
    /** 是否保留已完成任务历史 */
    public boolean keepTaskHistory = false;
    /** 历史保留数量 */
    public int taskHistoryLimit = 100;
    /** 反馈详细度 */
    public String feedbackLevel = "normal";
    /** 日志级别 */
    public String logLevel = "info";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static IcSleepConfig instance;

    /** 获取当前配置实例，未加载时返回默认配置 */
    public static IcSleepConfig get() {
        if (instance == null) {
            instance = new IcSleepConfig();
        }
        return instance;
    }

    /** 从 config/icsleep.json 加载配置，文件不存在或损坏时使用默认值并写出 */
    public static void load() {
        Path path = configPath();
        IcSleepConfig cfg = new IcSleepConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                cfg = GSON.fromJson(json, IcSleepConfig.class);
                if (cfg == null) {
                    cfg = new IcSleepConfig();
                }
            } catch (Exception e) {
                IcSleepMod.LOGGER.error("读取配置文件失败，使用默认配置：{}", e.toString());
                cfg = new IcSleepConfig();
            }
        }
        cfg.validate();
        instance = cfg;
        save();
    }

    /** 将当前配置写回文件 */
    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException e) {
            IcSleepMod.LOGGER.error("保存配置文件失败：{}", e.toString());
        }
    }

    /** 对不合法值进行兜底修正 */
    private void validate() {
        if (maxDelayTicks < 0L) {
            maxDelayTicks = 72000L;
        }
        if (maxGlobalTasks < 1) {
            maxGlobalTasks = 10000;
        }
        if (maxTasksPerSource < 1) {
            maxTasksPerSource = 100;
        }
        if (maxTasksPerTick < 1) {
            maxTasksPerTick = 1000;
        }
        if (saveIntervalTicks < 1L) {
            saveIntervalTicks = 6000L;
        }
        if (maxNestedDepth < 1) {
            maxNestedDepth = 10;
        }
        if (taskIdStart < 2) {
            taskIdStart = 2;
        }
        if (taskHistoryLimit < 0) {
            taskHistoryLimit = 100;
        }
        if (commandAlias == null || commandAlias.isBlank()) {
            commandAlias = "sleep";
        }
        if (persistFile == null || persistFile.isBlank()) {
            persistFile = "icsl_tasks.json";
        }
        if (defaultUnit == null || defaultUnit.isBlank()) {
            defaultUnit = "tick";
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("icsleep.json");
    }
}
