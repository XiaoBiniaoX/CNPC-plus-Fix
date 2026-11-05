package top.cnpcplus.shader;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** CPU state and batching on the server; renderer-owned state on the client. */
public final class ShaderController {
    private static final Map<UUID, ShaderController> PLAYERS = new HashMap<>();
    private static final Map<UUID, ShaderController> ENTITIES = new HashMap<>();
    private static final Map<String, ShaderController> BLOCKS = new HashMap<>();
    private static final Map<String, ShaderController> ITEMS = new HashMap<>();
    private final UUID player;
    private final Entity entity;
    private final Level level;
    private final BlockPos position;
    private final ItemStack item;
    public final String target;
    public final Map<UUID, ShaderInstance> instances = new LinkedHashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private ShaderSkill shieldSkill, arcaneSkill;
    Level level() { return level; }
    public ShaderInstance castShield(Map<?,?> options) {
        stopSkill("shield");
        shieldSkill = new ShaderSkill(this, skillCaster(), true, options);
        return shieldSkill.effects.get(0);
    }
    public ShaderInstance castArcane(Map<?,?> options) {
        stopSkill("arcane");
        arcaneSkill = new ShaderSkill(this, skillCaster(), false, options);
        return arcaneSkill.effects.get(0);
    }
    private ServerPlayer skillCaster() {
        if (!server() || !target.equals("post")) throw new IllegalArgumentException("Skills require a server player controller");
        return level.getServer().getPlayerList().getPlayer(player);
    }
    public void stopSkill(String kind) {
        if (kind.equals("shield")) { if (shieldSkill != null) shieldSkill.stop(); shieldSkill = null; }
        else if (kind.equals("arcane")) { if (arcaneSkill != null) arcaneSkill.stop(); arcaneSkill = null; }
        else throw new IllegalArgumentException("Skill must be shield or arcane");
    }
    private ShaderController(UUID player, Entity entity, Level level, BlockPos position, ItemStack item, String target) {
        this.player = player; this.entity = entity; this.level = level; this.position = position; this.item = item; this.target = target;
    }
    public static ShaderController local() { return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.CONTROLLER); }
    public static ShaderController localController() { return new ShaderController(null, null, null, null, null, "post"); }
    public static ShaderController localController(String target) { return new ShaderController(null, null, null, null, null, target); }
    public static ShaderController player(ServerPlayer player) {
        ShaderController controller = PLAYERS.get(player.getUUID());
        if (controller == null || controller.level != player.level()) {
            controller = new ShaderController(player.getUUID(), null, player.level(), null, null, "post");
            PLAYERS.put(player.getUUID(), controller);
        }
        return controller;
    }
    public static ShaderController entity(Entity entity) {
        if (entity.level().isClientSide) return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.objectController("entity:" + entity.getUUID()));
        return ENTITIES.computeIfAbsent(entity.getUUID(), id -> new ShaderController(null, entity, entity.level(), null, null, "entity:" + id));
    }
    public static ShaderController block(Level level, BlockPos position) {
        String key = "block:" + level.dimension().location() + ":" + position.asLong();
        if (level.isClientSide) return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.objectController(key));
        return BLOCKS.computeIfAbsent(key, id -> new ShaderController(null, null, level, position.immutable(), null, id));
    }
    public static ShaderController item(ItemStack stack, ServerPlayer player) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("CNPCPlusShaderItem")) tag.putUUID("CNPCPlusShaderItem", UUID.randomUUID());
        UUID id = tag.getUUID("CNPCPlusShaderItem");
        return ITEMS.computeIfAbsent(player.getUUID() + ":" + id, key -> new ShaderController(player.getUUID(), null, player.level(), null, stack, "item:" + id));
    }
    public ShaderInstance play(String id) { return play(id, Map.of()); }
    public ShaderInstance play(String id, Map<?,?> options) {
        return play(id, options, true);
    }
    ShaderInstance playBuiltin(String id, Map<?,?> options) { return play(id, options, false); }
    private ShaderInstance play(String id, Map<?,?> options, boolean pack) {
        ShaderDefinition definition = server() ? pack && player != null ? ShaderManager.forPlayer(player, id) : ShaderManager.SERVER.get(id) : ShaderRuntime.manager().get(id);
        if (server() && !definition.remote) throw new IllegalArgumentException("Shader is not remotely callable: " + id);
        if (!target.equals("post") && !definition.type.equals("RENDER") || target.equals("post") && !definition.type.equals("POST")) throw new IllegalArgumentException("Shader target/type mismatch: " + id);
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
        instance.loop = Boolean.TRUE.equals(options.get("loop"));
        if (instance.loop && !definition.loop) throw new IllegalArgumentException("Loop not supported: " + id);
        instance.state = Boolean.FALSE.equals(options.get("startImmediately")) ? "PAUSED" : "LOADING";
        if (options.get("uniforms") instanceof Map<?,?> uniforms && !instance.setUniforms(uniforms)) throw new IllegalArgumentException(instance.error);
        instance.controller = this;
        instances.put(instance.token, instance);
        changed(instance);
        return instance;
    }
    private boolean server() { return level != null && !level.isClientSide; }
    public void changed(ShaderInstance instance) {
        if (server()) dirty.add(instance.token);
    }
    private void send(ShaderInstance instance) {
        CompoundTag tag = instance.save(); tag.putString("Dimension", level.dimension().location().toString());
        if (player != null) {
            ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(player);
            if (recipient != null) ShaderPacket.send(recipient, tag);
        } else {
            for (ServerPlayer recipient : level.getServer().getPlayerList().getPlayers()) if (recipient.level() == level) ShaderPacket.send(recipient, tag);
        }
    }
    public boolean stop(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.stop(); return result; }
    public boolean stop(ShaderInstance instance) { return instances.containsKey(instance.token) && instance.stop(); }
    public void stopAll() { for (ShaderInstance instance : instances.values()) instance.stop(); }
    public void clear() { for (ShaderInstance instance : instances.values()) { instance.fadeOut = 0; instance.stop(); } }
    public boolean pause(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.pause(); return result; }
    public boolean resume(String id) { boolean result = false; for (ShaderInstance instance : getInstances(id)) result |= instance.resume(); return result; }
    public boolean isActive(String id) { return Arrays.stream(getInstances(id)).anyMatch(ShaderInstance::isActive); }
    public ShaderInstance getInstance(String id) { ShaderInstance result = null; for (ShaderInstance instance : getInstances(id)) result = instance; return result; }
    public ShaderInstance[] getInstances(String id) { return instances.values().stream().filter(i -> i.getId().equals(id) && i.isActive()).toArray(ShaderInstance[]::new); }
    public ShaderInstance[] getAllInstances() { return instances.values().stream().filter(ShaderInstance::isActive).toArray(ShaderInstance[]::new); }
    public String[] getAvailableShaders() { return server() && player != null ? ShaderManager.available(player) : ShaderRuntime.manager().getAll(); }
    public void tick() {
        if (arcaneSkill != null) {
            boolean paused = arcaneSkill.effects.get(0).isPaused();
            for (ShaderInstance effect : arcaneSkill.effects) {
                if (paused) effect.pause(); else if (effect.isPaused()) effect.resume();
            }
        }
        for (ShaderInstance instance : instances.values()) {
            boolean active = instance.isActive(); instance.tick();
            if (active && !instance.isActive()) dirty.add(instance.token);
        }
        if (shieldSkill != null) { if (shieldSkill.running()) { if (!shieldSkill.effects.get(0).isPaused()) shieldSkill.tick(); } else stopSkill("shield"); }
        if (arcaneSkill != null) { if (arcaneSkill.running()) { if (!arcaneSkill.effects.get(0).isPaused()) arcaneSkill.tick(); } else stopSkill("arcane"); }
        if (server()) for (UUID token : dirty) { ShaderInstance instance = instances.get(token); if (instance != null) send(instance); }
        dirty.clear();
        instances.values().removeIf(i -> !i.isActive());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void shieldDamage(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ShaderController controller = PLAYERS.get(event.getEntity().getUUID());
        ShaderSkill skill = controller == null ? null : controller.shieldSkill;
        if (skill == null || !skill.running() || skill.effects.get(0).isPaused()) return;
        Vec3 position = event.getSource().getSourcePosition();
        if (position == null) return; // Environmental damage is not blocked by a spatial shield.
        skill.impact(position);
        event.setAmount(event.getAmount()*(1-skill.reduction));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void projectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        var projectile = event.getProjectile();
        if (projectile.level().isClientSide) return;
        for (ShaderController controller : PLAYERS.values()) {
            ShaderSkill skill = controller.shieldSkill;
            if (skill != null && skill.blockProjectiles && skill.running() && !skill.effects.get(0).isPaused() && skill.caster.level() == projectile.level()
                    && skill.intercept(projectile, projectile.position(), event.getRayTraceResult().getLocation())) {
                event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                return;
            }
        }
    }
    public static void clearPlayer(UUID player) {
        ShaderController post = PLAYERS.remove(player);
        if (post != null) { post.clear(); post.tick(); }
        ITEMS.values().removeIf(c -> { if (!player.equals(c.player)) return false; c.clear(); c.tick(); return true; });
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        PLAYERS.values().forEach(ShaderController::tick);
        ENTITIES.values().removeIf(c -> { if (c.entity.isRemoved() || c.entity.level() != c.level) { c.clear(); c.tick(); return true; } c.tick(); return c.instances.isEmpty(); });
        BLOCKS.values().removeIf(c -> {
            if (c.level.hasChunkAt(c.position) && c.level.getBlockEntity(c.position) == null) c.clear();
            c.tick(); return c.instances.isEmpty();
        });
        ITEMS.values().removeIf(c -> { if (c.item.isEmpty()) c.clear(); c.tick(); return c.instances.isEmpty(); });
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        ShaderManager.clearPack(event.getEntity().getUUID());
        clearPlayer(event.getEntity().getUUID());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ShaderController controller : ENTITIES.values()) if (controller.level == player.level()) for (ShaderInstance instance : controller.instances.values()) {
            CompoundTag tag = instance.save(); tag.putString("Dimension", player.level().dimension().location().toString()); ShaderPacket.send(player, tag);
        }
        for (ShaderController controller : BLOCKS.values()) if (controller.level == player.level()) for (ShaderInstance instance : controller.instances.values()) {
            CompoundTag tag = instance.save(); tag.putString("Dimension", player.level().dimension().location().toString()); ShaderPacket.send(player, tag);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayer(event.getEntity().getUUID());
        login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(event.getEntity()));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        clearPlayer(event.getEntity().getUUID());
        login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(event.getEntity()));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopServer(net.minecraftforge.event.server.ServerStoppedEvent event) { PLAYERS.clear(); ENTITIES.clear(); BLOCKS.clear(); ITEMS.clear(); ShaderManager.clearPacks(); }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reload(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources -> {
            PLAYERS.values().forEach(ShaderController::clear); ENTITIES.values().forEach(ShaderController::clear);
            BLOCKS.values().forEach(ShaderController::clear); ITEMS.values().forEach(ShaderController::clear);
            ShaderManager.SERVER.reloadDefinitions(resources);
        });
    }
}
