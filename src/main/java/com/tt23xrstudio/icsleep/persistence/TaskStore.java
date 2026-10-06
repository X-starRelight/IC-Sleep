package com.tt23xrstudio.icsleep.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import com.tt23xrstudio.icsleep.scheduler.SleepScheduler;
import com.tt23xrstudio.icsleep.scheduler.SleepTask;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务持久化。
 *
 * 使用 JSON，保存到世界存档目录（world/icsl_tasks.json）。
 * 采用原子写入：先写临时文件，再替换正式文件。
 * 仅持久化 run 模式任务；函数暂停任务不持久化。
 */
public final class TaskStore {

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path filePath;

    /** 绑定到当前服务器的世界存档路径 */
    public void bind(Path worldPath, String fileName) {
        this.filePath = worldPath.resolve(fileName).normalize();
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * 保存当前任务队列。
     */
    public void save(SleepScheduler scheduler) {
        if (filePath == null || !IcSleepConfig.get().persistTasks) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("nextTaskId", scheduler.getNextTaskId());
        JsonArray tasks = new JsonArray();
        for (SleepTask task : scheduler.getPendingTasks()) {
            // 只持久化 run 模式任务
            if (task.isRun()) {
                tasks.add(GSON.toJsonTree(task));
            }
        }
        root.add("tasks", tasks);
        try {
            Path dir = filePath.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            IcSleepMod.LOGGER.error("保存任务持久化文件失败：{}", e.toString());
        }
    }

    /**
     * 加载持久化任务。文件损坏时逐任务容错。
     *
     * @return 可恢复的任务列表（含 nextTaskId 信息通过返回对象携带）
     */
    public LoadResult load() {
        LoadResult result = new LoadResult();
        if (filePath == null || !Files.exists(filePath)) {
            return result;
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            IcSleepMod.LOGGER.error("读取任务持久化文件失败，跳过加载：{}", e.toString());
            return result;
        }
        if (root == null) {
            return result;
        }
        int version = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : -1;
        if (version != FORMAT_VERSION) {
            IcSleepMod.LOGGER.error("任务持久化格式版本不兼容（期望 {}，实际 {}），停止加载。", FORMAT_VERSION, version);
            result.formatIncompatible = true;
            return result;
        }
        if (root.has("nextTaskId")) {
            result.nextTaskId = root.get("nextTaskId").getAsInt();
        }
        JsonArray tasks = root.has("tasks") ? root.getAsJsonArray("tasks") : new JsonArray();
        for (JsonElement element : tasks) {
            try {
                SleepTask task = GSON.fromJson(element, SleepTask.class);
                if (task == null || task.type == null || !task.isRun()) {
                    continue;
                }
                result.tasks.add(task);
            } catch (JsonSyntaxException e) {
                IcSleepMod.LOGGER.warn("忽略损坏的任务记录：{}", e.toString());
            }
        }
        IcSleepMod.LOGGER.info("加载到 {} 个待执行任务。", result.tasks.size());
        return result;
    }

    /** 加载结果 */
    public static final class LoadResult {
        public boolean formatIncompatible = false;
        public int nextTaskId = -1;
        public final List<SleepTask> tasks = new ArrayList<>();
    }
}
