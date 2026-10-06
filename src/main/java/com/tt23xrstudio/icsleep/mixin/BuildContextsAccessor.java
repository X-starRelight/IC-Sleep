package com.tt23xrstudio.icsleep.mixin;

import net.minecraft.commands.execution.tasks.BuildContexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 BuildContexts 的私有命令字符串字段，用于识别函数中的 /sleep 条目。
 */
@Mixin(BuildContexts.class)
public interface BuildContextsAccessor {

    @Accessor("commandInput")
    String icsleep$getCommandInput();
}
