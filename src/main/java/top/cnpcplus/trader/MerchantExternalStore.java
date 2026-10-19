package top.cnpcplus.trader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NPC-local RoleTrader product snapshots. TraderPager remains the only page
 * controller; this class never replaces its page lists or page index.
 */
public final class MerchantExternalStore {
    public static final String ID_KEY = "CNPCPlusTraderId";
    public static final String STATE_KEY = "CNPCPlusTraderMigration";
    private static final int COPYING = 1;
    private static final int VERIFIED = 2;
    private static final int EXTERNAL_ACTIVE = 3;
    private static final int CLEANUP_PENDING = 4;
    private static final int CLEANED = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcplus");
    private static final Map<RoleTrader, State> ACTIVE = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> MARKET_IO = ThreadLocal.withInitial(() -> false);

    private MerchantExternalStore() {}

    public static void beginMarketIO() { MARKET_IO.set(true); }
    public static void endMarketIO() { MARKET_IO.remove(); }
    public static boolean isMarketIO() { return MARKET_IO.get(); }

    private static final class State {
        UUID id;
        UUID owner;
        int pages;
        int migration;
        List<String> titles = new ArrayList<>();
        List<CompoundTag> pageData = new ArrayList<>();
    }

    /** Called from RoleTrader.load before native readNBT consumes the product keys. */
    public static synchronized void prepareLoad(RoleTrader role, CompoundTag tag) {
        if (role == null || role.npc == null || role.npc.level() == null || role.npc.level().isClientSide) return;
        UUID owner = role.npc.getUUID();
        UUID id = parseId(tag.getString(ID_KEY));
        if (id == null) id = owner;
        State state = readManifest(id);
        boolean legacy = hasLegacy(tag);

        try {
            if (state != null && !owner.equals(state.owner)) {
                UUID replacement = UUID.randomUUID();
                copyState(state, replacement, owner);
                id = replacement;
                state = readManifest(id);
            }

            if (state != null && state.migration >= VERIFIED && verifyPages(state)) {
                ACTIVE.put(role, state);
                attachPages(tag, state);
                tag.putString(ID_KEY, state.id.toString());
                tag.putInt(STATE_KEY, state.migration);
                return;
            }

            if (!legacy) {
                State fresh = new State();
                fresh.id = id;
                fresh.owner = owner;
                fresh.pages = 1;
                fresh.migration = CLEANED;
                fresh.titles.add("");
                fresh.pageData.add(emptyPage());
                writeManifest(fresh);
                writePages(fresh);
                ACTIVE.put(role, fresh);
                tag.putString(ID_KEY, id.toString());
                tag.putInt(STATE_KEY, CLEANED);
                return;
            }

            State migrated = new State();
            migrated.id = id;
            migrated.owner = owner;
            migrated.pageData = extractPages(tag);
            migrated.pages = migrated.pageData.size();
            migrated.titles = readTitles(tag, migrated.pages);
            migrated.migration = COPYING;
            ACTIVE.put(role, migrated);
            tag.putString(ID_KEY, id.toString());
            tag.putInt(STATE_KEY, COPYING);
            writeManifest(migrated);
            writePages(migrated);
            if (!verifyPages(migrated)) throw new IOException("legacy merchant page verification failed");
            migrated.migration = VERIFIED;
            writeManifest(migrated);
            attachPages(tag, migrated);
            tag.putInt(STATE_KEY, VERIFIED);
        } catch (Exception e) {
            ACTIVE.remove(role);
            LOGGER.error("[merchant] migration failed; old product NBT remains for {}", owner, e);
        }
    }

    /** Called from RoleTrader.save after native writeNBT/TraderPager serialization. */
    public static synchronized void saveNpc(EntityNPCInterface npc, CompoundTag entityNbt) {
        if (npc == null || !(npc.role instanceof RoleTrader role) || entityNbt == null) return;
        UUID owner = npc.getUUID();
        UUID id = parseId(entityNbt.getString(ID_KEY));
        if (id == null) id = owner;
        State state = ACTIVE.get(role);
        try {
            if (state == null || !owner.equals(state.owner) || !id.equals(state.id)) {
                state = readManifest(id);
                if (state == null || !owner.equals(state.owner)) {
                    state = new State();
                    state.id = id;
                    state.owner = owner;
                }
            }
            state.pageData = extractPages(entityNbt);
            state.pages = state.pageData.size();
            state.titles = readTitles(entityNbt, state.pages);
            state.migration = COPYING;
            ACTIVE.put(role, state);
            writeManifest(state);
            writePages(state);
            if (!verifyPages(state)) throw new IOException("merchant save verification failed");
            state.migration = EXTERNAL_ACTIVE;
            writeManifest(state);
            entityNbt.putString(ID_KEY, state.id.toString());
            entityNbt.putInt(STATE_KEY, CLEANUP_PENDING);
            removeLegacyPages(entityNbt);
            entityNbt.remove("TraderCurrency");
            entityNbt.remove("TraderSold");
            state.migration = CLEANED;
            entityNbt.putInt(STATE_KEY, CLEANED);
            writeManifest(state);
        } catch (Exception e) {
            LOGGER.error("[merchant] save failed; product keys retained for {}", owner, e);
        }
    }

    public static synchronized boolean isExternal(RoleTrader role) {
        return ACTIVE.containsKey(role);
    }

    public static synchronized void initializePages(RoleTrader role) {
        State state = ACTIVE.get(role);
        if (state == null || state.pageData.isEmpty()) return;
        TraderPager.initializeExternal(role, state.pageData, state.titles);
    }

    public static synchronized List<CompoundTag> loadPages(RoleTrader role) {
        State state = ACTIVE.get(role);
        if (state == null) return List.of();
        List<CompoundTag> result = new ArrayList<>();
        for (CompoundTag page : state.pageData) result.add(page.copy());
        return result;
    }

    public static synchronized List<String> loadTitles(RoleTrader role) {
        State state = ACTIVE.get(role);
        return state == null ? List.of("") : new ArrayList<>(state.titles);
    }

    public static synchronized CompoundTag toMarketPages(RoleTrader role) {
        return TraderPager.toNBT(role);
    }

    public static synchronized boolean applyNpcFallback(RoleTrader role) {
        State state = ACTIVE.get(role);
        if (state == null || state.pageData.isEmpty()) return false;
        try {
            CompoundTag tag = new CompoundTag();
            attachPages(tag, state);
            role.readNBT(tag);
            return true;
        } catch (Exception e) {
            LOGGER.error("[merchant] fallback load failed for {}", state.owner, e);
            return false;
        }
    }

    private static boolean verifyPages(State state) throws IOException {
        for (int i = 0; i < state.pages; i++) {
            if (!readPage(state.id, i).equals(state.pageData.get(i))) return false;
        }
        return true;
    }

    private static List<CompoundTag> extractPages(CompoundTag tag) {
        List<CompoundTag> pages = new ArrayList<>();
        CompoundTag current = new CompoundTag();
        current.put("Currency", tag.getCompound("TraderCurrency").copy());
        current.put("Sold", tag.getCompound("TraderSold").copy());
        ListTag list = tag.getList("TraderPages", Tag.TAG_COMPOUND);
        if (!tag.getBoolean("FullPages")) pages.add(current);
        for (Tag raw : list) {
            CompoundTag source = (CompoundTag) raw;
            CompoundTag page = new CompoundTag();
            page.put("Currency", source.contains("Tc") ? source.getCompound("Tc").copy() : source.getCompound("Currency").copy());
            page.put("Sold", source.contains("Ts") ? source.getCompound("Ts").copy() : source.getCompound("Sold").copy());
            pages.add(page);
        }
        if (pages.isEmpty()) pages.add(current);
        return pages;
    }

    private static List<String> readTitles(CompoundTag tag, int pages) {
        List<String> titles = new ArrayList<>();
        for (Tag raw : tag.getList("PageTitles", Tag.TAG_STRING)) titles.add(raw.getAsString());
        while (titles.size() < pages) titles.add("");
        return titles;
    }

    private static void attachPages(CompoundTag tag, State state) throws IOException {
        if (state.pageData.isEmpty()) return;
        CompoundTag first = state.pageData.get(0);
        tag.put("TraderCurrency", first.getCompound("Currency").copy());
        tag.put("TraderSold", first.getCompound("Sold").copy());
        ListTag pages = new ListTag();
        for (CompoundTag source : state.pageData) {
            CompoundTag page = new CompoundTag();
            page.put("Tc", source.getCompound("Currency").copy());
            page.put("Ts", source.getCompound("Sold").copy());
            pages.add(page);
        }
        tag.put("TraderPages", pages);
        tag.putBoolean("FullPages", true);
        ListTag titles = new ListTag();
        for (String title : state.titles) titles.add(net.minecraft.nbt.StringTag.valueOf(title));
        tag.put("PageTitles", titles);
    }

    private static void removeLegacyPages(CompoundTag tag) {
        tag.remove("TraderPages");
        tag.remove("PageTitles");
        tag.remove("FullPages");
    }

    private static boolean hasLegacy(CompoundTag tag) {
        return tag.contains("TraderCurrency") || tag.contains("TraderSold") || tag.contains("TraderPages");
    }

    private static CompoundTag emptyPage() {
        CompoundTag page = new CompoundTag();
        page.put("Currency", new CompoundTag());
        page.put("Sold", new CompoundTag());
        return page;
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private static File root(UUID id) { return new File(new File(noppes.npcs.CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/merchants"), id.toString()); }
    private static File manifest(UUID id) { return new File(root(id), "manifest.dat"); }
    private static File pageFile(UUID id, int page) { return new File(root(id), "page_" + page + ".dat"); }

    private static State readManifest(UUID id) {
        File file = manifest(id);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            State state = new State();
            state.id = id;
            state.owner = UUID.fromString(tag.getString("Owner"));
            state.pages = Math.max(1, tag.getInt("Pages"));
            state.migration = tag.getInt("MigrationState");
            state.titles = readTitles(tag, state.pages);
            for (int i = 0; i < state.pages; i++) state.pageData.add(readPage(id, i));
            return state;
        } catch (Exception e) {
            LOGGER.error("[merchant] manifest read failed for {}", id, e);
            return null;
        }
    }

    private static void writeManifest(State state) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString("Owner", state.owner.toString());
        tag.putInt("Pages", state.pages);
        tag.putInt("MigrationState", state.migration);
        ListTag titles = new ListTag();
        for (String title : state.titles) titles.add(net.minecraft.nbt.StringTag.valueOf(title));
        tag.put("PageTitles", titles);
        writeCompressed(manifest(state.id), tag);
    }

    private static void writePages(State state) throws IOException {
        for (int i = 0; i < state.pages; i++) writeCompressed(pageFile(state.id, i), state.pageData.get(i));
    }

    private static CompoundTag readPage(UUID id, int page) throws IOException {
        File file = pageFile(id, page);
        if (!file.isFile()) throw new IOException("missing merchant page " + file);
        try (FileInputStream input = new FileInputStream(file)) { return NbtIo.readCompressed(input); }
    }

    private static void copyState(State source, UUID id, UUID owner) throws IOException {
        State copy = new State();
        copy.id = id;
        copy.owner = owner;
        copy.pages = source.pages;
        copy.titles = new ArrayList<>(source.titles);
        copy.pageData = new ArrayList<>();
        for (CompoundTag page : source.pageData) copy.pageData.add(page.copy());
        copy.migration = COPYING;
        writeManifest(copy);
        writePages(copy);
        if (!verifyPages(copy)) throw new IOException("clone product verification failed");
        copy.migration = VERIFIED;
        writeManifest(copy);
    }

    private static void writeCompressed(File target, CompoundTag tag) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("cannot create " + parent);
        File temp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) { NbtIo.writeCompressed(tag, output); }
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException unsupported) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
}
