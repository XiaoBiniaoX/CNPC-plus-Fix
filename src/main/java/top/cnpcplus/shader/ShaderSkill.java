package top.cnpcplus.shader;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityNPCInterface;

/** Two concrete server skills, independent of script carriers and client pack metadata. */
final class ShaderSkill {
    final ServerPlayer caster;
    final ShaderController controller;
    final boolean shield, hurtPlayers, hurtMobs, excludeCaster, blockProjectiles;
    final float damage, reduction, radius;
    final int cooldown;
    final Set<Integer> factions;
    final List<ShaderInstance> effects = new ArrayList<>();
    final Map<UUID, Long> contacts = new HashMap<>();
    final Vec3 origin;
    int age, slot;
    ShaderSkill(ShaderController controller, ServerPlayer caster, boolean shield, Map<?,?> options) {
        this.controller = controller; this.caster = caster; this.shield = shield;
        damage = number(options, "damage", shield ? 8 : 200, 0, 1000000);
        radius = number(options, "radius", 2.2f, .5f, 16);
        reduction = number(options, "reduction", .65f, 0, 1);
        cooldown = (int)number(options, "cooldown", 20, 1, 1200);
        hurtPlayers = !Boolean.FALSE.equals(options.get("hurtPlayers"));
        hurtMobs = !Boolean.FALSE.equals(options.get("hurtMobs"));
        excludeCaster = Boolean.TRUE.equals(options.get("excludeCaster"));
        blockProjectiles = !Boolean.FALSE.equals(options.get("blockProjectiles"));
        factions = options.containsKey("npcFactions") ? new HashSet<>() : null;
        if (factions != null) {
            Object values = options.get("npcFactions");
            Object[] ids = values instanceof Map<?,?> map ? map.values().toArray() : values instanceof Collection<?> list ? list.toArray() : (Object[])values;
            for (Object value : ids) {
                double id = ((Number)value).doubleValue();
                if (!Double.isFinite(id) || id < 0 || id > Integer.MAX_VALUE || id != Math.rint(id)) throw new IllegalArgumentException("NPC faction IDs must be nonnegative integers");
                factions.add((int)id);
            }
        }
        float distance = number(options, "distance", 8, 0, 128);
        double yaw = Math.toRadians(caster.getYRot());
        origin = caster.position().add(-Math.sin(yaw)*distance, 0, Math.cos(yaw)*distance);
        if (shield) effects.add(controller.playBuiltin("cnpcplus:post/plasma_shield", Map.of("duration", 400, "fadeIn", 15, "fadeOut", 20,
                "uniforms", Map.of("Radius", radius, "Distortion", 1.2f))));
        else layer(0);
    }
    private static float number(Map<?,?> options, String key, float fallback, float min, float max) {
        float value = options.containsKey(key) ? ((Number)options.get(key)).floatValue() : fallback;
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid skill option: " + key);
        return value;
    }
    boolean running() { return caster.isAlive() && caster.level() == controller.level() && effects.get(0).isActive() && effects.get(0).stopping < 0; }
    void stop() { effects.forEach(ShaderInstance::stop); }
    private boolean canHurt(Entity entity) {
        if (!entity.isAlive() || excludeCaster && entity == caster) return false;
        if (entity instanceof EntityNPCInterface npc) return factions == null || factions.contains(npc.getFaction().getId());
        return entity instanceof Player ? hurtPlayers : hurtMobs;
    }
    Vec3 center() { return caster.position().add(0, .9, 0); }
    void impact(Vec3 point) {
        Vec3 direction = point.subtract(center()).normalize();
        if (direction.lengthSqr() < .001) direction = new Vec3(1, 0, 0);
        ShaderInstance effect = effects.get(0);
        effect.setVec4("Hit" + slot, (float)direction.x, (float)direction.y, (float)direction.z, effect.time/20);
        slot = (slot+1)%4;
    }
    boolean intercept(Projectile projectile, Vec3 from, Vec3 to) {
        Vec3 start = from.subtract(center()), segment = to.subtract(from);
        double b = start.dot(segment), a = segment.lengthSqr(), c = start.lengthSqr()-radius*radius;
        double discriminant = b*b-a*c;
        if (c > 0 && a > .000001 && discriminant >= 0) {
            double t = (-b-Math.sqrt(discriminant))/a;
            if (t >= 0 && t <= 1) { impact(from.add(segment.scale(t))); projectile.discard(); return true; }
        }
        return false;
    }
    void tick() {
        age++;
        if (shield) {
            if (age >= 380) { stop(); return; }
            Vec3 center = center();
            for (Entity entity : caster.level().getEntities((Entity)null, new AABB(center, center).inflate(radius+8))) {
                if (blockProjectiles && entity instanceof Projectile projectile) {
                    Vec3 from = new Vec3(entity.xo, entity.yo, entity.zo), to = entity.position().add(entity.getDeltaMovement());
                    if (intercept(projectile, from, to)) continue;
                }
                if (age%2 != 0 || !canHurt(entity)) continue;
                AABB box = entity.getBoundingBox();
                double dx = Math.max(Math.max(box.minX-center.x, center.x-box.maxX), 0);
                double dy = Math.max(Math.max(box.minY-center.y, center.y-box.maxY), 0);
                double dz = Math.max(Math.max(box.minZ-center.z, center.z-box.maxZ), 0);
                double fx = Math.max(Math.abs(box.minX-center.x), Math.abs(box.maxX-center.x));
                double fy = Math.max(Math.abs(box.minY-center.y), Math.abs(box.maxY-center.y));
                double fz = Math.max(Math.abs(box.minZ-center.z), Math.abs(box.maxZ-center.z));
                long now = caster.level().getGameTime();
                if (dx*dx+dy*dy+dz*dz > radius*radius || fx*fx+fy*fy+fz*fz < radius*radius || contacts.getOrDefault(entity.getUUID(), 0L) > now) continue;
                contacts.put(entity.getUUID(), now+cooldown);
                impact(box.getCenter());
                NpcAPI.Instance().getIEntity(entity).damage(damage, NpcAPI.Instance().getIEntity(caster));
            }
        } else {
            if (age%30 == 0 && age <= 120) layer(age/30);
            if (age != 180) return;
            effects.add(controller.playBuiltin("cnpcplus:post/arcane_beam", Map.of("replace", false, "duration", 16, "fadeIn", 0, "fadeOut", 4, "priority", 90,
                    "uniforms", Map.of("WorldPosition", new float[]{(float)origin.x,(float)origin.y,(float)origin.z}, "Radius", 2.5f, "Height", 27, "Downward", 1, "Intensity", 1.3f))));
            for (Entity entity : caster.level().getEntities((Entity)null, new AABB(origin.x-3,origin.y-2,origin.z-3,origin.x+3,origin.y+27,origin.z+3))) {
                if (canHurt(entity) && Math.abs(entity.getX()-origin.x) <= 2.5 && Math.abs(entity.getZ()-origin.z) <= 2.5 && entity.getY() >= origin.y && entity.getY() <= origin.y+24)
                    NpcAPI.Instance().getIEntity(entity).damage(damage, NpcAPI.Instance().getIEntity(caster));
            }
        }
    }
    private void layer(int index) {
        String[] ids = {"hexagram", "strange_pattern", "super_strike", "hexagram", "strange_pattern"};
        float[] heights = {24,24.65f,25.3f,26.1f,27}, radii = {3.6f,3.2f,2.8f,2.4f,2}, speeds = {.35f,-.7f,1.1f,-1.4f,.2f};
        ShaderInstance effect = controller.playBuiltin("cnpcplus:post/"+ids[index], Map.of("replace", false, "duration", 210-age, "fadeIn", 15, "fadeOut", 8, "priority", 60+index,
                "uniforms", Map.of("WorldPosition", new float[]{(float)origin.x,(float)origin.y+heights[index],(float)origin.z}, "PlaneNormal", new float[]{0,1,0},
                        "Radius", radii[index], "Color", new float[]{.4f,.72f,1}, "RotationSpeed", speeds[index])));
        effects.add(effect);
        effect.animateUniform("Radius", .1f, radii[index], 25, "easeOut");
    }
}
