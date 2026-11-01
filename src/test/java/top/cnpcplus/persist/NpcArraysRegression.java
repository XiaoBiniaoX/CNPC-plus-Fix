package top.cnpcplus.persist;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.entity.data.DataAdvanced;
import top.cnpcplus.lines.ArrayLine;
import top.cnpcplus.lines.DialogArrays;
import top.cnpcplus.lines.LinesData;
import top.cnpcplus.lines.NpcSoundArrays;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;

/** Exercises production data/playback helpers with a test-only mapped CNPC jar, not GUI/Mixin loading. */
public final class NpcArraysRegression {
    public static void main(String[] args) throws Exception {
        CompoundTag legacy = new CompoundTag(), entry = new CompoundTag();
        entry.putInt("Slot", 33);
        entry.putString("Line", "旧台词");
        entry.putString("Song", "minecraft:entity.player.hurt");
        ListTag list = new ListTag();
        list.add(entry);
        legacy.put("Lines", list);
        HashMap<Integer, Line> lines = LinesData.read(legacy);
        ArrayLine line = (ArrayLine) lines.get(33);
        check(line.getShowText() && line.sounds.sounds.size() == 1, "legacy Song/ShowText migration");
        StyledText styled = new StyledText("甲乙中文😀末尾", "").apply(2, 6, 0x12ABCD, 7, false);
        line.styled(styled);
        line.sounds.sounds.addAll(List.of("", "INVALID SOUND", "minecraft:entity.player.death"));
        line.setShowText(false);
        lines.put(8, line.copy());
        lines.put(Integer.MAX_VALUE, line.copy());
        lines.put(-1, line.copy());
        lines.put(90, null);
        List<Integer> slots = LinesData.slots(lines);
        check(slots.equals(List.of(8, 33, Integer.MAX_VALUE)), "sparse high slots");
        int previous = -1;
        for (int i = 0; i < 600; i++) {
            previous = LinesData.nextSlot(slots, previous);
            check(previous == slots.get(i % 3), "ordered cycle above slot 7");
        }
        HashMap<Integer, Line> restored = LinesData.read(LinesData.save(lines));
        check(((ArrayLine) restored.get(33)).sounds.sounds.equals(line.sounds.sounds)
                && !restored.get(33).getShowText(), "array NBT round trip");
        check(((ArrayLine)restored.get(33)).styled().equals(styled), "Spans NBT round trip");
        check(LinesData.save(lines).getList("Lines", 10).getCompound(1).getString("Line").equals(styled.text()), "style codes polluted Line NBT");
        ArrayLine copy = line.copy();
        copy.sounds.sounds.clear();
        copy.styled(styled.apply(0, styled.text().length(), 0xFF0000, 0, true));
        check(line.sounds.sounds.size() == 4, "Line.copy aliases sounds");
        check(line.styled().equals(styled), "Line.copy aliases styles");
        String first = line.getSound();
        for (int i = 0; i < 400; i++) {
            Line playback = line.forPlayback();
            check(playback.getClass() == Line.class && !playback.getShowText(), "playback must be a private stable snapshot");
            check(TextStyles.get(playback, TextStyles.LINE, playback.getText()).equals(styled), "playback styles missing");
            playback.setText("前" + playback.getText()); // Native ServerChatEvent may rewrite the private snapshot.
            check(TextStyles.get(playback, TextStyles.LINE, playback.getText()).equals(styled.edit(playback.getText())), "event rewrite style offsets drift");
            check(line.styled().equals(styled), "event rewrite modified source");
            check(List.of(first, "minecraft:entity.player.death").contains(playback.getSound()), "invalid sound selected");
            String chosen = playback.getSound();
            for (int listener = 0; listener < 5; listener++) check(playback.getSound().equals(chosen), "listener reselected sound");
            check(line.getSound().equals(first), "getter randomized/modified source");
        }
        entry.put("Sounds", new ListTag());
        check(((ArrayLine) LinesData.read(legacy).get(33)).sounds.sounds.isEmpty(), "explicit empty array resurrected Song");
        check(LinesData.slots(LinesData.read(new CompoundTag())).isEmpty(), "empty list");
        entry.putString("Line", "§c旧§l台词§r尾");
        ArrayLine oldStyled = (ArrayLine)LinesData.read(legacy).get(33);
        check(oldStyled.getText().equals("旧台词尾") && !oldStyled.styled().spans().isEmpty(), "old line colors not migrated");

        DataAdvanced advanced = new DataAdvanced(null);
        for (int i = 0; i < 5; i++) advanced.setSound(i, "minecraft:test_" + i);
        NpcSoundArrays.read(advanced, new CompoundTag());
        NpcSoundArrays sounds = NpcSoundArrays.get(advanced);
        for (int i = 0; i < 5; i++) {
            check(sounds.slots[i].first().equals("minecraft:test_" + i), "legacy event sound");
            check(sounds.slots[i].randomPitch == (i != 4), "legacy DisablePitch/footstep migration");
            sounds.slots[i].randomPitch = i % 2 == 0;
            sounds.slots[i].sounds.add("minecraft:second_" + i);
        }
        for (int i = 0; i < 400; i++) for (int slot = 0; slot < 5; slot++) {
            float pitch = sounds.slots[slot].pitch();
            check(slot % 2 == 0 ? pitch >= 0.8F && pitch <= 1.2F : pitch == 1F, "independent event pitch");
        }
        CompoundTag soundTag = new CompoundTag();
        sounds.save(soundTag);
        DataAdvanced second = new DataAdvanced(null);
        second.disablePitch = true;
        NpcSoundArrays.read(second, soundTag);
        NpcSoundArrays secondSounds = NpcSoundArrays.get(second);
        for (int i = 0; i < 5; i++) check(secondSounds.slots[i] != sounds.slots[i]
                && secondSounds.slots[i].sounds.equals(sounds.slots[i].sounds)
                && secondSounds.slots[i].randomPitch == sounds.slots[i].randomPitch, "event arrays reload/copy");
        secondSounds.slots[0].sounds.clear();
        check(sounds.slots[0].sounds.size() == 2, "event arrays shared across NPCs");
        NpcSoundArrays.read(second, new CompoundTag());
        for (int i = 0; i < 5; i++) check(!NpcSoundArrays.get(second).slots[i].randomPitch, "disabled legacy pitch");

        HashMap<Integer, DialogOption> dialogs = new HashMap<>();
        for (int slot : List.of(44, 3, 128, 12)) {
            DialogOption option = new DialogOption();
            option.title = "dialog-" + slot;
            option.dialogId = slot + 100;
            option.slot = slot;
            dialogs.put(slot, option);
        }
        List<DialogOption> ordered = DialogArrays.ordered(DialogArrays.read(DialogArrays.save(dialogs)));
        check(ordered.stream().map(option -> option.dialogId).toList().equals(List.of(103, 112, 144, 228)), "dialog priority/legacy NBT");
        DialogOption dialogCopy = DialogArrays.copy(ordered.get(0));
        dialogCopy.title = "changed";
        check(ordered.get(0).title.equals("dialog-3"), "dialog copy shared");

        CompoundTag full = soundTag.copy();
        for (String key : List.of("NpcLines", "NpcKilledLines", "NpcInteractLines", "NpcAttackLines",
                "NpcKillLines", "NpcInteractNPCLines", "CNPCPlusMeleeLines")) full.put(key, LinesData.save(lines));
        full.put("NPCDialogOptions", DialogArrays.save(dialogs));
        File root = new File(args[0], UUID.randomUUID().toString());
        UUID owner = UUID.randomUUID();
        Object identity = new Object();
        CompoundTag saved = full.copy();
        NpcDataExternalStore store = NpcDataExternalStore.ADVANCED;
        store.saveNpc(identity, owner, saved, root);
        for (String key : full.getAllKeys()) check(!saved.contains(key), "advanced key not externalized: " + key);
        CompoundTag clone = saved.copy();
        check(store.makePortable(clone, root) && clone.equals(full), "clone/soulstone snapshot incomplete");
        clone.getCompound("CNPCPlusMeleeLines").getList("Lines", 10).getCompound(0).putString("Line", "clone edit");
        UUID cloneOwner = UUID.randomUUID();
        store.saveNpc(new Object(), cloneOwner, clone, root);
        check(!clone.getString("CNPCPlusAdvancedId").equals(saved.getString("CNPCPlusAdvancedId")), "clone external ID reused");
        check(store.makePortable(saved, root) && saved.equals(full), "clone overwrote original arrays");
        System.out.println("NPC_ARRAYS_REGRESSION=PASS (helpers/NBT/IO only; GUI/Mixin lifecycle remains separate)");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
