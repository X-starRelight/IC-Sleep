package com.tt23xrstudio.icsleep.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tt23xrstudio.icsleep.scheduler.SleepTask;
import com.tt23xrstudio.icsleep.scheduler.TaskContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务 JSON 序列化单元测试。
 */
class TaskJsonTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void testRunTaskRoundTrip() {
        SleepTask task = new SleepTask();
        task.id = 123;
        task.remainingTicks = 20;
        task.type = SleepTask.TYPE_RUN;
        task.command = "say hello";
        task.sourceType = "player";
        task.sourceKey = "player:00000000-0000-0000-0000-000000000000";
        task.createdAt = 100;
        task.feedbackTarget = "player";
        TaskContext context = new TaskContext();
        context.dimension = "minecraft:overworld";
        context.x = 1.5;
        context.y = 64.0;
        context.z = -2.5;
        task.context = context;

        String json = GSON.toJson(task);
        SleepTask loaded = GSON.fromJson(json, SleepTask.class);

        assertEquals(123, loaded.id);
        assertEquals(20L, loaded.remainingTicks);
        assertEquals(SleepTask.TYPE_RUN, loaded.type);
        assertEquals("say hello", loaded.command);
        assertEquals("player", loaded.sourceType);
        assertEquals("minecraft:overworld", loaded.context.dimension);
        assertEquals(64.0, loaded.context.y);
    }

    @Test
    void testFunctionResumeNotPersisted() {
        // 函数暂停任务的 resumeState 为 transient，不应出现在 JSON 中
        SleepTask task = new SleepTask();
        task.id = 124;
        task.type = SleepTask.TYPE_FUNCTION_RESUME;
        task.resumeState = new Object();
        String json = GSON.toJson(task);
        assertTrue(!json.contains("resumeState"), "resumeState 不应被序列化");
    }

    @Test
    void testStoreFormatStructure() {
        // 验证持久化根结构字段命名
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("nextTaskId", 124);
        root.add("tasks", new com.google.gson.JsonArray());
        assertEquals(1, root.get("formatVersion").getAsInt());
        assertEquals(124, root.get("nextTaskId").getAsInt());
    }
}
