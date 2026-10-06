package com.tt23xrstudio.icsleep.scheduler;

import com.tt23xrstudio.icsleep.IcSleepMod;
import com.tt23xrstudio.icsleep.config.IcSleepConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 执行上下文捕获与重建工具。
 *
 * 负责在任务创建时快照来源信息，并在延迟执行时尽量还原原始上下文。
 */
public final class TaskContextUtil {

    private TaskContextUtil() {
    }

    /**
     * 捕获来源信息，用于持久化与来源判定。
     */
    public static SourceInfo sourceInfo(CommandSourceStack source, TaskContext context) {
        if (source == null) {
            return new SourceInfo(SourceType.SERVER, "server", null, "log");
        }
        Entity entity = source.getEntity();
        // 玩家来源
        if (entity instanceof ServerPlayer player) {
            String uuid = player.getUUID().toString();
            return new SourceInfo(SourceType.PLAYER, "player:" + uuid, uuid, "player");
        }
        // 命令方块来源：通过方块实体判定
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        BlockPos bp = BlockPos.containing(pos);
        if (level != null && level.getBlockEntity(bp) instanceof CommandBlockEntity) {
            String key = "block:" + dimensionId(level) + ":" + bp.getX() + "," + bp.getY() + "," + bp.getZ();
            return new SourceInfo(SourceType.COMMAND_BLOCK, key, null, "log");
        }
        // 函数来源：无实体但执行者为函数上下文（通过 source 名称粗略判定）
        if (entity != null) {
            // 其他实体（如命令方块矿车）
            String uuid = entity.getUUID().toString();
            return new SourceInfo(SourceType.FUNCTION, "entity:" + uuid, uuid, "log");
        }
        return new SourceInfo(SourceType.SERVER, "server", null, "log");
    }

    /**
     * 快照执行上下文。
     */
    public static TaskContext capture(CommandSourceStack source) {
        TaskContext context = new TaskContext();
        if (source == null) {
            return context;
        }
        ServerLevel level = source.getLevel();
        if (level != null) {
            context.dimension = dimensionId(level);
        }
        Vec3 pos = source.getPosition();
        if (pos != null) {
            context.x = pos.x;
            context.y = pos.y;
            context.z = pos.z;
        }
        Vec2 rot = source.getRotation();
        if (rot != null) {
            context.yaw = rot.y;
            context.pitch = rot.x;
        }
        Entity entity = source.getEntity();
        if (entity != null) {
            context.entityUuid = entity.getUUID().toString();
        }
        // 命令方块坐标
        if (level != null && pos != null) {
            BlockPos bp = BlockPos.containing(pos);
            if (level.getBlockEntity(bp) instanceof CommandBlockEntity) {
                context.blockX = bp.getX();
                context.blockY = bp.getY();
                context.blockZ = bp.getZ();
            }
        }
        return context;
    }

    /**
     * 根据任务快照重建执行上下文。来源失效时返回 null。
     */
    public static CommandSourceStack rebuild(MinecraftServer server, SleepTask task) {
        if (server == null) {
            return null;
        }
        TaskContext ctx = task.context;
        SourceType type = SourceType.fromId(task.sourceType);
        ServerLevel level = resolveLevel(server, ctx.dimension);
        switch (type) {
            case PLAYER -> {
                ServerPlayer player = resolvePlayer(server, ctx);
                if (player == null) {
                    return null;
                }
                // 使用玩家当前上下文（位置、维度、权限、朝向）
                return player.createCommandSourceStack();
            }
            case COMMAND_BLOCK -> {
                if (level == null || !ctx.hasBlockPos()) {
                    return null;
                }
                BlockPos bp = new BlockPos(ctx.blockX, ctx.blockY, ctx.blockZ);
                if (!(level.getBlockEntity(bp) instanceof CommandBlockEntity)) {
                    return null;
                }
                CommandSourceStack base = server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(Vec3.atCenterOf(bp))
                        .withPermission(LevelBasedPermissionSet.GAMEMASTER);
                return base.withSource(new CommandBlockSource(level, bp));
            }
            case FUNCTION -> {
                if (level == null) {
                    return null;
                }
                Entity entity = resolveEntity(server, ctx);
                CommandSourceStack base = server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(new Vec3(ctx.x, ctx.y, ctx.z))
                        .withRotation(new Vec2(ctx.pitch, ctx.yaw))
                        .withPermission(LevelBasedPermissionSet.GAMEMASTER);
                if (entity != null) {
                    base = base.withEntity(entity);
                }
                return base;
            }
            default -> {
                if (level == null) {
                    return null;
                }
                return server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(new Vec3(ctx.x, ctx.y, ctx.z))
                        .withRotation(new Vec2(ctx.pitch, ctx.yaw));
            }
        }
    }

    /**
     * 判断任务来源是否已失效。
     */
    public static boolean isSourceInvalid(MinecraftServer server, SleepTask task, IcSleepConfig config) {
        if (server == null) {
            return true;
        }
        TaskContext ctx = task.context;
        SourceType type = SourceType.fromId(task.sourceType);
        if (type == SourceType.PLAYER) {
            return resolvePlayer(server, ctx) == null;
        }
        if (type == SourceType.COMMAND_BLOCK) {
            if ("keep".equalsIgnoreCase(config.commandBlockPolicy)) {
                return false;
            }
            ServerLevel level = resolveLevel(server, ctx.dimension);
            if (level == null || !ctx.hasBlockPos()) {
                return true;
            }
            BlockPos bp = new BlockPos(ctx.blockX, ctx.blockY, ctx.blockZ);
            return !(level.getBlockEntity(bp) instanceof CommandBlockEntity);
        }
        // 其他来源只检查维度
        return resolveLevel(server, ctx.dimension) == null;
    }

    private static ServerPlayer resolvePlayer(MinecraftServer server, TaskContext ctx) {
        java.util.UUID uuid = ctx.entityUuidOrNull();
        if (uuid == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(uuid);
    }

    private static Entity resolveEntity(MinecraftServer server, TaskContext ctx) {
        java.util.UUID uuid = ctx.entityUuidOrNull();
        if (uuid == null) {
            return null;
        }
        ServerLevel level = resolveLevel(server, ctx.dimension);
        if (level == null) {
            return null;
        }
        return level.getEntityInAnyDimension(uuid);
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return server.overworld();
        }
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /**
     * 来源信息快照。
     */
    public record SourceInfo(SourceType sourceType, String sourceKey, String creatorUuid, String feedbackTarget) {
    }

    /**
     * 简单的命令方块来源包装，用于重建 CommandSourceStack。
     */
    private static final class CommandBlockSource implements net.minecraft.commands.CommandSource {
        private final BlockPos pos;

        CommandBlockSource(ServerLevel level, BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void sendSystemMessage(net.minecraft.network.chat.Component message) {
            IcSleepMod.LOGGER.info("[命令方块 {}] {}", pos, message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
