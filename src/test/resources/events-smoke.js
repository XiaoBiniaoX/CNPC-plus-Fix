/* Test-only script, run by the production Forge server in build/server-smoke-420. */
var System = Java.type("java.lang.System");
var Forge = Java.type("net.minecraftforge.common.MinecraftForge");
var Hurt = Java.type("net.minecraftforge.event.entity.living.LivingHurtEvent");
var Damage = Java.type("net.minecraft.util.DamageSource");
var Container = Java.type("noppes.npcs.controllers.ScriptContainer");
var Controller = Java.type("noppes.npcs.controllers.ScriptController");
var ScriptEvents = Java.type("bin.cnpcplus.script.tools.ScriptEvents");
var Tick = Java.type("net.minecraftforge.fml.common.gameevent.TickEvent");
var NBT = Java.type("net.minecraft.nbt.NBTTagCompound");
var normalCalls = 0, canceledCalls = 0, cnpcCalls = 0, hashDone = false;
var h, receiveCanceled, cnpc, page, handler, npc;
function check(value, message) { if (!value) throw new Error(message); }
function hurt() { return new Hurt(npc.getMCEntity(), Damage.field_76377_j, 3.5); }
function failed(error) {
    System.out.println("[EVENTS_SMOKE_FAIL] " + error);
    if (error instanceof Java.type("java.lang.Throwable")) error.printStackTrace();
    else if (error.stack) System.out.println(error.stack);
}
function init(e) {
    try {
        npc = e.npc;
        page = Container.Current;
        handler = npc.getMCEntity().script;
        h = events.on(Hurt.class.getName(), function(event) {
            check(Container.Current === page && events.has(h), "Forge callback page context");
            normalCalls++;
            event.setAmount(7);
        });
        check(events.list().size() === 1 && events.has(h), "registration");
        var event = hurt();
        Forge.EVENT_BUS.post(event);
        check(normalCalls === 1 && event.getAmount() === 7, "root Event listener inheritance and synchronous mutation");
        receiveCanceled = events.on(Hurt.class.getName(), true, function(event) { canceledCalls++; });
        event = hurt(); event.setCanceled(true); Forge.EVENT_BUS.post(event);
        check(normalCalls === 1 && canceledCalls === 1, "receiveCanceled filter");
        handler.getScripts().get(1).run("verify", h);
        check(!handler.getScripts().get(1).errored && events.list().size() === 2 && events.has(h), "nested page isolation and Current restore");
        cnpc = events.on("noppes.npcs.api.event.NpcEvent$UpdateEvent", function(event) {
            check(Container.Current === page && events.list().size() === 3, "CNPC callback context"); cnpcCalls++;
        });
        e.API.events().post(new (Java.type("noppes.npcs.api.event.NpcEvent$UpdateEvent"))(npc));
        check(cnpcCalls === 1, "CNPC event bus");
        var Thread = Java.type("java.lang.Thread");
        var worker = new Thread(function() { Forge.EVENT_BUS.post(hurt()); ScriptEvents.release(page); });
        worker.start(); worker.join();
        check(normalCalls === 1 && events.has(h), "worker event/release must not enter server scripts");
        var once = events.on(Hurt.class.getName(), function(event) {
            check(events.offAll() === 4, "offAll inside callback");
        });
        Forge.EVENT_BUS.post(hurt());
        check(events.list().isEmpty() && !events.has(once) && h.getState() === "UNSUBSCRIBED", "callback unsubscribe");
        h = events.on(Hurt.class.getName(), function(event) { normalCalls++; });
        var world = npc.getWorld();
        var fake = Java.type("net.minecraftforge.common.util.FakePlayerFactory").getMinecraft(world.getMCWorld());
        var player = e.API.getIEntity(fake);
        // FakePlayer has no network connection; only position the test entity, not a player teleport.
        fake.func_70107_b(npc.getX(), npc.getY(), npc.getZ());
        var carrier = world.createItem("customnpcs:scripted_item", 0, 1);
        carrier.setEnabled(true); carrier.setLanguage("ECMAScript");
        var timerPage = new Container(carrier);
        timerPage.script = new (Java.type("java.lang.String"))(Java.type("java.nio.file.Files").readAllBytes(
            Java.type("java.nio.file.Paths").get("verification-02.js")), Java.type("java.nio.charset.StandardCharsets").UTF_8)
            + "\nfunction verifyResults(e) { if (timerCount !== 3 || e.item.getTimers().has(420)) throw new Error('02 timer results'); }";
        carrier.getScripts().add(timerPage); carrier.saveScriptData();
        for (var i = 0; i < 60; i++) carrier.cnpcplus$tickCarrier(player, world, i);
        timerPage = carrier.getScripts().get(0);
        timerPage.run("verifyResults", new (Java.type("bin.cnpcplus.script.ExtraScriptHook$ItemTickEvent"))(carrier, player, world));
        check(!timerPage.errored, "02 production carrier timer: " + timerPage.console);
        Async.supply("SHA256", "abc").onSuccess(function(value) {
            check(value === "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "async value");
            check(Container.Current === page && events.has(h), "Async continuation page context");
            hashDone = true;
        }).onFailure(failed);
        Scheduler.runLater(120, finish);
    } catch (error) { failed(error); }
}
function finish() {
    try {
        check(hashDone && Container.Current === page && events.has(h), "Scheduler/Async page context");
        Forge.EVENT_BUS.post(hurt());
        for (var index = 2; index < handler.getScripts().size(); index++) {
            var verification = handler.getScripts().get(index);
            verification.run("verifyResults", new (Java.type("noppes.npcs.api.event.NpcEvent$UpdateEvent"))(npc));
            check(!verification.errored, "verification page " + index + ": " + verification.console);
        }
        page.readFromNBT(page.writeToNBT(new NBT()));
        check(!events.has(h) && events.list().isEmpty(), "page NBT replacement cleanup");
        h = events.on(Hurt.class.getName(), function(event) {});
        var controller = Controller.Instance, loaded = controller.lastLoaded;
        controller.lastLoaded++;
        try { ScriptEvents.tick(new Tick.ServerTickEvent(Tick.Phase.START)); }
        finally { controller.lastLoaded = loaded; }
        check(!events.has(h), "script reload cleanup");
        h = events.on(Hurt.class.getName(), function(event) {});
        handler.readFromNBT(new NBT());
        check(!events.has(h) && events.list().isEmpty(), "delete saved pages cleanup");
        System.out.println("[EVENTS_SMOKE_PASS] forge/cnpc/cancel/page/offAll/worker/scheduler/async/nbt/reload/delete/verification-01-02-03-09");
    } catch (error) { failed(error); }
}
