package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.battles.runner.ShowdownService;
import dev.hyperspacewormholes.wormholes.Wormholes;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The boss's side inside Showdown, shared by every trainer's battle against it.
 * Cobblemon's default runner keeps one JavaScript context for every battle, so this evaluates a hook into it that turns a boss Pokemon's HP and stat stages into one pool shared by all its battles, and holds the battles so their turns run together in one order.
 * The context is reached by reflection because GraalVM's classes aren't on this mod's compile path.
 */
public final class ShowdownPool {
    private static final String HOOK = """
            (function () {
              if (globalThis.hwBoss) return globalThis.hwBoss;
              const pools = new Map();
              const members = new Map();
              const STATS = ['atk', 'def', 'spa', 'spd', 'spe', 'accuracy', 'evasion'];
              const toId = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
              const slot = function (battle, id) {
                const m = battle.dex.moves.get(id);
                if (!m || !m.exists) return null;
                const pp = Math.max(99, m.pp || 1);
                return { move: m.name, id: m.id, pp: pp, maxpp: pp, target: m.target, disabled: false, disabledSource: '', used: false };
              };
              const fit = function (battle, p, pool) {
                const ids = (pool.moves && pool.moves.length ? pool.moves : p.hwOwnMoves).slice();
                if (pool.extra && !ids.includes(pool.extra)) ids.push(pool.extra);
                const slots = ids.map(id => slot(battle, id)).filter(Boolean);
                if (!slots.length) return;
                p.baseMoveSlots = slots;
                p.moveSlots = slots.slice();
              };
              const battles = function (id) {
                const out = [];
                for (const [bid, stream] of battleMap) {
                  const b = stream.battle;
                  if (b && b.hwPool === id) out.push([bid, b]);
                }
                return out;
              };
              const bossOf = function (b) {
                for (const side of b.sides) for (const p of side.pokemon) if (p && p.hwBoss) return p;
                return null;
              };
              const foeOf = function (b) {
                for (const side of b.sides) for (const p of side.active) if (p && !p.hwBoss && !p.fainted && p.hp > 0) return p;
                return null;
              };
              const stateOf = b => b.ended ? 'ended' : !b.midTurn ? 'idle' : !b.hwHeld ? 'wait' : b.queue.peek() ? 'held' : 'done';
              const ban = function (p, pool) {
                if (p.hwBan) return;
                p.hwBan = true;
                const getMoves = p.getMoves;
                p.getMoves = function () {
                  const moves = getMoves.apply(this, arguments);
                  for (const m of moves) {
                    if (m && pool.banned.has(m.id)) { m.disabled = true; m.disabledSource = 'boss'; }
                  }
                  return moves.length && moves.every(m => m.disabled) ? [] : moves;
                };
              };
              const hold = function (battle) {
                if (battle.hwGo) return;
                const go = battle.go;
                battle.hwGo = go;
                const spread = battle.actions.trySpreadMoveHit;
                battle.actions.trySpreadMoveHit = function (targets, pokemon, move, notActive) {
                  if (this.battle.hwSpread && pokemon && pokemon.hwBoss && move && move.category !== 'Status') move.spreadHit = true;
                  return spread.call(this, targets, pokemon, move, notActive);
                };
                battle.go = function () {
                  if (this.turn < 1 || this.ended || this.hwFree) return go.apply(this, arguments);
                  this.add('');
                  this.add('t:', Math.floor(Date.now() / 1000));
                  if (this.requestState) this.requestState = '';
                  if (!this.midTurn) {
                    this.queue.insertChoice({ choice: 'beforeTurn' });
                    this.queue.addChoice({ choice: 'residual' });
                    this.midTurn = true;
                  }
                  this.hwHeld = true;
                };
              };
              // A boss with a z item counts as holding that Z-Crystal while Showdown works out its Z-Moves, and only
              // then, so it keeps its real held item
              const withZ = function (p, fn) {
                const pool = p && p.hwBoss !== undefined ? pools.get(p.hwBoss) : null;
                if (!pool || !pool.zItem) return fn();
                // its Z-Moves are rationed by the boss (BossDef.Ultimate), not by Showdown's one per battle, so hard
                // mode's second Light That Burns the Sky can go
                const was = p.item, used = p.side.zMoveUsed;
                p.item = pool.zItem;
                p.side.zMoveUsed = false;
                try { return fn(); } finally { p.item = was; p.side.zMoveUsed = used || p.side.zMoveUsed; }
              };
              const zwrap = function (battle) {
                if (battle.hwZ) return;
                battle.hwZ = true;
                const a = battle.actions;
                const can = a.canZMove, get = a.getZMove, active = a.getActiveZMove;
                a.canZMove = function (p) { return withZ(p, () => can.call(this, p)); };
                a.getZMove = function (m, p, skip) { return withZ(p, () => get.call(this, m, p, skip)); };
                a.getActiveZMove = function (m, p) { return withZ(p, () => active.call(this, m, p)); };
              };
              // A wave (Hoopa Unbound's legendaries, LegendWave): each member's HP and stages are shared by every trainer's
              // battle like a boss's, but it can faint (floor 0) and its battles run their turns as usual; a member
              // knocked out in another battle faints here before the next action runs.
              const share = function (p, pool) {
                Object.defineProperty(p, 'hp', { configurable: true, enumerable: true,
                  get() { return pool.hp; },
                  set(v) { if (typeof v === 'number' && !isNaN(v)) pool.hp = Math.max(pool.floor, Math.min(pool.maxhp, Math.round(v))); } });
                for (const key of ['maxhp', 'baseMaxhp']) {
                  Object.defineProperty(p, key, { configurable: true, enumerable: true, get() { return pool.maxhp; }, set(v) {} });
                }
                Object.defineProperty(p, 'boosts', { configurable: true, enumerable: true,
                  get() { return pool.boosts; },
                  set(v) { if (v && this.battle && this.battle.turn >= 1) for (const k of STATS) pool.boosts[k] = v[k] || 0; } });
              };
              const waveFaint = function (battle) {
                let any = false;
                for (const side of battle.sides) {
                  if (!side) continue;
                  for (const p of side.active) {
                    if (p && p.hwWave !== undefined && !p.fainted && !p.faintQueued && p.hp <= 0) { p.faint(); any = true; }
                  }
                }
                if (any) battle.faintMessages();
              };
              // One trainer's Pokemon against a boss and then the whole wave, one active at a time.
              // Cobblemon asks for doubles, and before anyone is sent out each side
              // becomes one slot; callIn grows the wild side later, so no slot is ever null. A doubles game counts
              // everyone on the field as adjacent (activePerHalf 2), which is what this needs: spread moves reach every
              // legendary, they all reach the trainer, and triples' rules (shifting, the swap to the centre at the end)
              // never run on odd sides. Only validTargetLoc assumes equal sides, so this battle gets its own. Once the
              // whole wild side has fainted the next turn waits for sendBack, or the trainer would be asked for a move
              // with nobody to use it on.
              const lopside = function (battle, wild) {
                if (battle.gameType !== 'doubles') return;
                const trainer = battle.sides[1 - wild.n];
                trainer.active = [null];
                trainer.slotConditions.length = 1;
                wild.active = wild.pokemon.map(() => null);
                battle.hwWild = wild;
                const nextTurn = battle.nextTurn;
                battle.nextTurn = function () {
                  if (this.hwFree && wild.active.every(p => p.fainted)) return;
                  return nextTurn.apply(this, arguments);
                };
                battle.validTargetLoc = function (targetLoc, source, targetType) {
                  if (targetLoc === 0) return true;
                  const sourceLoc = source.getLocOf(source);
                  const isFoe = targetLoc > 0;
                  if (Math.abs(targetLoc) > (isFoe ? source.side.foe : source.side).active.length) return false;
                  const isSelf = sourceLoc === targetLoc;
                  const isAdjacent = isFoe || Math.abs(targetLoc - sourceLoc) === 1;
                  switch (targetType) {
                    case 'randomNormal':
                    case 'scripted':
                    case 'normal':
                      return isAdjacent;
                    case 'adjacentAlly':
                      return isAdjacent && !isFoe;
                    case 'adjacentAllyOrSelf':
                      return isAdjacent && !isFoe || isSelf;
                    case 'adjacentFoe':
                      return isAdjacent && isFoe;
                    case 'any':
                      return !isSelf;
                  }
                  return false;
                };
              };
              const wavewrap = function (battle) {
                if (battle.hwWaveWrapped) return;
                battle.hwWaveWrapped = true;
                const runAction = battle.runAction;
                battle.runAction = function (action) {
                  try { waveFaint(this); } catch (e) {}
                  if (this.ended) return;
                  return runAction.apply(this, arguments);
                };
              };
              const attach = function (battle) {
                let n = 0, wild = null;
                if (!battle || !battle.sides) return n;
                for (const side of battle.sides) {
                  if (!side) continue;
                  for (const p of side.pokemon) {
                    const id = members.get(String(p.set.uuid));
                    const pool = id === undefined ? undefined : pools.get(id);
                    if (!pool) continue;
                    n++;
                    if (pool.wave) {
                      if (p.hwWave === id) continue;
                      share(p, pool);
                      p.hwWave = id;
                      wavewrap(battle);
                      continue;
                    }
                    if (pool.lopsided) wild = side;
                    if (p.hwBoss === id) continue;
                    Object.defineProperty(p, 'hp', { configurable: true, enumerable: true,
                      get() { return pool.hp; },
                      set(v) { if (typeof v === 'number' && !isNaN(v)) pool.hp = Math.max(pool.floor, Math.min(pool.maxhp, Math.round(v))); } });
                    for (const key of ['maxhp', 'baseMaxhp']) {
                      Object.defineProperty(p, key, { configurable: true, enumerable: true, get() { return pool.maxhp; }, set(v) {} });
                    }
                    Object.defineProperty(p, 'boosts', { configurable: true, enumerable: true,
                      get() { return pool.boosts; },
                      set(v) { if (v && this.battle && this.battle.turn >= 1) for (const k of STATS) pool.boosts[k] = v[k] || 0; } });
                    p.hwOwnMoves = p.baseMoveSlots.map(s => s.id);
                    fit(battle, p, pool);
                    p.hwBoss = id;
                    battle.hwPool = id;
                    hold(battle);
                    zwrap(battle);
                  }
                }
                const pool = battle.hwPool === undefined ? undefined : pools.get(battle.hwPool);
                if (pool) {
                  for (const side of battle.sides) {
                    if (!side) continue;
                    for (const p of side.pokemon) if (!p.hwBoss && p.hwWave === undefined) ban(p, pool);
                  }
                }
                if (wild && !battle.started) lopside(battle, wild);
                return n;
              };
              const run = function (b, action, pool, echo) {
                const before = b.log.length;
                b.hwSpread = !!(pool && pool.together && pool.spread);
                try {
                  b.runAction(action);
                } catch (e) {
                  b.hwHeld = false;
                  b.hwError = String(e && e.stack || e);
                }
                b.hwSpread = false;
                if (echo) {
                  for (let i = b.log.length - 1; i >= before; i--) if (b.log[i].startsWith('|-zpower|')) b.log.splice(i, 1);
                }
                if (b.requestState || b.ended) b.hwHeld = false;
                const loud = b.log.slice(before).some(l => l && l !== '|' && !l.startsWith('|t:|') && l !== '|upkeep');
                b.sendUpdates();
                return loud || !b.hwHeld;
              };
              const describe = (bid, a) => bid + ':' + a.choice + (a.move ? ':' + a.move.id : '') + (a.pokemon && a.pokemon.hwBoss ? ':boss' : '');
              const writeLine = BS.BattleStream.prototype._writeLine;
              BS.BattleStream.prototype._writeLine = function (type, message) {
                const result = writeLine.call(this, type, message);
                const battle = this.battle;
                if (type === 'start' && battle && !battle.hwWrapped) {
                  battle.hwWrapped = true;
                  const start = battle.start;
                  battle.start = function () {
                    try { attach(this); } catch (e) { this.hwError = String(e && e.stack || e); }
                    return start.apply(this, arguments);
                  };
                }
                return result;
              };
              globalThis.hwBoss = {
                attach(battleId) {
                  const stream = battleMap.get(battleId);
                  return String(stream ? attach(stream.battle) : -1);
                },
                pool(id, hp, maxhp) {
                  let p = pools.get(id);
                  if (!p) {
                    p = { hp: 1, maxhp: 1, floor: 1, boosts: {}, moves: null, extra: null, together: false, spread: false, banned: new Set() };
                    for (const k of STATS) p.boosts[k] = 0;
                    pools.set(id, p);
                  }
                  p.maxhp = Math.max(1, maxhp);
                  p.hp = Math.max(p.floor, Math.min(p.maxhp, hp));
                  return p.hp + '/' + p.maxhp;
                },
                read(id) {
                  const p = pools.get(id);
                  return p ? p.hp + '/' + p.maxhp + '/' + STATS.map(k => p.boosts[k] || 0).join(',') : '';
                },
                setup(id, moves) {
                  const p = pools.get(id);
                  if (!p) return 'none';
                  p.moves = moves ? String(moves).split(',').filter(Boolean) : null;
                  for (const [, b] of battles(id)) { const boss = bossOf(b); if (boss) fit(b, boss, p); }
                  return 'ok';
                },
                ban(id, moves) {
                  const p = pools.get(id);
                  if (!p) return 'none';
                  p.banned = new Set(String(moves || '').split(',').filter(Boolean).map(toId));
                  return String(p.banned.size);
                },
                target(moveId) {
                  const m = Dex.moves.get(moveId);
                  return m && m.exists ? m.target + '/' + m.category : '';
                },
                arm(id, move) {
                  const p = pools.get(id);
                  if (!p) return 'none';
                  p.extra = move ? toId(move) : null;
                  for (const [, b] of battles(id)) { const boss = bossOf(b); if (boss) fit(b, boss, p); }
                  return 'ok';
                },
                boost(id, changes) {
                  const p = pools.get(id);
                  if (!p) return '';
                  for (const part of String(changes).split(',')) {
                    const [k, v] = part.split(':');
                    if (STATS.includes(k)) p.boosts[k] = Math.max(-6, Math.min(6, (p.boosts[k] || 0) + Number(v)));
                  }
                  return STATS.map(k => p.boosts[k] || 0).join(',');
                },
                status(id) {
                  return battles(id).map(([bid, b]) => bid + '=' + stateOf(b) + '=' + b.turn + (b.hwError ? '=error' : '')).join(';');
                },
                error(battleId) {
                  const s = battleMap.get(battleId);
                  const e = s && s.battle ? s.battle.hwError : '';
                  if (s && s.battle) s.battle.hwError = '';
                  return e || '';
                },
                step(id) {
                  const pool = pools.get(id);
                  const list = battles(id).filter(([, b]) => stateOf(b) === 'held');
                  let out = '';
                  for (let guard = 0; guard < 64; guard++) {
                    let best = null, ties = 0;
                    for (const e of list) {
                      const b = e[1];
                      if (!b.hwHeld || b.ended) continue;
                      const a = b.queue.peek();
                      if (!a) continue;
                      if (!best) { best = e; ties = 1; continue; }
                      const d = b.comparePriority(a, best[1].queue.peek());
                      if (d < 0) { best = e; ties = 1; }
                      else if (d === 0 && Math.random() * ++ties < 1) best = e;
                    }
                    if (!best) return out;
                    const [bid, b] = best;
                    const action = b.queue.shift();
                    const together = pool && pool.together && action.choice === 'move' && action.pokemon && action.pokemon.hwBoss === id;
                    const loud = run(b, action, together ? pool : null, false);
                    out = describe(bid, action);
                    if (together) {
                      for (const [ob, o] of list) {
                        if (o === b || !o.hwHeld || o.ended) continue;
                        const i = o.queue.list.findIndex(x => x.choice === 'move' && x.pokemon && x.pokemon.hwBoss === id);
                        if (i < 0) continue;
                        const x = o.queue.list.splice(i, 1)[0];
                        run(o, x, pool, true);
                        out += ',' + describe(ob, x);
                      }
                      pool.together = false;
                      pool.spread = false;
                      return out;
                    }
                    if (loud) return out;
                  }
                  return out;
                },
                finish(id) {
                  let n = 0;
                  for (const [, b] of battles(id)) {
                    if (stateOf(b) !== 'done') continue;
                    b.hwHeld = false;
                    try {
                      b.nextTurn();
                    } catch (e) {
                      b.hwError = String(e && e.stack || e);
                    }
                    b.midTurn = false;
                    b.queue.clear();
                    b.sendUpdates();
                    n++;
                  }
                  const pool = pools.get(id);
                  if (pool) { pool.together = false; pool.spread = false; }
                  return String(n);
                },
                cancel(id, all) {
                  const acts = all ? ['move', 'beforeTurnMove', 'priorityChargeMove', 'switch', 'shift', 'megaEvo', 'megaEvoX',
                    'megaEvoY', 'runDynamax', 'terastallize', 'event'] : ['move', 'beforeTurnMove', 'priorityChargeMove'];
                  let n = 0;
                  for (const [, b] of battles(id)) {
                    const before = b.queue.list.length;
                    b.queue.list = b.queue.list.filter(a => !(a.pokemon && (all || a.pokemon.hwBoss === id) && acts.includes(a.choice)));
                    n += before - b.queue.list.length;
                  }
                  const pool = pools.get(id);
                  if (pool) { pool.together = false; pool.spread = false; }
                  return String(n);
                },
                together(id, spread) {
                  const p = pools.get(id);
                  if (p) { p.together = true; p.spread = !!spread; }
                  return 'ok';
                },
                transform(id, spec) {
                  const o = JSON.parse(spec);
                  const pool = pools.get(id);
                  if (!pool) return '0';
                  if (o.moves && o.moves.length) pool.moves = o.moves;
                  let n = 0, species = '';
                  for (const [, b] of battles(id)) {
                    const p = bossOf(b);
                    if (!p) continue;
                    if (o.level) { p.level = o.level; p.set.level = o.level; }
                    const sp = o.species ? b.dex.species.get(o.species) : p.species;
                    if (sp && sp.exists && p.setSpecies(sp, null)) {
                      p.baseSpecies = sp;
                      p.details = sp.name + ', ' + p.uuid + (p.level === 100 ? '' : ', L' + p.level) + (p.gender === '' ? '' : ', ' + p.gender) + (p.set.shiny ? ', shiny' : '');
                    }
                    if (o.ability) { p.setAbility(o.ability, null, true); p.baseAbility = p.ability; }
                    if (o.item) { p.item = toId(o.item); p.itemState = { id: p.item, target: p }; }
                    if (o.zitem) pool.zItem = toId(o.zitem);
                    fit(b, p, pool);
                    species = p.species.name + '/' + p.ability + '/' + p.item + '/L' + p.level + '/' + p.moveSlots.map(m => m.id).join(',');
                    n++;
                  }
                  return n + ':' + species;
                },
                score(battleId, moveId) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  if (!b) return '0';
                  const p = bossOf(b), foe = foeOf(b);
                  if (!p || !foe) return '0';
                  const move = b.dex.getActiveMove(moveId);
                  if (!move || !move.exists || move.category === 'Status') return '0';
                  let dmg = 0;
                  try { dmg = b.actions.getDamage(p, foe, move, true); } catch (e) { dmg = 0; }
                  if (typeof dmg !== 'number' || isNaN(dmg)) dmg = 0;
                  return String(Math.min(dmg / Math.max(1, foe.hp), 1) + (dmg >= foe.hp ? 1 : 0));
                },
                callIn(battleId, packed) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  if (!b || !b.hwWild || b.ended) return 'none';
                  const wild = b.hwWild;
                  const Pokemon = wild.active[0].constructor;
                  if (!b.hwFree) b.hwAway = wild.pokemon.find(p => p.hwBoss !== undefined);
                  b.queue.clear();
                  b.midTurn = false;
                  b.hwHeld = false;
                  b.hwFree = true;
                  b.clearRequest();
                  for (const p of wild.active) p.isActive = false;
                  const team = b.getTeam({ team: packed }).map(set => new Pokemon(set, wild));
                  wild.pokemon = team;
                  wild.active = team.slice();
                  wild.pokemonLeft = team.length + 1;
                  while (wild.slotConditions.length < team.length) wild.slotConditions.push({});
                  team.forEach((p, i) => { p.position = i; p.isActive = true; });
                  try {
                    attach(b);
                    for (const p of team) b.actions.runSwitch(p);
                    b.nextTurn();
                  } catch (e) {
                    b.hwError = String(e && e.stack || e);
                  }
                  b.sendUpdates();
                  return b.sides.map(side => side.active.length).join('v');
                },
                settle(battleId) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  if (!b || b.ended) return 'none';
                  try { waveFaint(b); } catch (e) { b.hwError = String(e && e.stack || e); }
                  b.sendUpdates();
                  return 'ok';
                },
                switching(battleId) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  return b && !b.ended && b.requestState === 'switch' ? 'yes' : 'no';
                },
                sendBack(battleId) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  if (!b || !b.hwAway || b.ended) return 'none';
                  const wild = b.hwWild, boss = b.hwAway;
                  b.queue.clear();
                  b.midTurn = false;
                  b.hwHeld = false;
                  b.hwFree = false;
                  b.clearRequest();
                  for (const p of wild.active) p.isActive = false;
                  wild.pokemon = [boss];
                  wild.active = [boss];
                  wild.pokemonLeft = 1;
                  boss.position = 0;
                  boss.isActive = true;
                  boss.isStarted = true;
                  boss.activeTurns = 0;
                  b.hwAway = null;
                  // a move locked in on the third legendary (Outrage) would aim past the one slot left
                  for (const p of b.sides[1 - wild.n].pokemon) if (p.lastMoveTargetLoc > 1) p.lastMoveTargetLoc = 1;
                  return 'ok';
                },
                turn(battleId) {
                  const s = battleMap.get(battleId);
                  const b = s && s.battle;
                  if (!b || b.ended || b.midTurn || b.requestState) return 'none';
                  try { b.nextTurn(); } catch (e) { b.hwError = String(e && e.stack || e); }
                  b.sendUpdates();
                  return 'ok';
                },
                join(uuid, id) { members.set(uuid, id); return 'ok'; },
                wave(id) {
                  const p = pools.get(id);
                  if (!p) return 'none';
                  p.wave = true;
                  p.floor = 0;
                  return 'ok';
                },
                lopsided(id) {
                  const p = pools.get(id);
                  if (p) p.lopsided = true;
                  return 'ok';
                },
                leave(uuid) { members.delete(uuid); return 'ok'; },
                drop(id) {
                  pools.delete(id);
                  for (const [k, v] of [...members]) if (v === id) members.delete(k);
                  return 'ok';
                }
              };
              return globalThis.hwBoss;
            })()
            """;

    private static @Nullable Object installedFor;
    private static @Nullable Object hook;
    private static @Nullable Method invokeMember, asString, getContext;
    private static @Nullable Class<?> serviceType;
    private static boolean warned;

    private ShowdownPool() {}

    public record State(int hp, int max, int[] stages) {}

    public static boolean available() {
        return hook() != null;
    }

    public static boolean reinstalled() {
        Object before = installedFor;
        hook();
        return installedFor != before;
    }

    public static void pool(String id, int hp, int max) {
        call("pool", id, hp, max);
    }

    public static @Nullable State read(String id) {
        String text = call("read", id);
        if (text == null || text.isEmpty()) return null;
        String[] parts = text.split("/");
        try {
            int[] stages = new int[BossDef.STATS.size()];
            if (parts.length > 2) stages(parts[2], stages);
            return new State(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), stages);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void stages(String csv, int[] into) {
        String[] values = csv.split(",");
        for (int i = 0; i < into.length && i < values.length; i++) into[i] = Integer.parseInt(values[i]);
    }

    public static void setup(String id, @Nullable String moves) {
        call("setup", id, moves == null ? "" : moves);
    }

    public static void arm(String id, @Nullable String move) {
        call("arm", id, move == null ? "" : move);
    }

    public static @Nullable String boost(String id, String changes) {
        return call("boost", id, changes);
    }

    public static Map<String, String[]> status(String id) {
        Map<String, String[]> out = new LinkedHashMap<>();
        String text = call("status", id);
        if (text == null || text.isEmpty()) return out;
        for (String entry : text.split(";")) {
            String[] parts = entry.split("=");
            if (parts.length >= 3) out.put(parts[0], parts);
        }
        return out;
    }

    public static String error(String battleId) {
        return text("error", battleId);
    }

    public static String step(String id) {
        return text("step", id);
    }

    public static int finish(String id) {
        return number(call("finish", id));
    }

    public static int cancel(String id, boolean everyone) {
        return number(call("cancel", id, everyone));
    }

    public static void together(String id, boolean spread) {
        call("together", id, spread);
    }

    public static void ban(String id, List<String> moves) {
        call("ban", id, String.join(",", moves));
    }

    public static String target(String moveId) {
        return text("target", moveId);
    }

    public static String transform(String id, String spec) {
        return text("transform", id, spec);
    }

    public static double score(String battleId, String moveId) {
        String text = call("score", battleId, moveId);
        try {
            return text == null ? 0 : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int attach(String battleId) {
        return number(call("attach", battleId));
    }

    /** Makes the pool a wave member's: it can faint, and its battles aren't held (LegendWave). */
    public static void wave(String id) {
        call("wave", id);
    }

    /** Makes the boss's battles doubles of one slot a side, ready for a wave (LegendWave). */
    public static void lopsided(String id) {
        call("lopsided", id);
    }

    /** Swaps the battle's boss for these packed legendaries and starts the next turn; the sides' slots, "1v3". */
    public static String callIn(String battleId, String packed) {
        return text("callIn", battleId, packed);
    }

    /** Faints any legendary whose shared HP ran out in another battle. */
    public static void settle(String battleId) {
        call("settle", battleId);
    }

    /** A trainer here still has to choose a Pokemon to replace a fainted one. */
    public static boolean switching(String battleId) {
        return "yes".equals(call("switching", battleId));
    }

    public static void sendBack(String battleId) {
        call("sendBack", battleId);
    }

    /** Starts the next turn of a battle sitting between turns. */
    public static void turn(String battleId) {
        call("turn", battleId);
    }

    public static void join(String pokemonUuid, String id) {
        call("join", pokemonUuid, id);
    }

    public static void leave(String pokemonUuid) {
        call("leave", pokemonUuid);
    }

    public static void drop(String id) {
        call("drop", id);
    }

    private static int number(@Nullable String text) {
        try {
            return text == null ? -1 : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String text(String member, Object... args) {
        String text = call(member, args);
        return text == null ? "" : text;
    }

    private static @Nullable String call(String member, Object... args) {
        Object target = hook();
        if (target == null) return null;
        try {
            Object result = invokeMember.invoke(target, member, args);
            return (String) asString.invoke(result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.error("Boss Showdown call {} failed", member, e);
            return null;
        }
    }

    private static @Nullable Object hook() {
        try {
            ShowdownService service = ShowdownService.Companion.getService();
            if (service.getClass() != serviceType) {
                try {
                    getContext = service.getClass().getMethod("getContext");
                } catch (NoSuchMethodException e) {
                    if (!warned) Wormholes.LOGGER.warn("Showdown runner {} has no script context, so boss battles can't be shared", service.getClass().getName());
                    warned = true;
                    return null;
                }
                serviceType = service.getClass();
            }
            Object context = getContext.invoke(service);
            if (context == null) return null;
            if (context != installedFor || hook == null) {
                Method eval = context.getClass().getMethod("eval", String.class, CharSequence.class);
                Object value = eval.invoke(context, "js", HOOK);
                invokeMember = value.getClass().getMethod("invokeMember", String.class, Object[].class);
                asString = value.getClass().getMethod("asString");
                hook = value;
                installedFor = context;
            }
            return hook;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) Wormholes.LOGGER.error("Couldn't install the boss hook into Showdown", e);
            warned = true;
            return null;
        }
    }
}
