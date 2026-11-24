/* Fixtures expose CNPC APIs, but deliberately no MCP methods on raw entities. */
var messages = [], entityQueries = 0, closestQueries = 0, sneaking = false;
var npcId = "00000000-0000-0000-0000-000000000001";
var playerId = "00000000-0000-0000-0000-000000000002";
function assert(value, message) { if (!value) throw new Error(message); }
var world = {
    getEntity: function(uuid) { entityQueries++; assert(uuid === npcId, "invalid UUID reached world API"); return npc; },
    getClosestEntity: function(pos, range, type) {
        closestQueries++; assert(pos === position && range === 16 && type === 2, "nearby reporter arguments"); return npc;
    },
    getDimension: function() { return {getId: function() { return 0; }}; }
};
var position = {};
var npc = {
    say: function(message) { messages.push(String(message)); },
    getUUID: function() { return npcId; }, getType: function() { return 2; },
    getWorld: function() { return world; }, getPos: function() { return position; },
    getName: function() { return "Reporter"; }
};
var player = {
    getWorld: function() { return world; }, getPos: function() { return position; },
    getUUID: function() { return playerId; }, getType: function() { return 1; },
    getName: function() { return "Caster"; }, isSneaking: function() { return sneaking; }
};
var block = {getWorld: function() { return world; }, getPos: function() { return position; }};
var item = {getTimers: function() { return testTimers; }, getName: function() { return "customnpcs:scripted_item"; }};
var rawNpc = {}, rawPlayer = {}, rawDamage = {};
var api = {
    getIEntity: function(raw) {
        if (raw === null) return null;
        assert(raw === rawNpc || raw === rawPlayer, "unexpected raw entity");
        return raw === rawNpc ? npc : player;
    },
    getIDamageSource: function(raw) { assert(raw === rawDamage, "damage wrapper"); return {getType: function() { return "test"; }}; }
};
var event = {npc: npc, player: player, block: block, item: item, world: world, API: api,
    getClass: function() { return {getName: function() { return "VerificationEvent"; }}; }};
var clock = 0, ends = {};
var Cooldown = {
    start: function(owner, id, duration) { assert(owner === event[OWNER], "CD owner"); ends[id] = clock + duration; },
    getRemaining: function(owner, id) { return Math.max(0, (ends[id] || clock) - clock); },
    isReady: function(owner, id) { return this.getRemaining(owner, id) === 0; },
    clear: function(owner, id) { delete ends[id]; },
    tryStart: function(owner, id, duration) { if (!this.isReady(owner, id)) return false; this.start(owner, id, duration); return true; }
};
var handles = [];
var events = {
    isAvailable: function(name) { return true; },
    on: function(name, callback) {
        var h = {name: name, callback: callback, active: true, getState: function() { return this.active ? "ACTIVE" : "UNSUBSCRIBED"; }};
        handles.push(h); return h;
    },
    has: function(h) { return h !== null && h.active; },
    list: function() { var count = handles.filter(function(h) { return h.active; }).length; return {size: function() { return count; }}; },
    off: function(h) { var active = h.active; h.active = false; return active; },
    offAll: function() { var count = this.list().size(); handles.forEach(function(h) { h.active = false; }); return count; }
};
function check01(owner) {
    OWNER = owner;
    for (var i = 0; i < 100; i++) {
        if (i % 10 === 0) tick(event);
        tickfix(event); clock++;
    }
    assert(fixedCount === 100 && nativeCount === 10, "01 tick counts");
    assert(messages.some(function(m) { return m.indexOf("tryStart(40)=true 重复=false remaining=40") >= 0; }), "01 CD results");
    assert(messages.filter(function(m) { return m.indexOf("进度 tickfix=") >= 0; }).length === 4, "01 progress");
    var count = messages.length; interact(event);
    assert(messages.length === count + 1 && messages[count].indexOf("completed=true") >= 0, "01 right-click status");
    sneaking = true; interact(event); sneaking = false;
    assert(fixedCount === 0 && nativeCount === 0 && !started, "01 restart");
    tickfix(event); assert(started && fixedCount === 1, "01 restarted tick");
    assert(entityQueries === 0, "01 default must not query empty UUID");
    if (owner !== "npc") {
        REPORT_NPC_UUID = npcId; assert(resolveReporter(event) === npc && entityQueries === 1, "01 explicit UUID");
        REPORT_NPC_UUID = "bad";
        var rejected = false;
        try { resolveReporter(event); } catch (error) { rejected = String(error).indexOf("REPORT_NPC_UUID") >= 0; }
        assert(rejected && entityQueries === 1, "01 UUID boundary validation");
        REPORT_NPC_UUID = "";
    }
    fixedCount = 0; nativeCount = 0;
    for (var j = 0; j < 20; j++) tick(event);
    assert(messages[messages.length - 1].indexOf("tickfix=0") >= 0, "01 missing hook diagnostic");
}
function check02() {
    tickfix(event);
    assert(started && testTimers.has(420) && entityQueries === 0 && closestQueries === 1, "02 default nearby NPC");
    var timerEvent = {id: 420, item: item, player: player, world: world};
    for (var i = 0; i < 60; i++) testTimers.tick(function(id) { timer(eventFor(id)); });
    function eventFor(id) { assert(id === 420, "02 timer ID"); return timerEvent; }
    assert(timerCount === 3 && !testTimers.has(420), "02 timer runs three times and stops");
    assert(messages[messages.length - 1].indexOf("stop=true has=false") >= 0, "02 stop result");
    started = false; REPORT_NPC_UUID = npcId; tickfix(event);
    assert(started && entityQueries === 1, "02 explicit UUID");
    started = false; REPORT_NPC_UUID = "bad";
    var rejected = false;
    try { tickfix(event); } catch (error) { rejected = String(error).indexOf("REPORT_NPC_UUID") >= 0; }
    assert(rejected && entityQueries === 1, "02 invalid UUID rejected before world API");
}
function check03() {
    interact(event);
    assert(messages.some(function(m) { return m.indexOf("\u00a7c这段应为红色\u00a7r") >= 0; }), "03 colored original retains codes");
    assert(messages.some(function(m) { return m.indexOf("stripCodes 去色后=这段应为红色") >= 0 && m.indexOf("\u00a7") < 0; }), "03 stripped output");
}
function check09() {
    init(event);
    assert(handles.length === 3, "09 subscriptions");
    var spell = {getClass: function() { return {getName: function() { return "TestSpell"; }}; }};
    var rawCaster = rawPlayer;
    var castEvent = {
        getCaster: function() { return rawCaster; }, getSpell: function() { return spell; },
        getSource: function() { return "WAND"; }, getX: function() { return 1; }, getY: function() { return 2; }, getZ: function() { return 3; }
    };
    wizardryHandle.callback(castEvent);
    assert(messages[messages.length - 1].indexOf("caster=Caster player=true uuid=" + playerId) >= 0, "09 raw player wrapped");
    var count = messages.length; rawCaster = rawNpc; wizardryHandle.callback(castEvent);
    rawCaster = null; wizardryHandle.callback(castEvent);
    assert(messages.length === count, "09 players-only excludes mobs and dispensers");
    PLAYERS_ONLY = false; wizardryHandle.callback(castEvent);
    assert(messages[messages.length - 1].indexOf("caster=none player=false uuid=none") >= 0, "09 null caster with all sources");
    rawCaster = rawNpc; wizardryHandle.callback(castEvent);
    assert(messages[messages.length - 1].indexOf("caster=Reporter player=false") >= 0, "09 non-player caster");
    hurtHandle.callback({getEntityLiving: function() { return rawNpc; }, getSource: function() { return rawDamage; },
        getAmount: function() { return 3.5; }, isCanceled: function() { return false; }});
    assert(messages[messages.length - 1].indexOf("amount=3.5 source=test") >= 0, "09 hurt wraps raw entity and damage source");
    interact(event); sneaking = true; interact(event);
    assert(events.list().size() === 0, "09 off/offAll");
}
