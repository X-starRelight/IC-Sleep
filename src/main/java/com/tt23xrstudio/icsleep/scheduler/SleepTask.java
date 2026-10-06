package com.tt23xrstudio.icsleep.scheduler;

import com.google.gson.annotations.SerializedName;

/**
 * 延迟任务模型。
 *
 * 支持两种类型：
 * - run：延迟执行单条命令
 * - function_resume：函数暂停后恢复执行
 *
 * 函数恢复任务仅存在于内存，不参与持久化。
 */
public final class SleepTask {

    /** 任务类型：延迟执行单条命令 */
    public static final String TYPE_RUN = "run";
    /** 任务类型：函数暂停后恢复 */
    public static final String TYPE_FUNCTION_RESUME = "function_resume";

    @SerializedName("id")
    public int id;

    /** 剩余延迟（tick） */
    @SerializedName("remainingTicks")
    public long remainingTicks;

    @SerializedName("type")
    public String type = TYPE_RUN;

    /** run 模式要执行的命令字符串 */
    @SerializedName("command")
    public String command;

    /** 函数恢复模式：剩余函数条目（仅内存，不序列化） */
    public transient Object resumeState;

    @SerializedName("sourceType")
    public String sourceType = SourceType.SERVER.id();

    /** 来源标识：玩家 UUID / 命令方块坐标字符串 / 函数 ID 等，用于每来源限额与 last 查询 */
    @SerializedName("sourceKey")
    public String sourceKey = "server";

    @SerializedName("permissionLevel")
    public int permissionLevel = 2;

    /** 创建时的游戏时间 */
    @SerializedName("createdAt")
    public long createdAt;

    @SerializedName("context")
    public TaskContext context = new TaskContext();

    /** 反馈目标：player / none / log */
    @SerializedName("feedbackTarget")
    public String feedbackTarget = "none";

    /** 创建者 UUID，用于 /sleep clear last */
    @SerializedName("creatorUuid")
    public String creatorUuid;

    public boolean isRun() {
        return TYPE_RUN.equals(type);
    }

    public boolean isFunctionResume() {
        return TYPE_FUNCTION_RESUME.equals(type);
    }
}
