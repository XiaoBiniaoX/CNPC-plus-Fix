package top.cnpcplus.mixin;

import noppes.npcs.constants.EnumScriptType;
import org.spongepowered.asm.mixin.Mixin;

/** Sites for the enum extension in the config plugin. */
@Mixin(value = EnumScriptType.class, remap = false)
public abstract class MixinScriptTypes {}
