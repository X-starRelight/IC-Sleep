package com.tt23xrstudio.icsleep.gametest;

import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import com.tt23xrstudio.icsleep.scheduler.SleepScheduler;
import com.tt23xrstudio.icsleep.scheduler.SleepTask;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.block.Blocks;

/**
 * ICSl 服务端 GameTest。
 *
 * 覆盖：run 模式延迟执行、时间解析错误、任务 ID、list/clear 管理命令。
 *
 * 注意：所有测试共享全局调度器。GameTest 批次中所有测试的 setup 代码
 * 在同一个 tick 上按顺序执行，因此不能在 setup 中调用 cancelAll()，
 * 否则会误删其他测试刚创建的任务。每个 gradlew runGameTest 启动新服务器，
 * 调度器天然为空，无需手动清空。
 */
public class SleepCommandGameTest {

    private static String alias() {
        return IcSleepConfig.get().commandAlias;
    }

    /**
     * 拼出经 IC-Root 统一入口执行的完整命令：
     * {@code ic run sleep <alias> <args>}（name = sleep，command 根字面量 = alias，默认 sleep）。
     */
    private static String ic(String args) {
        return "ic run " + IcSleepMod.SLEEP_IC_NAME + " " + alias() + " " + args;
    }

    private static net.minecraft.commands.CommandSourceStack testSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        return server.createCommandSourceStack()
                .withLevel(level)
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))))
                .withPermission(LevelBasedPermissionSet.GAMEMASTER);
    }

    /** run 模式：延迟 1 tick 后应放置方块 */
    @GameTest
    public void testRunModeExecutes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        String command = "setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:stone";

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    server.getCommands().performPrefixedCommand(testSource(helper), ic("1 run " + command));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    if (!helper.getLevel().getBlockState(target).is(Blocks.STONE)) {
                        helper.fail("run 模式未在延迟后执行 setblock");
                    }
                })
                .thenSucceed();
    }

    /** 无效时间不应创建新任务（检查 size 未增加，而非 == 0） */
    @GameTest
    public void testInvalidTimeRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int before = IcSleepMod.getScheduler().size();
        server.getCommands().performPrefixedCommand(testSource(helper), ic("abc run say hi"));
        if (IcSleepMod.getScheduler().size() != before) {
            helper.fail("无效时间不应创建任务，但队列增加了 " + (IcSleepMod.getScheduler().size() - before) + " 个");
        }
        helper.succeed();
    }

    /** 任务 ID 应从 2 开始且不为 0/1 */
    @GameTest
    public void testTaskIdStartsAtTwo(GameTestHelper helper) {
        SleepScheduler scheduler = IcSleepMod.getScheduler();
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(testSource(helper), ic("100 run say a"));
        server.getCommands().performPrefixedCommand(testSource(helper), ic("100 run say b"));

        var tasks = scheduler.getPendingTasks();
        if (tasks.size() < 2) {
            helper.fail("应创建至少 2 个任务，实际 " + tasks.size());
        }
        for (SleepTask task : tasks) {
            if (task.id < 2) {
                helper.fail("任务 ID 不应小于 2，实际 " + task.id);
            }
        }

        helper.succeed();
    }

    /** clear 应清除全部任务 */
    @GameTest
    public void testClearAll(GameTestHelper helper) {
        SleepScheduler scheduler = IcSleepMod.getScheduler();
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(testSource(helper), ic("100 run say a"));
        if (scheduler.size() == 0) {
            helper.fail("任务未创建");
        }
        server.getCommands().performPrefixedCommand(testSource(helper), ic("clear all"));
        if (scheduler.size() != 0) {
            helper.fail("clear all 后队列应为空，实际 " + scheduler.size());
        }
        helper.succeed();
    }

    /** clear <id> 应只清除指定任务 */
    @GameTest
    public void testClearById(GameTestHelper helper) {
        SleepScheduler scheduler = IcSleepMod.getScheduler();
        MinecraftServer server = helper.getLevel().getServer();
        int before = scheduler.size();
        server.getCommands().performPrefixedCommand(testSource(helper), ic("100 run say a"));
        server.getCommands().performPrefixedCommand(testSource(helper), ic("100 run say b"));
        var tasks = scheduler.getPendingTasks();
        int created = tasks.size();
        if (created < 2) {
            helper.fail("应创建至少 2 个任务，实际 " + created);
        }
        int id = tasks.get(tasks.size() - 1).id;
        server.getCommands().performPrefixedCommand(testSource(helper), ic("clear " + id));
        if (scheduler.getTask(id) != null) {
            helper.fail("clear <id> 未正确清除任务 " + id);
        }
        helper.succeed();
    }
}
