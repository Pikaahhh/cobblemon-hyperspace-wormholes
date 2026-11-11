package dev.hyperspacewormholes.wormholes.boss;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.exception.IllegalActionChoiceException;
import dev.hyperspacewormholes.wormholes.Wormholes;
import kotlin.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The boss's side in one trainer's battle: a wild actor over that battle's copy of the boss (whose entity is the one real
 * boss). It never chooses by itself when Showdown asks: it waits until Boss.round has every trainer's choice, then asks each
 * battle's actor for its plan (Cobblemon's strong battle AI, or the Rules' AI) and decides one: the boss's attack in one
 * battle, its ultimate, a move that hits every foe used in every battle (use), or a pass everywhere else. While a LegendWave
 * has taken the boss's place (wave) it chooses for the legendaries right away and has no entity of its own, or Cobblemon
 * would recall a fainted legendary as if this actor had sent it out. fitSlots gives a lopsided fight its one slot a side.
 */
public final class BossActor extends PokemonBattleActor {
    private final BattleAI ai;
    boolean waiting, wave, lopsided;
    int trainerSlots = 1;
    @Nullable BattleAI waveAi;

    BossActor(BattlePokemon pokemon, float fleeDistance, BattleAI ai) {
        super(pokemon.getUuid(), pokemon, fleeDistance, ai);
        this.ai = ai;
    }

    @Override
    public void onChoiceRequested() {
        if (!wave) {
            waiting = true;
            return;
        }
        ShowdownActionRequest request = getRequest();
        if (request == null || waveAi == null) return;
        List<ShowdownActionResponse> chosen;
        try {
            chosen = request.iterate(getActivePokemon(), (active, moveset, forceSwitch) -> waveAi.choose(active, getBattle(), getSide(), moveset, forceSwitch));
        } catch (RuntimeException e) {
            // its tracker doesn't know a legendary on its first turn. A pass is no answer for a Pokemon that can move, Showdown
            // would wait for this side for ever, and default settles every slot, so it has to be all of them.
            Wormholes.LOGGER.warn("[wave] the AI failed to choose: {}", e.toString());
            chosen = request.iterate(getActivePokemon(), (active, moveset, forceSwitch) -> new DefaultActionResponse());
        }
        setActionResponses(chosen);
    }

    /** None while the wave fights (FaintInstruction would recall the legendaries to it). With all of them down it is back:
     *  a trainer's replacement sent out then finds no slot to face and looks for an actor with an entity, and without
     *  one the send-out throws, the Pokemon stays in its ball and the battle waits forever. */
    @Override
    public @Nullable PokemonEntity getEntity() {
        if (wave) {
            for (ActiveBattlePokemon slot : getActivePokemon()) if (slot.getBattlePokemon() != null) return null;
        }
        return super.getEntity();
    }

    /** From the boss, here or not: without a position PokemonBattle.checkFlee would call the battle fled. */
    @Override
    public @Nullable Pair<ServerLevel, Vec3> getWorldAndPosition() {
        PokemonEntity boss = getPokemon().getEntity();
        return boss == null ? null : new Pair<>((ServerLevel) boss.level(), boss.position());
    }

    /** After Cobblemon makes the slots and before Showdown sends anything that names them (WaveSlotsMixin). */
    public static void fitSlots(PokemonBattle battle) {
        for (BattleActor actor : battle.getSide2().getActors()) {
            if (!(actor instanceof BossActor boss) || !boss.lopsided) continue;
            slots(boss, 1);
            for (BattleActor trainer : battle.getSide1().getActors()) slots(trainer, boss.trainerSlots);
        }
    }

    /** The Pokemon facing this one in a lopsided fight, or null in any other battle (BossSendOutMixin). */
    public static @Nullable ActiveBattlePokemon opposite(ActiveBattlePokemon active) {
        List<ActiveBattlePokemon> foes = active.getActor().getSide().getOppositeSide().getActivePokemon();
        for (BattleActor actor : active.getActor().getBattle().getSide2().getActors()) {
            if (!(actor instanceof BossActor boss) || !boss.lopsided || foes.isEmpty()) continue;
            for (ActiveBattlePokemon foe : foes) if (foe.getBattlePokemon() != null) return foe;
            return foes.getFirst();
        }
        return null;
    }

    private static void slots(BattleActor actor, int keep) {
        List<ActiveBattlePokemon> slots = actor.getActivePokemon();
        while (slots.size() > keep) slots.removeLast();
    }

    boolean ready() {
        ShowdownActionRequest request = getRequest();
        return waiting && request != null && !request.getWait() && request.getActive() != null && !request.getActive().isEmpty()
                && !getActivePokemon().isEmpty() && request.getForceSwitch().stream().noneMatch(b -> b);
    }

    @Nullable ShowdownMoveset moveset() {
        ShowdownActionRequest request = getRequest();
        return request == null || request.getActive() == null || request.getActive().isEmpty() ? null : request.getActive().getFirst();
    }

    @Nullable String foe() {
        List<ActiveBattlePokemon> foes = getSide().getOppositeSide().getActivePokemon();
        for (ActiveBattlePokemon foe : foes) {
            if (foe.getBattlePokemon() != null && foe.getBattlePokemon().getHealth() > 0) return foe.getPNX();
        }
        return foes.isEmpty() ? null : foes.getFirst().getPNX();
    }

    ShowdownActionResponse plan() {
        ShowdownMoveset moveset = moveset();
        if (moveset == null) return PassActionResponse.INSTANCE;
        ShowdownActionResponse chosen;
        try {
            chosen = ai.choose(getActivePokemon().getFirst(), getBattle(), getSide(), moveset, false);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.warn("[boss] the boss AI failed to choose, using its first usable move", e);
            chosen = null;
        }
        if (chosen instanceof MoveActionResponse move) {
            if (move.getGimmickID() != null) return new MoveActionResponse(move.getMoveName(), move.getTargetPnx(), null);
            return move;
        }
        for (InBattleMove move : moveset.getMoves()) {
            if (move.canBeUsed()) return new MoveActionResponse(move.getId(), foe(), null);
        }
        return chosen != null ? chosen : PassActionResponse.INSTANCE;
    }

    @Nullable ShowdownActionResponse ultimate(String move, boolean zMove) {
        ShowdownMoveset moveset = moveset();
        if (moveset == null) return null;
        List<InBattleMove> moves = moveset.getMoves();
        List<InBattleGimmickMove> zMoves = moveset.getCanZMove();
        for (int i = 0; i < moves.size(); i++) {
            if (!moves.get(i).getId().equals(move)) continue;
            if (zMove && zMoves != null && i < zMoves.size() && zMoves.get(i) != null && !zMoves.get(i).getDisabled()) {
                return new MoveActionResponse(move, foe(), ShowdownMoveset.Gimmick.Z_POWER.getId());
            }
            if (!zMove && moves.get(i).canBeUsed()) return new MoveActionResponse(move, foe(), null);
        }
        return null;
    }

    @Nullable ShowdownActionResponse use(String move) {
        ShowdownMoveset moveset = moveset();
        if (moveset == null) return null;
        for (InBattleMove known : moveset.getMoves()) {
            if (known.getId().equals(move) && known.canBeUsed()) return new MoveActionResponse(move, null, null);
        }
        return null;
    }

    void decide(ShowdownActionResponse response) {
        waiting = false;
        try {
            setActionResponses(List.of(response));
        } catch (IllegalActionChoiceException e) {
            Wormholes.LOGGER.warn("[boss] Showdown would refuse {}, passing instead", response);
            setActionResponses(List.of(PassActionResponse.INSTANCE));
        }
    }
}
