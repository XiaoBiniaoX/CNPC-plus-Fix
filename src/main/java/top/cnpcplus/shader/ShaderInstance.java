package top.cnpcplus.shader;

import java.util.*;
import net.minecraft.nbt.*;

public final class ShaderInstance {
    public final UUID token;
    public final ShaderDefinition definition;
    public final String target;
    public ShaderController controller;
    public String state = "LOADING", error = "";
    public int duration, fadeIn, fadeOut, priority;
    public float speed = 1, time;
    public boolean loop;
    public int stopping = -1;
    public final Map<String, float[]> uniforms = new LinkedHashMap<>();
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final Map<String, Animation> animations = new LinkedHashMap<>();
    public ShaderInstance(UUID token, ShaderDefinition definition, String target) {
        this.token = token; this.definition = definition; this.target = target;
        duration = definition.duration; priority = definition.priority;
        definition.uniforms.forEach((name, parameter) -> uniforms.put(name, parameter.defaults().clone()));
        textures.putAll(definition.samplers);
    }
    private void changed() { if (controller != null) controller.changed(this); }
    public String getId() { return definition.id; }
    public String getState() { return state; }
    public String getError() { return error; }
    public boolean isActive() { return !state.equals("STOPPED") && !state.equals("ERROR"); }
    public boolean isPaused() { return state.equals("PAUSED"); }
    public boolean stop() { if (!isActive()) return false; stopping = 0; animations.clear(); state = fadeOut == 0 ? "STOPPED" : "STOPPING"; changed(); return true; }
    public boolean pause() { if (!isActive() || isPaused()) return false; state = "PAUSED"; changed(); return true; }
    public boolean resume() { if (!isPaused()) return false; state = stopping >= 0 ? "STOPPING" : "PLAYING"; changed(); return true; }
    public void setDuration(int ticks) { if (ticks != -1 && (ticks < 1 || ticks > 1000000)) throw new IllegalArgumentException("Duration must be -1 or 1..1000000 ticks"); duration = ticks; changed(); }
    public int getDuration() { return duration; }
    public int getRemainingTime() { return duration == -1 ? -1 : Math.max(0, duration - (int)time); }
    public void setPriority(int value) { if (Math.abs((long)value) > 1000000) throw new IllegalArgumentException("Priority out of range"); priority = value; changed(); }
    public int getPriority() { return priority; }
    public void setSpeed(float value) { if (!Float.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("Speed must be 0..100"); speed = value; changed(); }
    public float getSpeed() { return speed; }
    public void setTime(float ticks) { if (!Float.isFinite(ticks) || ticks < 0 || ticks > 1000000) throw new IllegalArgumentException("Time out of range"); time = ticks; changed(); }
    public float getTime() { return time; }
    public float getProgress() { return duration < 0 ? 0 : Math.min(1, time / duration); }
    public void setFadeIn(int ticks) { if (ticks < 0 || ticks > 1000000) throw new IllegalArgumentException("Invalid fade"); fadeIn = ticks; changed(); }
    public void setFadeOut(int ticks) { if (ticks < 0 || ticks > 1000000) throw new IllegalArgumentException("Invalid fade"); fadeOut = ticks; changed(); }
    public ShaderDefinition getDefinition() { return definition; }
    public boolean hasUniform(String name) { return uniforms.containsKey(name); }
    public Object getUniform(String name) { float[] value = uniforms.get(name); return value == null ? null : value.length == 1 ? value[0] : value.clone(); }
    public String[] getUniformNames() { return definition.getUniformNames(); }
    public String getUniformType(String name) { return definition.uniforms.containsKey(name) ? definition.uniforms.get(name).type() : ""; }
    public Object getDefaultUniform(String name) { var parameter = definition.uniforms.get(name); return parameter == null ? null : parameter.defaults().clone(); }
    public boolean setUniform(String name, Object value) {
        try {
            float[] numbers = numbers(value);
            String type = getUniformType(name);
            if (type.equals("bool") != (value instanceof Boolean) && numbers.length == 1) throw new IllegalArgumentException("Boolean uniform type mismatch");
            definition.validate(name, numbers);
            uniforms.put(name, numbers); animations.remove(name); changed(); return true;
        } catch (IllegalArgumentException e) { error = e.getMessage(); org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] {} {}", getId(), error); return false; }
    }
    private boolean typed(String name, String type, Object value) {
        if (!getUniformType(name).equals(type)) { error = "Uniform type mismatch: " + name + " expected " + type; org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] {} {}", getId(), error); return false; }
        return setUniform(name, value);
    }
    public boolean setInt(String name, int value) { return typed(name, "int", value); }
    public boolean setFloat(String name, float value) { return typed(name, "float", value); }
    public boolean setBool(String name, boolean value) { return typed(name, "bool", value); }
    public boolean setVec2(String name, float x, float y) { return typed(name, "vec2", new float[]{x,y}); }
    public boolean setVec3(String name, float x, float y, float z) { return typed(name, "vec3", new float[]{x,y,z}); }
    public boolean setVec4(String name, float x, float y, float z, float w) { return typed(name, "vec4", new float[]{x,y,z,w}); }
    public boolean setVector(String name, Object values) { return setUniform(name, values); }
    public boolean setMatrix4(String name, Object values) { return typed(name, "matrix4", values); }
    public boolean setUniforms(Map<?,?> values) { boolean result = true; for (var entry : values.entrySet()) result &= setUniform(entry.getKey().toString(), entry.getValue()); return result; }
    public boolean resetUniform(String name) { var parameter = definition.uniforms.get(name); if (parameter == null) return false; uniforms.put(name, parameter.defaults().clone()); animations.remove(name); changed(); return true; }
    public void resetUniforms() { animations.clear(); definition.uniforms.forEach((name, parameter) -> uniforms.put(name, parameter.defaults().clone())); changed(); }
    public boolean hasTexture(String name) { return textures.containsKey(name); }
    public String getTexture(String name) { return textures.get(name); }
    public String[] getTextureNames() { return definition.getSamplerNames(); }
    public boolean setTexture(String name, String id) {
        if (!hasTexture(name) || name.startsWith("HistorySampler") || name.equals("DiffuseSampler") || name.equals("DepthSampler") || name.equals("MaskSampler") && definition.type.equals("RENDER")) return false;
        textures.put(name, ShaderDefinition.resource(id)); changed(); return true;
    }
    public boolean resetTexture(String name) { if (!hasTexture(name)) return false; textures.put(name, definition.samplers.get(name)); changed(); return true; }
    public Animation animateUniform(String name, Object from, Object to, int ticks, String easing) {
        float[] start = numbers(from), end = numbers(to);
        definition.validate(name, start); definition.validate(name, end);
        if (getUniformType(name).equals("int") || getUniformType(name).equals("bool")) throw new IllegalArgumentException("Animate float/vector/matrix uniforms only");
        if (ticks < 1 || ticks > 1000000 || !Set.of("linear","easeIn","easeOut","easeInOut","smoothstep").contains(easing)) throw new IllegalArgumentException("Invalid animation duration/easing");
        Animation animation = new Animation(start, end, ticks, easing, time);
        animations.put(name, animation); uniforms.put(name, start.clone()); changed(); return animation;
    }
    public boolean cancelAnimation(String name) { boolean removed = animations.remove(name) != null; if (removed) changed(); return removed; }
    public boolean isAnimating(String name) { return animations.containsKey(name); }
    public Animation getAnimation(String name) { return animations.get(name); }
    public record Animation(float[] from, float[] to, int duration, String easing, float start) {
        public float[] value(float now) {
            float p = Math.max(0, Math.min(1, (now - start) / duration));
            p = switch (easing) { case "easeIn" -> p*p; case "easeOut" -> 1-(1-p)*(1-p); case "easeInOut" -> p < .5f ? 2*p*p : 1-2*(1-p)*(1-p); case "smoothstep" -> p*p*(3-2*p); default -> p; };
            float[] value = new float[from.length]; for (int i = 0; i < value.length; i++) value[i] = from[i] + (to[i]-from[i])*p; return value;
        }
    }
    public float opacity(float partial) {
        float now = time + (isPaused() ? 0 : partial * speed), value = fadeIn == 0 ? 1 : Math.min(1, now / fadeIn);
        if (stopping >= 0) value *= fadeOut == 0 ? 0 : Math.max(0, 1 - (stopping + (isPaused() ? 0 : partial)) / fadeOut);
        else if (duration > 0 && !loop && fadeOut > 0) value *= Math.max(0, Math.min(1, (duration - now) / fadeOut));
        return value;
    }
    public void tick() {
        if (!isActive() || isPaused()) return;
        if (state.equals("LOADING") || state.equals("READY")) state = "PLAYING";
        time += speed;
        for (var iterator = animations.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next(); uniforms.put(entry.getKey(), entry.getValue().value(time));
            if (time >= entry.getValue().start + entry.getValue().duration) iterator.remove();
        }
        if (stopping >= 0 && ++stopping >= fadeOut) state = "STOPPED";
        if (duration > 0 && time >= duration) { if (loop) time %= duration; else state = "STOPPED"; }
    }
    public static float[] numbers(Object value) {
        if (value instanceof Boolean bool) return new float[]{bool ? 1 : 0};
        if (value instanceof Number number) return new float[]{number.floatValue()};
        Object[] array;
        if (value instanceof Map<?,?> map) array = map.values().toArray();
        else if (value instanceof Collection<?> list) array = list.toArray();
        else if (value != null && value.getClass().isArray()) {
            int size = java.lang.reflect.Array.getLength(value); array = new Object[size];
            for (int i = 0; i < size; i++) array[i] = java.lang.reflect.Array.get(value, i);
        } else throw new IllegalArgumentException("Numeric scalar or array required");
        if (array.length < 1 || array.length > 16) throw new IllegalArgumentException("Uniform array length must be 1..16");
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) { if (!(array[i] instanceof Number number)) throw new IllegalArgumentException("Numeric array required"); result[i] = number.floatValue(); }
        return result;
    }
    private static ListTag list(float[] values) { ListTag list = new ListTag(); for (float value : values) list.add(FloatTag.valueOf(value)); return list; }
    private static float[] array(ListTag list) { float[] result = new float[list.size()]; for (int i = 0; i < result.length; i++) result[i] = list.getFloat(i); return result; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Token", token); tag.putString("Id", getId()); tag.putString("Target", target);
        tag.putString("State", state); tag.putInt("Duration", duration); tag.putInt("FadeIn", fadeIn); tag.putInt("FadeOut", fadeOut);
        tag.putInt("Priority", priority); tag.putFloat("Speed", speed); tag.putFloat("Time", time); tag.putBoolean("Loop", loop); tag.putInt("Stopping", stopping);
        CompoundTag params = new CompoundTag(); uniforms.forEach((name, value) -> params.put(name, list(value))); tag.put("Uniforms", params);
        CompoundTag samplers = new CompoundTag(); textures.forEach(samplers::putString); tag.put("Textures", samplers);
        CompoundTag anims = new CompoundTag(); animations.forEach((name, value) -> {
            CompoundTag a = new CompoundTag(); a.put("From", list(value.from)); a.put("To", list(value.to)); a.putInt("Duration", value.duration); a.putString("Easing", value.easing); a.putFloat("Start", value.start); anims.put(name, a);
        }); tag.put("Animations", anims); return tag;
    }
    public static ShaderInstance read(CompoundTag tag, ShaderManager manager) {
        ShaderInstance instance = new ShaderInstance(tag.getUUID("Token"), manager.get(tag.getString("Id")), tag.getString("Target"));
        instance.state = tag.getString("State"); instance.setDuration(tag.getInt("Duration")); instance.setFadeIn(tag.getInt("FadeIn")); instance.setFadeOut(tag.getInt("FadeOut"));
        if (!Set.of("LOADING", "READY", "PLAYING", "PAUSED", "STOPPING", "STOPPED", "ERROR").contains(instance.state)) throw new IllegalArgumentException("Unknown shader state");
        if (instance.target.equals("post") != instance.definition.type.equals("POST") || !instance.target.equals("post") && !instance.target.matches("(entity|item):[0-9a-f-]{36}|block:[a-z0-9_.-]+:[a-z0-9_./-]+:-?[0-9]+")) throw new IllegalArgumentException("Invalid shader target/type");
        instance.setPriority(tag.getInt("Priority")); instance.setSpeed(tag.getFloat("Speed")); instance.setTime(tag.getFloat("Time"));
        instance.loop = tag.getBoolean("Loop") && instance.definition.loop; instance.stopping = tag.getInt("Stopping");
        if (instance.stopping < -1 || instance.stopping > 1000000) throw new IllegalArgumentException("Invalid stopping time");
        CompoundTag params = tag.getCompound("Uniforms");
        for (String name : params.getAllKeys()) { float[] value = array(params.getList(name, Tag.TAG_FLOAT)); instance.definition.validate(name, value); instance.uniforms.put(name, value); }
        CompoundTag textures = tag.getCompound("Textures"); for (String name : textures.getAllKeys()) {
            if (!instance.hasTexture(name)) throw new IllegalArgumentException("Unknown sampler " + name);
            instance.textures.put(name, ShaderDefinition.resource(textures.getString(name)));
        }
        CompoundTag anims = tag.getCompound("Animations"); for (String name : anims.getAllKeys()) {
            CompoundTag a = anims.getCompound(name); instance.animateUniform(name, array(a.getList("From", Tag.TAG_FLOAT)), array(a.getList("To", Tag.TAG_FLOAT)), a.getInt("Duration"), a.getString("Easing"));
            Animation value = instance.animations.get(name); instance.animations.put(name, new Animation(value.from, value.to, value.duration, value.easing, a.getFloat("Start")));
        }
        return instance;
    }
}
