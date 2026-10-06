package com.tt23xrstudio.icsleep.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.CommandDispatcher;
import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import com.tt23xrstudio.icsleep.scheduler.SleepScheduler;
import com.tt23xrstudio.icsleep.scheduler.SleepTask;
import com.tt23xrstudio.icsleep.time.TimeParseException;
import com.tt23xrstudio.icsleep.time.TimeParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;

import java.util.List;

/**
 * 睡眠命令注册与实现。
 *
 * <p>命令树不再注册到全局命令空间，而是交给 IC-Root 挂载到统一入口下：
 * {@code /ic run <name> <command> ...}，其中 name 为 {@code sleep}，
 * command 根字面量为配置项 {@code commandAlias}（默认 {@code sleep}），
 * 即 {@code /ic run sleep sleep <time> run <command>}。
 */
public final class SleepCommand {

    private SleepCommand() {
    }

    /**
     * 模式 B 出现在非函数上下文时的错误提示。
     * 按当前别名动态生成，始终指向 IC 统一入口的新路径。
     */
    private static SimpleCommandExceptionType notInFunction() {
        String alias = IcSleepConfig.get().commandAlias;
        return new SimpleCommandExceptionType(Component.literal(
                "该写法只能用于数据包函数中。若只想延迟一条命令，请使用 /ic run "
                        + IcSleepMod.SLEEP_IC_NAME + " " + alias + " <time> run <command>。"));
    }

    /**
     * 注册命令树，由 IC-Root 的 {@code ICMod.RegMod} 在初始化阶段回调。
     *
     * <p>不再添加 {@code .requires(...)} 权限限制：{@code /ic} 整棵树已由
     * ic-root 统一要求 moderator 权限，且 {@code /ic run} 不会提升执行源权限。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        String alias = IcSleepConfig.get().commandAlias;
        dispatcher.register(Commands.literal(alias)
                // 模式 B：/ic run sleep sleep <time>（仅函数内有效，此处一律报错引导使用 run）
                .then(Commands.argument("time", StringArgumentType.word())
                        .executes(SleepCommand::executeModeB)
                        // 模式 A：/ic run sleep sleep <time> run <command>
                        .then(Commands.literal("run")
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(SleepCommand::executeRun))))
                // 管理命令：list
                .then(Commands.literal("list")
                        .executes(context -> executeList(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> executeList(context,
                                        IntegerArgumentType.getInteger(context, "page")))))
                // 管理命令：info <id>
                .then(Commands.literal("info")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(SleepCommand::executeInfo)))
                // 管理命令：clear 系列
                .then(Commands.literal("clear")
                        .executes(SleepCommand::executeClearAll)
                        .then(Commands.literal("all").executes(SleepCommand::executeClearAll))
                        .then(Commands.literal("last").executes(SleepCommand::executeClearLast))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(SleepCommand::executeClearId))
                        .then(Commands.literal("score")
                                .then(Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .then(Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(SleepCommand::executeClearScore))))
                        .then(Commands.literal("storage")
                                .then(Commands.argument("storageId", IdentifierArgument.id())
                                        .then(Commands.argument("path", NbtPathArgument.nbtPath())
                                                .executes(SleepCommand::executeClearStorage))))));
    }

    /** 模式 B：函数暂停，非函数上下文一律报错 */
    private static int executeModeB(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        throw notInFunction().create();
    }

    /** 模式 A：延迟执行单条命令 */
    private static int executeRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String timeArg = StringArgumentType.getString(context, "time");
        String command = StringArgumentType.getString(context, "command");
        IcSleepConfig config = IcSleepConfig.get();

        if (command == null || command.isBlank()) {
            source.sendFailure(Component.literal("run 后命令为空。"));
            return -1;
        }
        long ticks;
        try {
            ticks = TimeParser.parse(timeArg, config.defaultUnit, config.maxDelayTicks).ticks();
        } catch (TimeParseException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return -1;
        }
        int id = IcSleepMod.getScheduler().scheduleRun(source, command, ticks);
        if (id < 0) {
            source.sendFailure(Component.literal("任务创建失败，请检查队列是否已满。"));
            return -1;
        }
        if (config.showTaskIdInFeedback) {
            source.sendSuccess(() -> Component.literal(
                    "已安排任务 ID: " + id + "，将在 " + ticks + " tick 后执行 /" + command + "。"), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "已安排任务，将在 " + ticks + " tick 后执行 /" + command + "。"), false);
        }
        return config.returnTaskId ? id : 1;
    }

    /** 模式 B 检测：判断函数条目是否为 /sleep <time>（无 run） */
    public static boolean isModeB(String alias, String input) {
        if (input == null || alias == null) {
            return false;
        }
        String s = input.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (!s.startsWith(alias)) {
            return false;
        }
        String rest = s.substring(alias.length()).trim();
        if (rest.isEmpty()) {
            return false;
        }
        int space = rest.indexOf(' ');
        String timePart = space >= 0 ? rest.substring(0, space) : rest;
        try {
            IcSleepConfig config = IcSleepConfig.get();
            TimeParser.parse(timePart, config.defaultUnit, config.maxDelayTicks);
        } catch (TimeParseException e) {
            return false;
        }
        // 存在空格说明带了 run 等后续参数，属于模式 A
        return space < 0;
    }

    /** 管理命令：list [page] */
    private static int executeList(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        List<SleepTask> tasks = IcSleepMod.getScheduler().getPendingTasks();
        int pageSize = 10;
        int total = tasks.size();
        int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
        int current = Math.min(page, maxPage);
        int from = (current - 1) * pageSize;
        int to = Math.min(total, from + pageSize);

        source.sendSuccess(() -> Component.literal("待执行任务（第 " + current + "/" + maxPage + " 页，共 " + total + " 个）："), false);
        for (int i = from; i < to; i++) {
            SleepTask task = tasks.get(i);
            int idx = i;
            source.sendSuccess(() -> Component.literal(
                    "  #" + task.id + "  剩余 " + task.remainingTicks + "t  来源 " + task.sourceType
                            + "  " + commandSummary(task)), false);
        }
        return total;
    }

    private static String commandSummary(SleepTask task) {
        if (task.command != null) {
            return task.command.length() > 40 ? task.command.substring(0, 40) + "…" : task.command;
        }
        return "(函数恢复)";
    }

    /** 管理命令：info <id> */
    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        SleepTask task = IcSleepMod.getScheduler().getTask(id);
        if (task == null) {
            source.sendFailure(Component.literal("未找到任务 " + id + "。"));
            return -1;
        }
        int taskId = task.id;
        source.sendSuccess(() -> Component.literal("任务 " + taskId + " 详情："), false);
        source.sendSuccess(() -> Component.literal("  ID: " + task.id), false);
        source.sendSuccess(() -> Component.literal("  类型: " + task.type), false);
        source.sendSuccess(() -> Component.literal("  剩余: " + task.remainingTicks + " tick"), false);
        source.sendSuccess(() -> Component.literal("  来源: " + task.sourceType + " (" + task.sourceKey + ")"), false);
        if (task.command != null) {
            source.sendSuccess(() -> Component.literal("  命令: " + task.command), false);
        }
        return 1;
    }

    /** 管理命令：clear / clear all */
    private static int executeClearAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = IcSleepMod.getScheduler().cancelAll();
        source.sendSuccess(() -> Component.literal("已清除 " + count + " 个任务。"), false);
        return count;
    }

    /** 管理命令：clear <id> */
    private static int executeClearId(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        if (IcSleepMod.getScheduler().cancelTask(id)) {
            source.sendSuccess(() -> Component.literal("已清除任务 " + id + "。"), false);
            return 1;
        }
        source.sendFailure(Component.literal("未找到任务 " + id + "。"));
        return -1;
    }

    /** 管理命令：clear last */
    private static int executeClearLast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("未找到你最近创建的任务。"));
            return -1;
        }
        if (IcSleepMod.getScheduler().cancelLast(player.getUUID().toString())) {
            source.sendSuccess(() -> Component.literal("已清除你最近创建的任务。"), false);
            return 1;
        }
        source.sendFailure(Component.literal("未找到你最近创建的任务。"));
        return -1;
    }

    /** 管理命令：clear score <target> <objective> */
    private static int executeClearScore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ScoreHolder holder = ScoreHolderArgument.getName(context, "target");
            Objective objective = ObjectiveArgument.getObjective(context, "objective");
            var info = source.getServer().getScoreboard().getPlayerScoreInfo(holder, objective);
            if (info == null) {
                source.sendFailure(Component.literal("未找到记分板分数。"));
                return -1;
            }
            int id = info.value();
            if (IcSleepMod.getScheduler().cancelTask(id)) {
                source.sendSuccess(() -> Component.literal("已清除任务 " + id + "。"), false);
                return 1;
            }
            source.sendFailure(Component.literal("未找到任务 " + id + "。"));
            return -1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return -1;
        }
    }

    /** 管理命令：clear storage <namespace:path> <tag> */
    private static int executeClearStorage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            Identifier storageId = IdentifierArgument.getId(context, "storageId");
            NbtPathArgument.NbtPath path = NbtPathArgument.getPath(context, "path");
            MinecraftServer server = source.getServer();
            CompoundTag tag = server.getCommandStorage().get(storageId);
            List<net.minecraft.nbt.Tag> elements = path.get(tag);
            if (elements.isEmpty() || !(elements.get(0) instanceof IntTag intTag)) {
                source.sendFailure(Component.literal("未找到存储值或值不是整数。"));
                return -1;
            }
            int id = intTag.intValue();
            if (IcSleepMod.getScheduler().cancelTask(id)) {
                source.sendSuccess(() -> Component.literal("已清除任务 " + id + "。"), false);
                return 1;
            }
            source.sendFailure(Component.literal("未找到任务 " + id + "。"));
            return -1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return -1;
        }
    }
}
