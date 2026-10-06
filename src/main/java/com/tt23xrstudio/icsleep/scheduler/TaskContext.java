package com.tt23xrstudio.icsleep.scheduler;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * 可序列化的执行上下文快照。
 *
 * 用于在延迟任务到期时尽量还原原始执行环境。
 * 不同来源使用到的字段不同：
 * - 玩家：uuid、dimension、位置、朝向
 * - 命令方块：dimension、blockX/Y/Z
 * - 服务端：无
 * - 函数：dimension、位置、朝向、entityUuid（若有执行实体）
 */
public final class TaskContext {

    /** 维度 ID，例如 minecraft:overworld */
    @SerializedName("dimension")
    public String dimension;

    /** 玩家或函数执行实体 UUID */
    @SerializedName("entityUuid")
    public String entityUuid;

    /** 命令方块坐标 */
    @SerializedName("blockX")
    public Integer blockX;
    @SerializedName("blockY")
    public Integer blockY;
    @SerializedName("blockZ")
    public Integer blockZ;

    @SerializedName("x")
    public double x;
    @SerializedName("y")
    public double y;
    @SerializedName("z")
    public double z;
    @SerializedName("yaw")
    public float yaw;
    @SerializedName("pitch")
    public float pitch;

    public TaskContext() {
    }

    /** 解析 UUID，格式非法时返回 null */
    public UUID entityUuidOrNull() {
        if (entityUuid == null || entityUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(entityUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 是否包含命令方块坐标 */
    public boolean hasBlockPos() {
        return blockX != null && blockY != null && blockZ != null;
    }
}
