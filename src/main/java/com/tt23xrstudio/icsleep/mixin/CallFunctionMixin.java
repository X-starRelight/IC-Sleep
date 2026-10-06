package com.tt23xrstudio.icsleep.mixin;

import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.command.SleepCommand;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import com.tt23xrstudio.icsleep.time.TimeParseException;
import com.tt23xrstudio.icsleep.time.TimeParser;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.ContinuationTask;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截函数调用，实现“暂停并继续执行后续命令”（模式 B）。
 *
 * 当函数条目中出现 /sleep <time>（无 run）时：
 * 1. 立即执行该条目之前的命令。
 * 2. 跳过 /sleep 条目，保存其后剩余条目并注册恢复任务。
 * 3. 延迟到期后从下一条命令继续。
 */
@Mixin(CallFunction.class)
public abstract class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow
    @Final
    private InstantiatedFunction<T> function;

    @Shadow
    @Final
    private CommandResultCallback resultCallback;

    @Shadow
    @Final
    private boolean returnParentFrame;

    /**
     * 在函数开始执行前检查是否包含模式 B 的 /sleep。
     * 若包含则自行调度并取消原版流程。
     */
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void icsleep$intercept(T source, ExecutionContext<T> context, Frame frame, CallbackInfo ci) {
        IcSleepConfig config = IcSleepConfig.get();
        if (!config.enabled || !config.enableFunctionPause) {
            return;
        }
        if (!(source instanceof CommandSourceStack)) {
            return;
        }
        List<UnboundEntryAction<T>> entries = function.entries();
        int sleepIndex = findModeBIndex(entries, config);
        if (sleepIndex < 0) {
            return;
        }
        // 取消原版整体调度
        ci.cancel();

        // 构造与 CallFunction.execute 相同的子帧
        int depth = frame.depth() + 1;
        Frame.FrameControl control = returnParentFrame
                ? frame.frameControl()
                : context.frameControlForDepth(depth);
        Frame childFrame = new Frame(depth, resultCallback, control);

        // 1. 执行 /sleep 之前的命令
        List<UnboundEntryAction<T>> prefix = new ArrayList<>(entries.subList(0, sleepIndex));
        scheduleAll(context, childFrame, prefix, source);

        // 2. 解析延迟时间并注册恢复任务
        String input = commandOf(entries.get(sleepIndex));
        long ticks = parseTicks(input, config);
        if (ticks < 0) {
            // 解析失败，跳过该条目继续执行剩余命令
            List<UnboundEntryAction<T>> rest = new ArrayList<>(entries.subList(sleepIndex + 1, entries.size()));
            scheduleAll(context, childFrame, rest, source);
            return;
        }

        List<UnboundEntryAction<T>> remaining = new ArrayList<>(entries.subList(sleepIndex + 1, entries.size()));

        // 3. 注册恢复任务（仅内存）
        @SuppressWarnings("unchecked")
        List<UnboundEntryAction<CommandSourceStack>> remainingCss =
                (List<UnboundEntryAction<CommandSourceStack>>) (List<?>) remaining;
        int taskId = IcSleepMod.getScheduler().scheduleFunctionResume(
                (CommandSourceStack) (Object) source, remainingCss, ticks,
                IcSleepMod.getScheduler().getExecutionDepth() + 1);
        if (taskId < 0) {
            // 注册失败，直接继续执行剩余命令
            scheduleAll(context, childFrame, remaining, source);
            return;
        }
        if (config.showTaskIdInFeedback) {
            ((CommandSourceStack) (Object) source).sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                            "函数已暂停，任务 ID: " + taskId + "，将在 " + ticks + " tick 后继续。"), false);
        }
    }

    /** 将一批条目按顺序入队执行 */
    private void scheduleAll(ExecutionContext<T> context, Frame frame,
                             List<UnboundEntryAction<T>> entries, T source) {
        if (entries.isEmpty()) {
            return;
        }
        ContinuationTask.schedule(context, frame, entries,
                (f, arg) -> new CommandQueueEntry<>(f, arg.bind(source)));
    }

    /** 查找第一个模式 B 的 /sleep 条目索引，找不到返回 -1 */
    private int findModeBIndex(List<UnboundEntryAction<T>> entries, IcSleepConfig config) {
        for (int i = 0; i < entries.size(); i++) {
            String input = commandOf(entries.get(i));
            if (SleepCommand.isModeB(config.commandAlias, input)) {
                return i;
            }
        }
        return -1;
    }

    /** 读取条目对应的命令字符串，非命令条目返回 null */
    private String commandOf(UnboundEntryAction<T> entry) {
        if (entry instanceof BuildContexts<?> buildContexts) {
            return ((BuildContextsAccessor) buildContexts).icsleep$getCommandInput();
        }
        return null;
    }

    /** 解析模式 B 的时间参数，失败返回 -1 */
    private long parseTicks(String input, IcSleepConfig config) {
        if (input == null) {
            return -1L;
        }
        String s = input.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        String rest = s.substring(config.commandAlias.length()).trim();
        try {
            return TimeParser.parse(rest, config.defaultUnit, config.maxDelayTicks).ticks();
        } catch (TimeParseException e) {
            return -1L;
        }
    }
}
