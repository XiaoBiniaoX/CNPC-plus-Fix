package top.cnpcplus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.client.gui.roles.GuiNpcTransporter;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.components.GuiWrapper;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.ShapePreset;
import top.cnpcplus.drop.fx.runtime.DropFxInstance;
import top.cnpcplus.ranged.RangedFxStore;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportOrder;
import top.cnpcplus.transport.TransportPanelStyle;
import top.cnpcplus.transport.TransportSpans;
import top.cnpcplus.transport.client.GuiTransporterVisualEditor;
import top.cnpcplus.transport.client.TransportClientCache;

/** Actual production logic with headless GUI/entity shells; not a game or mixin smoke test. */
public final class Hotfix4Regression {
    private static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        spans();
        names();
        order();
        follow();
        System.out.println("HOTFIX4_REGRESSION=PASS");
    }

    private static void spans() {
        String raw = "0,10,16711680,1;2,8,-1,2";
        raw = TransportSpans.apply(raw, 3, 7, -1, 1, true);
        check(TransportSpans.flagsAt(TransportSpans.parse(raw), 4) == 2, "bold did not toggle off");
        check(TransportSpans.flagsAt(TransportSpans.parse(raw), 2) == 3, "toggle changed outside range");
        raw = TransportSpans.apply(raw, 4, 6, TransportSpans.CLEAR_COLOR, 0, false);
        check(TransportSpans.colorAt(TransportSpans.parse(raw), 4, -1) == -1, "clear color failed");
        check(TransportSpans.flagsAt(TransportSpans.parse(raw), 4) == 2, "clear color removed underline");
        raw = TransportSpans.apply(raw, 0, 3, -1, 0, true);
        check(TransportSpans.flagsAt(TransportSpans.parse(raw), 2) == 0, "reset kept flags");
        check(TransportSpans.colorAt(TransportSpans.parse(raw), 2, -1) == -1, "reset kept color");
        check(raw.equals(TransportSpans.apply(raw, 5, 5, 123, 0, false)), "empty selection modified text");

        Random r = new Random(241);
        int[] colors = new int[24], flags = new int[24];
        Arrays.fill(colors, -1);
        raw = "";
        for (int n = 0; n < 1000; n++) {
            int start = r.nextInt(24), end = start + 1 + r.nextInt(24 - start), op = r.nextInt(4);
            int color = op == 0 ? r.nextInt(0x1000000) : op == 1 ? TransportSpans.CLEAR_COLOR : -1;
            int flag = op == 2 ? 1 << r.nextInt(3) : 0;
            boolean toggle = op >= 2;
            boolean remove = flag != 0;
            for (int i = start; i < end; i++) remove &= (flags[i] & flag) == flag;
            raw = TransportSpans.apply(raw, start, end, color, flag, toggle);
            for (int i = start; i < end; i++) {
                if (op == 0) colors[i] = color;
                else if (op == 1) colors[i] = -1;
                else if (op == 2) flags[i] = remove ? flags[i] & ~flag : flags[i] | flag;
                else { colors[i] = -1; flags[i] = 0; }
            }
            List<TransportSpans.Span> parsed = TransportSpans.parse(raw);
            for (int i = 0; i < 24; i++) {
                check(TransportSpans.colorAt(parsed, i, -1) == colors[i], "color oracle mismatch");
                check(TransportSpans.flagsAt(parsed, i) == flags[i], "flags oracle mismatch");
            }
            for (int i = 1; i < parsed.size(); i++) check(parsed.get(i - 1).end <= parsed.get(i).start, "overlap remained");
        }
        System.out.println("PASS: color, clear, reset, toggle, outside selection and 1000 mixed operations");
    }

    private static void names() throws Exception {
        TransportClientCache.beginEditor();
        CompoundTag root = new CompoundTag(), entries = new CompoundTag();
        entries.put("7", meta(7, "default name"));
        entries.put("8", meta(8, "default name"));
        root.put("Entries", entries);
        TransportClientCache.acceptMeta(root, true);
        check(TransportClientCache.allEditMeta().size() == 2, "duplicate names collapsed");
        GuiTransporterVisualEditor editor = shell(GuiTransporterVisualEditor.class);
        GuiNpcTransporter parent = shell(GuiNpcTransporter.class);
        parent.wrapper = new GuiWrapper(parent);
        parent.location = shell(TransportLocation.class);
        parent.location.id = 7;
        parent.location.name = "default name";
        NameField name = shell(NameField.class);
        name.value = "default name";
        parent.wrapper.textfields.put(0, name);
        editor.wrapper = new GuiWrapper(editor);
        editor.wrapper.parent = parent;
        set(editor, "ownKey", "7");
        set(editor, "selectedName", "7");
        set(editor, "entryLocal", new HashMap<>());
        set(editor, "panelLocal", new CompoundTag());
        for (String text : List.of("T", "Te", "Teleport", "传送点")) {
            call(editor, "applyName", new Class<?>[]{String.class}, text);
            check(text.equals(parent.location.name) && text.equals(name.value), "continuous rename lost parent linkage");
        }
        call(editor, "markEntryDirty", new Class<?>[]{String.class}, "7");
        TransportClientCache.acceptMeta(root.copy(), true); // old response replaces metadata objects
        call(editor, "applyLocal", new Class<?>[]{});
        check("传送点".equals(TransportClientCache.editMeta("7").getString(TransportKeys.NAME)), "renamed entry lost local overlay");
        parent.location = shell(TransportLocation.class); // native async replacement
        parent.location.id = 7;
        parent.location.name = "default name";
        call(editor, "applyName", new Class<?>[]{String.class}, "完整新名字");
        check("完整新名字".equals(parent.location.name) && "完整新名字".equals(name.value), "replacement object not synchronized");
        set(editor, "activeElement", 0);
        check("完整新名字".equals(call(editor, "currentContent", new Class<?>[]{})), "own suffix entered editable name");
        set(editor, "selectedName", "8");
        call(editor, "applyName", new Class<?>[]{String.class}, "Other");
        check("完整新名字".equals(parent.location.name), "other row overwrote own name");
        System.out.println("PASS: multi-character names, duplicate names, old response, parent replacement and pure edit text");
    }

    private static void order() {
        Map<String, CompoundTag> meta = new HashMap<>();
        meta.put("1", meta(1, "beta")); meta.put("2", meta(2, "Alpha")); meta.put("3", meta(3, "Zulu"));
        CompoundTag panel = new CompoundTag();
        List<String> keys = new ArrayList<>(meta.keySet());
        keys.sort(TransportOrder.comparator(panel, meta::get, k -> meta.get(k).getString("Name")));
        check(keys.equals(List.of("2", "1", "3")), "default A-Z order failed");
        panel.putIntArray(TransportOrder.KEY, new int[]{3, 1, 2});
        CompoundTag saved = new CompoundTag();
        TransportPanelStyle.merge(saved, panel);
        meta.get("3").putString("Name", "AAA renamed");
        keys.sort(TransportOrder.comparator(saved.copy(), meta::get, k -> meta.get(k).getString("Name")));
        check(keys.equals(List.of("3", "1", "2")), "rename changed custom ID order");
        keys.remove("1");
        keys.sort(TransportOrder.comparator(saved, meta::get, k -> meta.get(k).getString("Name")));
        check(keys.equals(List.of("3", "2")), "player subset lost shared order");
        TransportPanelStyle.merge(saved, new CompoundTag());
        check(Arrays.equals(saved.getIntArray(TransportOrder.KEY), new int[]{3, 1, 2}), "unrelated panel save erased order");
        System.out.println("PASS: A-Z default, persistent ID order, rename and player subset");
    }

    private static void follow() throws Exception {
        DataRanged ranged = shell(DataRanged.class), restored = shell(DataRanged.class);
        DropFxConfig fx = new DropFxConfig("minecraft:flame");
        ShapePreset shape = new ShapePreset();
        shape.durationTicks = 20;
        fx.shapes.add(shape);
        RangedFxStore.put(ranged, List.of(fx));
        RangedFxStore.setFollows(ranged, true);
        CompoundTag nbt = new CompoundTag();
        RangedFxStore.write(ranged, nbt);
        check(nbt.contains(RangedFxStore.NBT_KEY, Tag.TAG_LIST), "legacy list format changed");
        RangedFxStore.read(restored, nbt);
        check(RangedFxStore.follows(restored) && RangedFxStore.size(restored) == 1, "follow round trip failed");
        nbt.remove(RangedFxStore.FOLLOW_KEY);
        RangedFxStore.read(restored, nbt);
        check(!RangedFxStore.follows(restored) && RangedFxStore.size(restored) == 1, "old data default/FX lost");
        RangedFxStore.setFollows(restored, true);
        RangedFxStore.clear(restored);
        RangedFxStore.write(restored, nbt);
        check(!nbt.contains(RangedFxStore.NBT_KEY) && !nbt.contains(RangedFxStore.FOLLOW_KEY), "clear did not persist");
        // Vanilla registries are needed by Entity's static serializers. Plain JavaExec
        // lacks Forge's event-class transform; skip only that known final network hook.
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: skipped untransformed Forge network hook; not a loader smoke test");
        }
        Anchor entity = shell(Anchor.class);
        entity.pos = new Vec3(1, 64, 2);
        DropFxInstance following = new DropFxInstance(1, 64, 2, List.of(fx), 10, entity);
        DropFxInstance fixed = new DropFxInstance(1, 64, 2, List.of(fx), 10);
        entity.pos = new Vec3(9, 70, -3);
        check(following.center().equals(entity.pos), "live anchor did not move");
        check(fixed.center().equals(new Vec3(1, 64, 2)), "disabled behavior moved fixed anchor");
        check(following.shapeAge(41, shape) == 1 && fixed.shapeAge(41, shape) == 41, "follow cycle/legacy duration changed");
        shape.radius = 77;
        check(following.fxs.get(0).shapes.get(0).radius == 1, "launch snapshot not independent");
        System.out.println("PASS: follow NBT, old list/default, clear, live XYZ, fixed anchor, loop and snapshot");
    }

    private static CompoundTag meta(int id, String name) {
        CompoundTag t = new CompoundTag(); t.putInt("Id", id); t.putString("Name", name); return t;
    }
    @SuppressWarnings("unchecked")
    private static <T> T shell(Class<T> type) throws InstantiationException { return (T) UNSAFE.allocateInstance(type); }
    private static void set(Object object, String field, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(field); f.setAccessible(true); f.set(object, value);
    }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(object, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class NameField extends GuiTextFieldNop {
        String value;
        private NameField() { super(0, null, 0, 0, 1, 1, ""); }
        @Override public String getValue() { return value; }
        @Override public void setValue(String value) { this.value = value; }
    }
    private static final class Anchor extends Entity {
        Vec3 pos;
        private Anchor() { super(null, null); }
        @Override public Vec3 position() { return pos; }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag t) {}
        @Override protected void addAdditionalSaveData(CompoundTag t) {}
    }
}
