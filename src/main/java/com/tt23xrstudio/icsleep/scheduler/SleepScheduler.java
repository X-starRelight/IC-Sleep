package com.tt23xrstudio.icsleep.scheduler;

import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局延迟任务调度器。
 *
 * 职责：
 * - 任务注册（含全局/每来源/每 tick 上限检查、ID 分配）。
 * - 每个服务器 tick 递减剩余时间，到期后按注册顺序执行。
 * - run 模式：重建原始执行上下文后执行命令。
 * - 函数暂停任务：在内存中保存剩余函数条目，延迟后恢复执行。
 * - 玩家离线/命令方块消失/维度消失时取消任务。
 */
public final class SleepScheduler {

    /** 注册顺序的任务表 */
    private final Map<Integer, SleepTask> tasks = new LinkedHashMap<>();
    /** 每来源任务计数 */
    private final Map<String, Integer> taskCountBySource = new HashMap<>();
    /** 每创建者最近任务 ID（用于 clear last） */
    private final Map<String, Integer> lastTaskIdByCreator = new HashMap<>();
    /** 历史记录 */
    private final List<SleepTask> history = new ArrayList<>();

    private TaskIdAllocator idAllocator;
    private MinecraftServer server;
    private long nextSaveTick = 0;
    private long tickCount = 0;
    /** 当前嵌套执行深度 */
    private int executionDepth = 0;

    public SleepScheduler() {
        resetIdAllocator(IcSleepConfig.get().taskIdStart);
    }

    /** 重置 ID 分配器（加载配置/启动时调用） */
    public void resetIdAllocator(int startId) {
        this.idAllocator = new TaskIdAllocator(startId);
    }

    /** 服务器启动时调用 */
    public void onServerStart(MinecraftServer server) {
        this.server = server;
    }

    /** 服务器停止时调用 */
    public void onServerStop() {
        this.server = null;
        this.tasks.clear();
        this.taskCountBySource.clear();
        this.lastTaskIdByCreator.clear();
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** 当前任务总数 */
    public int size() {
        return tasks.size();
    }

    /** 读取 nextTaskId（用于持久化） */
    public int getNextTaskId() {
        return idAllocator.getNextTaskId();
    }

    /** 恢复持久化的 nextTaskId */
    public void setNextTaskId(int value) {
        idAllocator.setNextTaskId(value);
    }

    /**
     * 注册一个 run 模式延迟任务。
     *
     * @param source    发起命令的执行上下文
     * @param command   要延迟执行的命令
     * @param ticks     延迟刻数
     * @return 任务 ID；注册失败（队列满等）返回 -1
     */
    public int scheduleRun(CommandSourceStack source, String command, long ticks) {
        IcSleepConfig config = IcSleepConfig.get();
        if (!config.enabled) {
            return -1;
        }
        if (ticks < 0 || ticks > config.maxDelayTicks) {
            return -1;
        }
        SleepTask task = createTask(source, command, ticks, TYPE_RUN);
        if (task == null) {
            return -1;
        }
        task.type = SleepTask.TYPE_RUN;
        task.command = command;
        return registerTask(task, source);
    }

    /**
     * 注册一个函数暂停恢复任务。
     *
     * @param source    函数执行上下文
     * @param remaining 剩余函数条目
     * @param ticks     延迟刻数
     * @param depth     嵌套深度
     * @return 任务 ID；失败返回 -1
     */
    public int scheduleFunctionResume(CommandSourceStack source, List<UnboundEntryAction<CommandSourceStack>> remaining, long ticks, int depth) {
        IcSleepConfig config = IcSleepConfig.get();
        if (!config.enabled || !config.enableFunctionPause) {
            return -1;
        }
        if (ticks < 0 || ticks > config.maxDelayTicks) {
            return -1;
        }
        if (depth > config.maxNestedDepth) {
            IcSleepMod.LOGGER.warn("函数暂停嵌套深度超出限制 {}，取消暂停。", config.maxNestedDepth);
            return -1;
        }
        SleepTask task = createTask(source, null, ticks, TYPE_FUNCTION_RESUME);
        if (task == null) {
            return -1;
        }
        task.type = SleepTask.TYPE_FUNCTION_RESUME;
        task.resumeState = new FunctionResumeState(remaining, source, depth);
        return registerTask(task, source);
    }

    private SleepTask createTask(CommandSourceStack source, String command, long ticks, String type) {
        int id = idAllocator.allocate(tasks.keySet());
        if (id < 0) {
            IcSleepMod.LOGGER.warn("任务 ID 分配失败：所有合法 ID 均被占用。");
            return null;
        }
        SleepTask task = new SleepTask();
        task.id = id;
        task.remainingTicks = ticks;
        task.command = command;
        task.type = type;
        task.createdAt = tickCount;
        task.context = TaskContextUtil.capture(source);
        task.permissionLevel = 2;
        // 记录来源与创建者
        TaskContextUtil.SourceInfo info = TaskContextUtil.sourceInfo(source, task.context);
        task.sourceType = info.sourceType().id();
        task.sourceKey = info.sourceKey();
        task.creatorUuid = info.creatorUuid();
        task.feedbackTarget = info.feedbackTarget();
        return task;
    }

    /** 内部注册：执行上限检查后放入队列 */
    private int registerTask(SleepTask task, CommandSourceStack source) {
        IcSleepConfig config = IcSleepConfig.get();
        if (tasks.size() >= config.maxGlobalTasks) {
            sendFailure(source, "队列已满：全局任务数达到上限 " + config.maxGlobalTasks + "。");
            return -1;
        }
        int count = taskCountBySource.getOrDefault(task.sourceKey, 0);
        if (count >= config.maxTasksPerSource) {
            sendFailure(source, "队列已满：该来源任务数达到上限 " + config.maxTasksPerSource + "。");
            return -1;
        }
        tasks.put(task.id, task);
        taskCountBySource.put(task.sourceKey, count + 1);
        if (task.creatorUuid != null) {
            lastTaskIdByCreator.put(task.creatorUuid, task.id);
        }
        return task.id;
    }

    /**
     * 每个服务器 tick 调用：递减剩余时间并执行到期任务。
     */
    public void tick() {
        if (server == null) {
            return;
        }
        tickCount++;
        IcSleepConfig config = IcSleepConfig.get();
        // 1. 玩家离线/命令方块消失/维度消失 → 取消任务
        if (tickCount % 20L == 0L) {
            cancelInvalidSources();
        }
        // 2. 递减并收集到期任务
        List<SleepTask> due = new ArrayList<>();
        Iterator<SleepTask> it = tasks.values().iterator();
        while (it.hasNext()) {
            SleepTask task = it.next();
            task.remainingTicks--;
            if (task.remainingTicks <= 0L) {
                due.add(task);
                it.remove();
                decrementSourceCount(task);
            }
        }
        // 3. 按注册顺序执行，受每 tick 上限限制
        int executed = 0;
        for (SleepTask task : due) {
            if (executed >= config.maxTasksPerTick) {
                // 超出上限的任务保留，下一 tick 再执行
                task.remainingTicks = 0L;
                tasks.put(task.id, task);
                incrementSourceCount(task);
                continue;
            }
            executed++;
            executeTask(task);
        }
        // 4. 定期持久化
        if (config.persistTasks && tickCount >= nextSaveTick) {
            nextSaveTick = tickCount + config.saveIntervalTicks;
            IcSleepMod.getTaskStore().save(this);
        }
    }

    private void incrementSourceCount(SleepTask task) {
        taskCountBySource.merge(task.sourceKey, 1, Integer::sum);
    }

    private void decrementSourceCount(SleepTask task) {
        Integer c = taskCountBySource.get(task.sourceKey);
        if (c != null) {
            if (c <= 1) {
                taskCountBySource.remove(task.sourceKey);
            } else {
                taskCountBySource.put(task.sourceKey, c - 1);
            }
        }
    }

    private void executeTask(SleepTask task) {
        try {
            if (task.isRun()) {
                executeRunTask(task);
            } else if (task.isFunctionResume()) {
                executeResumeTask(task);
            }
        } catch (Exception e) {
            IcSleepMod.LOGGER.error("执行延迟任务 {} 失败：{}", task.id, e.toString());
        }
    }

    /** 执行 run 模式任务 */
    private void executeRunTask(SleepTask task) {
        CommandSourceStack source = TaskContextUtil.rebuild(server, task);
        if (source == null) {
            IcSleepMod.LOGGER.info("任务 {} 来源失效，取消执行。", task.id);
            return;
        }
        if (task.command == null || task.command.isBlank()) {
            return;
        }
        executionDepth++;
        try {
            server.getCommands().performPrefixedCommand(source, task.command);
        } finally {
            executionDepth--;
        }
        addHistory(task);
    }

    /** 执行函数暂停恢复任务 */
    private void executeResumeTask(SleepTask task) {
        if (!(task.resumeState instanceof FunctionResumeState state)) {
            return;
        }
        CommandSourceStack source = state.source();
        if (source == null || source.getServer() == null) {
            IcSleepMod.LOGGER.info("函数暂停任务 {} 来源失效，取消恢复。", task.id);
            return;
        }
        if (state.remaining().isEmpty()) {
            addHistory(task);
            return;
        }
        executionDepth++;
        try {
            Commands.executeCommandInContext(source, ctx -> {
                // 在新的执行上下文中恢复剩余函数条目
                Frame frame = new Frame(0, CommandResultCallback.EMPTY,
                        ctx.frameControlForDepth(0));
                for (UnboundEntryAction<CommandSourceStack> entry : state.remaining()) {
                    ctx.queueNext(new CommandQueueEntry<>(frame, entry.bind(source)));
                }
            });
        } finally {
            executionDepth--;
        }
        addHistory(task);
    }

    /** 当前嵌套执行深度 */
    public int getExecutionDepth() {
        return executionDepth;
    }

    /** 取消指定任务，返回是否找到 */
    public boolean cancelTask(int id) {
        SleepTask task = tasks.remove(id);
        if (task == null) {
            return false;
        }
        decrementSourceCount(task);
        IcSleepMod.LOGGER.info("取消任务 ID: {}", id);
        return true;
    }

    /** 清除执行者最近创建的任务，返回是否清除 */
    public boolean cancelLast(String creatorUuid) {
        if (creatorUuid == null) {
            return false;
        }
        Integer lastId = lastTaskIdByCreator.get(creatorUuid);
        if (lastId == null) {
            return false;
        }
        if (cancelTask(lastId)) {
            lastTaskIdByCreator.remove(creatorUuid);
            return true;
        }
        return false;
    }

    /** 清除全部任务，返回清除数量 */
    public int cancelAll() {
        int count = tasks.size();
        tasks.clear();
        taskCountBySource.clear();
        return count;
    }

    /** 按 ID 获取任务 */
    public SleepTask getTask(int id) {
        return tasks.get(id);
    }

    /** 返回所有待执行任务（按注册顺序） */
    public List<SleepTask> getPendingTasks() {
        return new ArrayList<>(tasks.values());
    }

    /** 取消因来源失效的任务（玩家离线等） */
    private void cancelInvalidSources() {
        IcSleepConfig config = IcSleepConfig.get();
        Iterator<Map.Entry<Integer, SleepTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            SleepTask task = it.next().getValue();
            if (TaskContextUtil.isSourceInvalid(server, task, config)) {
                IcSleepMod.LOGGER.info("任务 {} 来源失效，取消。", task.id);
                it.remove();
                decrementSourceCount(task);
            }
        }
    }

    private void addHistory(SleepTask task) {
        IcSleepConfig config = IcSleepConfig.get();
        if (!config.keepTaskHistory || config.taskHistoryLimit <= 0) {
            return;
        }
        history.add(task);
        while (history.size() > config.taskHistoryLimit) {
            history.remove(0);
        }
    }

    /** 恢复已持久化的任务（服务器启动时调用） */
    public void restore(List<SleepTask> restoredTasks) {
        for (SleepTask task : restoredTasks) {
            if (tasks.containsKey(task.id)) {
                continue;
            }
            // 恢复前先检查来源是否有效
            if (server != null && TaskContextUtil.isSourceInvalid(server, task, IcSleepConfig.get())) {
                IcSleepMod.LOGGER.info("恢复任务 {} 时来源失效，丢弃。", task.id);
                continue;
            }
            tasks.put(task.id, task);
            incrementSourceCount(task);
        }
    }

    private static void sendFailure(CommandSourceStack source, String message) {
        if (source != null && !source.isSilent()) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(message));
        }
    }

    /** 标记类型常量，供命令层使用 */
    public static final String TYPE_RUN = SleepTask.TYPE_RUN;
    public static final String TYPE_FUNCTION_RESUME = SleepTask.TYPE_FUNCTION_RESUME;

    /** 函数暂停恢复状态（仅内存） */
    public record FunctionResumeState(
            List<UnboundEntryAction<CommandSourceStack>> remaining,
            CommandSourceStack source,
            int depth) {
    }
}
