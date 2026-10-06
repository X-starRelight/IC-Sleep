package com.tt23xrstudio.icsleep.scheduler;

import java.util.Set;

/**
 * 任务 ID 分配器。
 *
 * 规则：
 * - ID 从配置起始值（默认 2）开始，禁止使用 0 和 1。
 * - 全局递增，持久化 nextTaskId。
 * - 达到 Integer.MAX_VALUE 后回绕到起始值，并跳过当前仍被占用的 ID。
 *
 * 该类不依赖 Minecraft 运行时，可单元测试。
 */
public final class TaskIdAllocator {

    /** 32 位有符号整数上限 */
    public static final int MAX_ID = Integer.MAX_VALUE;

    private int nextTaskId;
    private final int startId;

    public TaskIdAllocator(int startId) {
        this.startId = Math.max(2, startId);
        this.nextTaskId = this.startId;
    }

    /** 读取下一个待分配 ID（不消耗） */
    public int peek() {
        return nextTaskId;
    }

    /** 当前下一个 ID，用于持久化 */
    public int getNextTaskId() {
        return nextTaskId;
    }

    /** 恢复持久化得到的 nextTaskId */
    public void setNextTaskId(int value) {
        this.nextTaskId = value < startId ? startId : value;
    }

    /**
     * 分配一个新 ID，跳过占用 ID，必要时回绕。
     *
     * @param inUse 当前仍被占用的 ID 集合
     * @return 新 ID；若所有合法 ID 均被占用则返回 -1
     */
    public int allocate(Set<Integer> inUse) {
        int candidate = nextTaskId;
        int scanned = 0;
        while (scanned <= MAX_ID - startId + 1) {
            if (!inUse.contains(candidate)) {
                // 推进到下一个候选
                if (candidate == MAX_ID) {
                    nextTaskId = startId;
                } else {
                    nextTaskId = candidate + 1;
                }
                return candidate;
            }
            candidate = next(candidate);
            scanned++;
        }
        return -1;
    }

    private int next(int current) {
        return current == MAX_ID ? startId : current + 1;
    }
}
