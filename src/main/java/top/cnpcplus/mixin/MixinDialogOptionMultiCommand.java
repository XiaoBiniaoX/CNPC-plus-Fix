package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.NoppesUtilServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Pattern;

/**
 * 对话选项（命令方块类型）支持一次执行多条指令：
 *   /say 你好 + /say 看得到吗
 * 分隔符：加号（半角 + / 全角 ＋）两侧各带至少一个空白（半角空格或全角空格），或换行。
 *
 * 注入 4 参重载 —— 这是 SPacketDialogSelected 执行对话选项命令时走的方法。
 */
@Mixin(value = NoppesUtilServer.class, remap = false)
public class MixinDialogOptionMultiCommand {

    private static final Pattern SPLIT =
            Pattern.compile("[\\s\\u3000]+[+\\uFF0B][\\s\\u3000]+|[\\r\\n]+");

    @Inject(
            method = "runCommand(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cnpcplus$splitDialogCommands(Entity executer, String name, String command,
                                                     Player player,
                                                     CallbackInfoReturnable<String> cir) {
        if (command == null) return;
        String normalized = command.replace('\u3000', ' ');
        String[] parts = SPLIT.split(normalized);
        if (parts.length < 2) return;

        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            String single = part.trim();
            if (single.isEmpty()) continue;
            String result = NoppesUtilServer.runCommand(executer, name, single, player);
            if (result == null || result.isEmpty()) continue;
            if (output.length() > 0) output.append('\n');
            output.append(result);
        }
        cir.setReturnValue(output.length() == 0 ? null : output.toString());
    }
}
