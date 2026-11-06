package bin.cnpcplus.shader;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;

public final class ShaderController {
    private static final Map<UUID, ShaderController> PLAYERS = new HashMap<>();
    private static final Map<String, ShaderController> ENTITIES = new HashMap<>();
    private static final Map<String, ShaderController> BLOCKS = new HashMap<>(), ITEMS = new HashMap<>();
    private final UUID player; private final Entity entity; private final World level; private final BlockPos position; private final ItemStack item;
    public final String target;
    public final Map<UUID, ShaderInstance> instances = new LinkedHashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private ShaderSkill shieldSkill, arcaneSkill;
    World level() { return level; }
    public ShaderInstance castShield(Map<?,?> options) { stopSkill("shield"); shieldSkill = new ShaderSkill(this, skillCaster(), true, options); return shieldSkill.effects.get(0); }
    public ShaderInstance castArcane(Map<?,?> options) { stopSkill("arcane"); arcaneSkill = new ShaderSkill(this, skillCaster(), false, options); return arcaneSkill.effects.get(0); }
    private EntityPlayerMP skillCaster() { if (!server() || !target.equals("post")) throw new IllegalArgumentException("Skills require a server player controller"); return level.getMinecraftServer().getPlayerList().getPlayerByUUID(player); }
    public void stopSkill(String kind) {
        if (kind.equals("shield")) { if (shieldSkill != null) shieldSkill.stop(); shieldSkill = null; }
        else if (kind.equals("arcane")) { if (arcaneSkill != null) arcaneSkill.stop(); arcaneSkill = null; }
        else throw new IllegalArgumentException("Skill must be shield or arcane");
    }
    private ShaderController(UUID player, Entity entity, World level, BlockPos position, ItemStack item, String target) { this.player = player; this.entity = entity; this.level = level; this.position = position; this.item = item; this.target = target; }
    public static ShaderController local() {
        if (!ShaderRuntime.client()) throw new IllegalStateException("Use player.getShaderController() on the server");
        return bin.cnpcplus.shader.client.ClientShaders.CONTROLLER;
    }
    public static ShaderController localController() { return localController("post"); }
    public static ShaderController localController(String target) { return new ShaderController(null, null, null, null, null, target); }
    public static ShaderController player(EntityPlayerMP player) {
        ShaderController controller = PLAYERS.get(player.getUniqueID());
        if (controller == null || controller.level != player.world) { controller = new ShaderController(player.getUniqueID(), null, player.world, null, null, "post"); PLAYERS.put(player.getUniqueID(), controller); } return controller;
    }
    public static ShaderController entity(Entity entity) {
        if (entity.world.isRemote) return bin.cnpcplus.shader.client.ClientShaders.objectController("entity:" + entity.getUniqueID());
        return ENTITIES.computeIfAbsent(entity.getUniqueID().toString(), id -> new ShaderController(null, entity, entity.world, null, null, "entity:" + id));
    }
    public static ShaderController entity(Entity entity, EntityPlayerMP viewer) {
        if (entity.world != viewer.world) throw new IllegalArgumentException("Shader viewer must be in the same world");
        return ENTITIES.computeIfAbsent(viewer.getUniqueID() + ":" + entity.getUniqueID(), id -> new ShaderController(viewer.getUniqueID(), entity, entity.world, null, null, "entity:" + entity.getUniqueID()));
    }
    public static String blockTarget(World world, BlockPos pos) { return "block:" + world.provider.getDimension() + ":" + pos.toLong(); }
    public static ShaderController block(World level, BlockPos position) {
        String key = blockTarget(level, position);
        if (level.isRemote) return bin.cnpcplus.shader.client.ClientShaders.objectController(key);
        return BLOCKS.computeIfAbsent(key, id -> new ShaderController(null, null, level, position.toImmutable(), null, id));
    }
    public static ShaderController block(World level, BlockPos position, EntityPlayerMP viewer) {
        if (level != viewer.world) throw new IllegalArgumentException("Shader viewer must be in the same world");
        String target = blockTarget(level, position);
        return BLOCKS.computeIfAbsent(viewer.getUniqueID() + ":" + target, id -> new ShaderController(viewer.getUniqueID(), null, level, position.toImmutable(), null, target));
    }
    public static ShaderController item(ItemStack stack, EntityPlayerMP player) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound()); NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasUniqueId("CNPCPlusShaderItem")) tag.setUniqueId("CNPCPlusShaderItem", UUID.randomUUID()); UUID id = tag.getUniqueId("CNPCPlusShaderItem");
        return ITEMS.computeIfAbsent(player.getUniqueID() + ":" + id, key -> new ShaderController(player.getUniqueID(), null, player.world, null, stack, "item:" + id));
    }
    public ShaderInstance play(String id) { return play(id, Collections.emptyMap()); }
    public ShaderInstance play(String id, Map<?,?> options) { return play(id, options, true); }
    ShaderInstance playBuiltin(String id, Map<?,?> options) { return play(id, options, false); }
    private ShaderInstance play(String id, Map<?,?> options, boolean pack) {
        ShaderDefinition definition = server() ? pack && player != null ? ShaderManager.forPlayer(player, id) : ShaderManager.SERVER.get(id) : ShaderRuntime.manager().get(id);
        if (server() && !definition.remote) throw new IllegalArgumentException("Shader is not remotely callable: " + id);
        if (target.equals("post") != definition.type.equals("POST")) throw new IllegalArgumentException("Shader target/type mismatch: " + id);
        boolean replace = !options.containsKey("replace") || Boolean.TRUE.equals(options.get("replace"));
        if (!replace && !definition.multiple) throw new IllegalArgumentException("Multiple instances not supported: " + id);
        if (instances.values().stream().filter(ShaderInstance::isActive).filter(i -> !replace || !i.getId().equals(id)).count() >= 32) throw new IllegalArgumentException("At most 32 active shader instances");
        if (replace) for (ShaderInstance instance : getInstances(id)) { instance.fadeOut = 0; instance.stop(); }
        ShaderInstance instance = new ShaderInstance(UUID.randomUUID(), definition, target);
        if (options.containsKey("duration")) instance.setDuration(((Number)options.get("duration")).intValue());
        if (options.containsKey("fadeIn")) instance.setFadeIn(((Number)options.get("fadeIn")).intValue());
        if (options.containsKey("fadeOut")) instance.setFadeOut(((Number)options.get("fadeOut")).intValue());
        if (options.containsKey("priority")) instance.setPriority(((Number)options.get("priority")).intValue());
        if (options.containsKey("timeScale")) instance.setSpeed(((Number)options.get("timeScale")).floatValue());
        instance.loop = Boolean.TRUE.equals(options.get("loop")); if (instance.loop && !definition.loop) throw new IllegalArgumentException("Loop not supported: " + id);
        instance.state = Boolean.FALSE.equals(options.get("startImmediately")) ? "PAUSED" : "LOADING";
        if (options.get("uniforms") instanceof Map && !instance.setUniforms((Map<?,?>)options.get("uniforms"))) throw new IllegalArgumentException(instance.error);
        instance.controller = this; instances.put(instance.token, instance); changed(instance); return instance;
    }
    private boolean server() { return level != null && !level.isRemote; }
    public void changed(ShaderInstance instance) { if (server()) dirty.add(instance.token); }
    private void send(ShaderInstance instance) {
        NBTTagCompound tag = instance.save(); tag.setInteger("Dimension", level.provider.getDimension());
        if (player != null) { EntityPlayerMP recipient = level.getMinecraftServer().getPlayerList().getPlayerByUUID(player); if (recipient != null) ShaderPacket.send(recipient, tag); }
        else for (EntityPlayerMP recipient : level.getMinecraftServer().getPlayerList().getPlayers()) if (recipient.world == level) ShaderPacket.send(recipient, tag);
    }
    public boolean stop(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.stop(); return result; }
    public boolean stop(ShaderInstance instance) { return instances.containsKey(instance.token) && instance.stop(); }
    public void stopAll() { instances.values().forEach(ShaderInstance::stop); }
    public void clear() { for (ShaderInstance instance : instances.values()) { instance.fadeOut = 0; instance.stop(); } }
    public boolean pause(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.pause(); return result; }
    public boolean resume(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.resume(); return result; }
    public boolean isActive(String id) { return Arrays.stream(getInstances(id)).anyMatch(ShaderInstance::isActive); }
    public ShaderInstance getInstance(String id) { ShaderInstance result = null; for (ShaderInstance instance : getInstances(id)) result = instance; return result; }
    public ShaderInstance[] getInstances(String id) { return instances.values().stream().filter(i -> i.getId().equals(id) && i.isActive()).toArray(ShaderInstance[]::new); }
    public ShaderInstance[] getAllInstances() { return instances.values().stream().filter(ShaderInstance::isActive).toArray(ShaderInstance[]::new); }
    public String[] getAvailableShaders() { return server() && player != null ? ShaderManager.available(player) : ShaderRuntime.manager().getAll(); }
    public void tick() {
        if (arcaneSkill != null) { boolean paused = arcaneSkill.effects.get(0).isPaused(); for (ShaderInstance effect : arcaneSkill.effects) { if (paused) effect.pause(); else if (effect.isPaused()) effect.resume(); } }
        for (ShaderInstance instance : instances.values()) { boolean active = instance.isActive(); instance.tick(); if (active && !instance.isActive()) dirty.add(instance.token); }
        if (shieldSkill != null) { if (shieldSkill.running()) { if (!shieldSkill.effects.get(0).isPaused()) shieldSkill.tick(); } else stopSkill("shield"); }
        if (arcaneSkill != null) { if (arcaneSkill.running()) { if (!arcaneSkill.effects.get(0).isPaused()) arcaneSkill.tick(); } else stopSkill("arcane"); }
        if (server()) for (UUID token : dirty) { ShaderInstance instance = instances.get(token); if (instance != null) send(instance); }
        dirty.clear(); instances.values().removeIf(i -> !i.isActive());
    }
    @SubscribeEvent public static void shieldDamage(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().world.isRemote) return; ShaderController controller = PLAYERS.get(event.getEntity().getUniqueID()); ShaderSkill skill = controller == null ? null : controller.shieldSkill;
        if (skill == null || !skill.running() || skill.effects.get(0).isPaused()) return;
        Vec3d position = event.getSource().getDamageLocation(); if (position == null) return;
        skill.impact(position); event.setAmount(event.getAmount() * (1-skill.reduction));
    }
    @SubscribeEvent public static void projectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        Entity projectile = event.getEntity(); if (projectile.world.isRemote || event.getRayTraceResult().hitVec == null) return;
        for (ShaderController controller : PLAYERS.values()) { ShaderSkill skill = controller.shieldSkill;
            if (skill != null && skill.blockProjectiles && skill.running() && !skill.effects.get(0).isPaused() && skill.caster.world == projectile.world
                    && skill.intercept(projectile, projectile.getPositionVector(), event.getRayTraceResult().hitVec)) { event.setCanceled(true); return; }
        }
    }
    public static void clearPlayer(UUID player) {
        ShaderController post = PLAYERS.remove(player); if (post != null) { post.clear(); post.tick(); }
        ITEMS.values().removeIf(c -> { if (!player.equals(c.player)) return false; c.clear(); c.tick(); return true; });
        for (Map<String, ShaderController> map : Arrays.asList(ENTITIES, BLOCKS)) map.values().removeIf(c -> { if (!player.equals(c.player)) return false; c.clear(); c.tick(); return true; });
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PLAYERS.values().forEach(ShaderController::tick);
        ENTITIES.values().removeIf(c -> { if (c.entity.isDead || c.entity.world != c.level) c.clear(); c.tick(); return c.instances.isEmpty(); });
        BLOCKS.values().removeIf(c -> { if (c.level.isBlockLoaded(c.position) && c.level.getTileEntity(c.position) == null) c.clear(); c.tick(); return c.instances.isEmpty(); });
        ITEMS.values().removeIf(c -> {
            EntityPlayerMP owner = c.level.getMinecraftServer().getPlayerList().getPlayerByUUID(c.player);
            boolean held = false;
            if (owner != null && owner.world == c.level) {
                for (int i = -1; i < owner.inventory.getSizeInventory(); i++) {
                    ItemStack stack = i < 0 ? owner.inventory.getItemStack() : owner.inventory.getStackInSlot(i);
                    NBTTagCompound tag = stack.getTagCompound();
                    if (!stack.isEmpty() && tag != null && tag.hasUniqueId("CNPCPlusShaderItem") && c.target.equals("item:" + tag.getUniqueId("CNPCPlusShaderItem"))) { held = true; break; }
                }
            }
            if (!held) c.clear();
            c.tick(); return c.instances.isEmpty();
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.player.world.isRemote) return; ShaderManager.clearPack(event.player.getUniqueID()); clearPlayer(event.player.getUniqueID()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) syncPlayer((EntityPlayerMP)event.player);
    }
    public static void syncPlayer(EntityPlayerMP player) {
        for (Map<?, ShaderController> map : Arrays.asList(PLAYERS, ENTITIES, BLOCKS, ITEMS)) for (ShaderController controller : map.values()) if (controller.level == player.world && (controller.player == null || controller.player.equals(player.getUniqueID()))) for (ShaderInstance instance : controller.instances.values()) {
            NBTTagCompound tag = instance.save(); tag.setInteger("Dimension", player.dimension); ShaderPacket.send(player, tag);
        }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearPlayer(event.player.getUniqueID()); login(new PlayerEvent.PlayerLoggedInEvent(event.player)); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clearPlayer(event.player.getUniqueID()); login(new PlayerEvent.PlayerLoggedInEvent(event.player)); }
    public static void stopServer() { PLAYERS.clear(); ENTITIES.clear(); BLOCKS.clear(); ITEMS.clear(); ShaderManager.clearPacks(); }
}
