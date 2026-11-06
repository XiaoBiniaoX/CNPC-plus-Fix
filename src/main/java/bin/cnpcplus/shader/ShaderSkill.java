package bin.cnpcplus.shader;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.math.*;
import net.minecraft.util.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;

final class ShaderSkill {
    final EntityPlayerMP caster; final ShaderController controller;
    final boolean shield, hurtPlayers, hurtMobs, excludeCaster, blockProjectiles;
    final float damage, reduction, radius; final int cooldown; final Set<Integer> factions;
    final List<ShaderInstance> effects = new ArrayList<>(); final Map<UUID, Long> contacts = new HashMap<>();
    final Vec3d origin; int age, slot;
    ShaderSkill(ShaderController controller, EntityPlayerMP caster, boolean shield, Map<?,?> options) {
        this.controller = controller; this.caster = caster; this.shield = shield;
        damage = number(options, "damage", shield ? 8 : 200, 0, 1000000); radius = number(options, "radius", 2.2f, .5f, 16);
        reduction = number(options, "reduction", .65f, 0, 1); cooldown = (int)number(options, "cooldown", 20, 1, 1200);
        hurtPlayers = !Boolean.FALSE.equals(options.get("hurtPlayers")); hurtMobs = !Boolean.FALSE.equals(options.get("hurtMobs"));
        excludeCaster = Boolean.TRUE.equals(options.get("excludeCaster")); blockProjectiles = !Boolean.FALSE.equals(options.get("blockProjectiles"));
        factions = options.containsKey("npcFactions") ? new HashSet<>() : null;
        if (factions != null) {
            Object values = options.get("npcFactions"); Object[] ids = values instanceof Map ? ((Map<?,?>)values).values().toArray() : values instanceof Collection ? ((Collection<?>)values).toArray() : (Object[])values;
            for (Object value : ids) { double id = ((Number)value).doubleValue(); if (!Double.isFinite(id) || id < 0 || id > Integer.MAX_VALUE || id != Math.rint(id)) throw new IllegalArgumentException("NPC faction IDs must be nonnegative integers"); factions.add((int)id); }
        }
        float distance = number(options, "distance", 8, 0, 128); double yaw = Math.toRadians(caster.rotationYaw);
        origin = caster.getPositionVector().add(-Math.sin(yaw)*distance, 0, Math.cos(yaw)*distance);
        if (shield) {
            ShaderInstance effect = controller.playBuiltin("cnpcplus:post/plasma_shield", Collections.emptyMap()); effect.setDuration(400); effect.setFadeIn(15); effect.setFadeOut(20); effect.setFloat("Radius", radius); effect.setFloat("Distortion", 1.2f); effects.add(effect);
        } else layer(0);
    }
    private static float number(Map<?,?> options, String key, float fallback, float min, float max) { float value = options.containsKey(key) ? ((Number)options.get(key)).floatValue() : fallback; if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid skill option: " + key); return value; }
    boolean running() { return caster.isEntityAlive() && caster.world == controller.level() && effects.get(0).isActive() && effects.get(0).stopping < 0; }
    void stop() { effects.forEach(ShaderInstance::stop); }
    private boolean canHurt(Entity entity) {
        if (!entity.isEntityAlive() || excludeCaster && entity == caster) return false;
        if (entity instanceof EntityNPCInterface) return factions == null || factions.contains(((EntityNPCInterface)entity).getFaction().id);
        return entity instanceof EntityPlayer ? hurtPlayers : hurtMobs;
    }
    Vec3d center() { return caster.getPositionVector().add(0, .9, 0); }
    void impact(Vec3d point) { Vec3d direction = point.subtract(center()).normalize(); if (direction.lengthSquared() < .001) direction = new Vec3d(1,0,0); ShaderInstance effect = effects.get(0); effect.setVec4("Hit" + slot, (float)direction.x, (float)direction.y, (float)direction.z, effect.time/20); slot = (slot+1)%4; }
    boolean intercept(Entity projectile, Vec3d from, Vec3d to) {
        Vec3d start = from.subtract(center()), segment = to.subtract(from); double b = start.dotProduct(segment), a = segment.lengthSquared(), c = start.lengthSquared()-radius*radius, discriminant = b*b-a*c;
        if (c > 0 && a > .000001 && discriminant >= 0) { double t = (-b-Math.sqrt(discriminant))/a; if (t >= 0 && t <= 1) { impact(from.add(segment.scale(t))); projectile.setDead(); return true; } } return false;
    }
    void tick() {
        age++;
        if (shield) {
            if (age >= 380) { stop(); return; } Vec3d center = center();
            for (Entity entity : caster.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(center.x,center.y,center.z,center.x,center.y,center.z).grow(radius+8))) {
                if (blockProjectiles && (entity instanceof IProjectile || entity instanceof EntityFireball)) {
                    Vec3d from = new Vec3d(entity.prevPosX, entity.prevPosY, entity.prevPosZ), to = entity.getPositionVector().add(entity.motionX,entity.motionY,entity.motionZ); if (intercept(entity, from, to)) continue;
                }
                if (age%2 != 0 || !canHurt(entity)) continue; AxisAlignedBB box = entity.getEntityBoundingBox();
                double dx = Math.max(Math.max(box.minX-center.x,center.x-box.maxX),0), dy = Math.max(Math.max(box.minY-center.y,center.y-box.maxY),0), dz = Math.max(Math.max(box.minZ-center.z,center.z-box.maxZ),0);
                double fx = Math.max(Math.abs(box.minX-center.x),Math.abs(box.maxX-center.x)), fy = Math.max(Math.abs(box.minY-center.y),Math.abs(box.maxY-center.y)), fz = Math.max(Math.abs(box.minZ-center.z),Math.abs(box.maxZ-center.z));
                long now = caster.world.getTotalWorldTime(); if (dx*dx+dy*dy+dz*dz > radius*radius || fx*fx+fy*fy+fz*fz < radius*radius || contacts.getOrDefault(entity.getUniqueID(),0L) > now) continue;
                contacts.put(entity.getUniqueID(),now+cooldown); impact(new Vec3d(box.minX + (box.maxX-box.minX)*.5, box.minY + (box.maxY-box.minY)*.5, box.minZ + (box.maxZ-box.minZ)*.5)); entity.attackEntityFrom(DamageSource.causePlayerDamage(caster), damage);
            }
        } else {
            if (age%30 == 0 && age <= 120) layer(age/30); if (age != 180) return;
            ShaderInstance beam = controller.playBuiltin("cnpcplus:post/arcane_beam", Collections.singletonMap("replace",false));
            beam.setDuration(16); beam.setFadeOut(4); beam.setPriority(90); beam.setVec3("WorldPosition",(float)origin.x,(float)origin.y,(float)origin.z); beam.setFloat("Radius",2.5f); beam.setFloat("Height",27); beam.setFloat("Downward",1); beam.setFloat("Intensity",1.3f); effects.add(beam);
            for (Entity entity : caster.world.getEntitiesWithinAABBExcludingEntity(null,new AxisAlignedBB(origin.x-3,origin.y-2,origin.z-3,origin.x+3,origin.y+27,origin.z+3)))
                if (canHurt(entity) && Math.abs(entity.posX-origin.x) <= 2.5 && Math.abs(entity.posZ-origin.z) <= 2.5 && entity.posY >= origin.y && entity.posY <= origin.y+24) entity.attackEntityFrom(DamageSource.causePlayerDamage(caster), damage);
        }
    }
    private void layer(int index) {
        String[] ids = {"hexagram","strange_pattern","super_strike","hexagram","strange_pattern"}; float[] heights = {24,24.65f,25.3f,26.1f,27}, radii = {3.6f,3.2f,2.8f,2.4f,2}, speeds = {.35f,-.7f,1.1f,-1.4f,.2f};
        ShaderInstance effect = controller.playBuiltin("cnpcplus:post/"+ids[index], Collections.singletonMap("replace",false));
        effect.setDuration(210-age); effect.setFadeIn(15); effect.setFadeOut(8); effect.setPriority(60+index); effect.setVec3("WorldPosition",(float)origin.x,(float)origin.y+heights[index],(float)origin.z); effect.setVec3("PlaneNormal",0,1,0); effect.setFloat("Radius",radii[index]); effect.setVec3("Color",.4f,.72f,1); effect.setFloat("RotationSpeed",speeds[index]); effects.add(effect); effect.animateUniform("Radius",.1f,radii[index],25,"easeOut");
    }
}
