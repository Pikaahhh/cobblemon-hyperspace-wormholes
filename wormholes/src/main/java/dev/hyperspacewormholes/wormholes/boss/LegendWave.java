package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.network.BossHudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A wave of legendaries taking the boss's place in every trainer's battle against it, without ending the battle. Boss
 * fights with Rules.lopsided are doubles with one slot each, so callIn only grows the wild side: it puts a copy of each
 * legendary (drawn as the real one, Bosses.link) into the BossActor's slots with no Showdown switch, which would recall
 * the real entities, and ShowdownPool.callIn swaps them in. The legendaries are real wild Pokemon in the arena (no AI,
 * unhurtable, uncatchable) whose HP is one pool shared by every trainer's battle (stacked boss HP bars, BossHudPayload).
 * finish hands the battles back to the boss once every pool is empty.
 */
public final class LegendWave {
    /** Ticks after the last legendary faints to let the faints play; and the most to wait for them. */
    private static final int END_AFTER = 60, END_LIMIT = 900, AI_SKILL = 5;

    private final Boss boss;
    private final List<PokemonEntity> legends;
    private final List<String> pools = new ArrayList<>();
    private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();
    private final Map<UUID, List<BossHudPayload>> huds = new HashMap<>();
    private boolean closed;
    private int clearedTicks;

    private record Fighter(Boss.Trainer trainer, List<Pokemon> copies) {
    }

    public LegendWave(Boss boss, List<PokemonEntity> legends, int trainers) {
        this.boss = boss;
        this.legends = List.copyOf(legends);
        for (PokemonEntity legend : legends) {
            String id = "wave:" + legend.getUUID();
            int max = Math.max(1, legend.getPokemon().getStat(Stats.HP)) * Math.max(1, trainers);
            ShowdownPool.pool(id, max, max);
            ShowdownPool.wave(id);
            pools.add(id);
        }
        boss.away(this);
    }

    public List<PokemonEntity> legends() {
        return legends;
    }

    private void callIn(Boss.Trainer t) {
        if (closed || fighters.containsKey(t.player) || !t.live() || !t.battle.getStarted() || t.battle.getTurn() < 1 || Boss.busy(t)) return;
        ServerPlayer player = boss.online(t);
        if (player == null) return;
        String held = t.battle.getBattleId().toString();
        if (ShowdownPool.switching(held) || ShowdownPool.flush(held) > 0) return;
        List<Pokemon> copies = new ArrayList<>();
        List<BattlePokemon> wild = new ArrayList<>();
        for (int i = 0; i < legends.size(); i++) {
            PokemonEntity legend = legends.get(i);
            ShowdownPool.State state = ShowdownPool.read(pools.get(i));
            if (state != null && state.hp() <= 0) continue;
            Pokemon copy = legend.getPokemon().clone(true, player.registryAccess());
            PokemonProperties.Companion.parse("uncatchable").apply(copy);
            copy.setState(new SentOutState(legend));
            Bosses.link(copy, legend);
            ShowdownPool.join(copy.getUuid().toString(), pools.get(i));
            copies.add(copy);
            wild.add(new BattlePokemon(copy, copy, new ArrayList<>(), new ArrayList<>()));
        }
        if (wild.isEmpty()) return;
        String battleId = t.battle.getBattleId().toString();
        fighters.put(t.player, new Fighter(t, copies));
        BossActor actor = t.actor;
        actor.waveAi = new StrongBattleAI(AI_SKILL);
        actor.wave = true;
        actor.getPokemonList().clear();
        actor.getPokemonList().addAll(wild);
        List<ActiveBattlePokemon> slots = actor.getActivePokemon();
        while (slots.size() < wild.size()) slots.add(new ActiveBattlePokemon(actor, null));
        while (slots.size() > wild.size()) slots.removeLast();
        Boss.send(player, new BossSlotsPayload(wild.size()));
        for (int i = 0; i < wild.size(); i++) {
            wild.get(i).setActor(actor);
            place(t, slots.get(i), wild.get(i));
        }
        ShowdownPool.callIn(battleId, BattleRegistry.INSTANCE.packTeam(wild));
        String error = ShowdownPool.error(battleId);
        if (!error.isEmpty()) Wormholes.LOGGER.error("[wave] Showdown couldn't bring the legendaries in: {}", error);
    }

    private static void place(Boss.Trainer t, ActiveBattlePokemon slot, BattlePokemon pokemon) {
        slot.setBattlePokemon(pokemon);
        String pnx = slot.getPNX();
        t.battle.sendSidedUpdate(t.actor, new BattleSwitchPokemonPacket(pnx, pokemon, true, null),
                new BattleSwitchPokemonPacket(pnx, pokemon, false, null), false);
    }

    /** Brings the wave into every battle that hasn't got it, drops finished ones and keeps the HP bars current. */
    public void tick() {
        fighters.values().removeIf(f -> {
            if (f.trainer.live()) return false;
            for (Pokemon copy : f.copies) forget(copy);
            ServerPlayer player = boss.online(f.trainer);
            if (player != null) hideHud(player);
            return true;
        });
        boolean cleared = cleared();
        if (!cleared) {
            for (Boss.Trainer t : boss.trainers) callIn(t);
        } else if (++clearedTicks == 1) {
            for (Fighter f : fighters.values()) ShowdownPool.settle(f.trainer.battle.getBattleId().toString());
        }
        for (Fighter f : fighters.values()) {
            ServerPlayer player = boss.online(f.trainer);
            if (player != null) showHud(player);
        }
    }

    /** Sets every legendary still standing to this much of its HP (/boss setHP; 0 beats them). */
    void setHpFraction(double fraction) {
        for (String id : pools) {
            ShowdownPool.State state = ShowdownPool.read(id);
            if (state != null && state.hp() > 0) ShowdownPool.pool(id, (int) Math.round(fraction * state.max()), state.max());
        }
    }

    public boolean cleared() {
        for (String id : pools) {
            ShowdownPool.State state = ShowdownPool.read(id);
            if (state != null && state.hp() > 0) return false;
        }
        return true;
    }

    /** It is cleared and every battle has shown it. */
    public boolean settled() {
        if (!cleared() || clearedTicks < END_AFTER) return false;
        // a replacement still to choose would be lost in the hand back, and nobody can take the next turn without one
        for (Fighter f : fighters.values()) if (f.trainer.live() && ShowdownPool.switching(f.trainer.battle.getBattleId().toString())) return false;
        if (clearedTicks >= END_LIMIT) return true;
        for (Fighter f : fighters.values()) if (f.trainer.live() && Boss.busy(f.trainer)) return false;
        return true;
    }

    /** Hands every battle back to the boss, still between turns, and closes the wave. */
    public void finish() {
        for (Fighter f : fighters.values()) {
            Boss.Trainer t = f.trainer;
            if (!t.live()) continue;
            BossActor actor = t.actor;
            actor.wave = false;
            // an answer for the wave's slots that never went out, or the next dispatch reads it against one slot and throws
            actor.getResponses().clear();
            BattlePokemon own = actor.getPokemon();
            actor.getPokemonList().clear();
            actor.getPokemonList().add(own);
            List<ActiveBattlePokemon> slots = actor.getActivePokemon();
            while (slots.size() > 1) slots.removeLast();
            Boss.send(boss.online(t), new BossSlotsPayload(1));
            place(t, slots.getFirst(), own);
            ShowdownPool.sendBack(t.battle.getBattleId().toString());
        }
        close();
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (Fighter f : fighters.values()) {
            for (Pokemon copy : f.copies) forget(copy);
            ServerPlayer player = boss.online(f.trainer);
            if (player != null) hideHud(player);
        }
        fighters.clear();
        for (String id : pools) ShowdownPool.drop(id);
        boss.away(null);
    }

    private BossHudPayload hud(int i, boolean shown) {
        PokemonEntity legend = legends.get(i);
        ShowdownPool.State state = ShowdownPool.read(pools.get(i));
        Map<String, Integer> stages = new HashMap<>();
        if (state != null) {
            int[] now = state.stages();
            for (int s = 0; s < now.length && s < BossDef.STATS.size(); s++) if (now[s] != 0) stages.put(BossDef.STATS.get(s), now[s]);
        }
        float hp = state == null ? 0f : state.hp() / (float) Math.max(1, state.max());
        Component species = legend.getPokemon().getSpecies().getTranslatedName();
        String name = species.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : species.getString();
        return new BossHudPayload(legend.getId(), name, legend.getPokemon().getLevel(), hp, stages, shown, "");
    }

    private void showHud(ServerPlayer player) {
        List<BossHudPayload> last = huds.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        for (int i = 0; i < legends.size(); i++) {
            BossHudPayload now = hud(i, true);
            if (i < last.size() && now.equals(last.get(i))) continue;
            ServerPlayNetworking.send(player, now);
            if (i < last.size()) last.set(i, now);
            else last.add(now);
        }
    }

    private void hideHud(ServerPlayer player) {
        if (huds.remove(player.getUUID()) == null) return;
        for (int i = 0; i < legends.size(); i++) ServerPlayNetworking.send(player, hud(i, false));
    }

    private static void forget(Pokemon copy) {
        ShowdownPool.leave(copy.getUuid().toString());
        Bosses.forget(copy);
    }
}
