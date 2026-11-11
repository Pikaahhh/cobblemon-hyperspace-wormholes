package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.animations.MoveTileOffscreenAnimation;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import dev.hyperspacewormholes.wormholes.boss.BossBanPayload;
import dev.hyperspacewormholes.wormholes.boss.BossLinkPayload;
import dev.hyperspacewormholes.wormholes.boss.BossSlotsPayload;
import dev.hyperspacewormholes.wormholes.boss.BossWaitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's side of boss Pokemon (boss.Boss). Every trainer fights their own copy of the boss with its own uuid, and
 * the server says which entity draws each copy (BossLinkPayload); entity() answers that for client code that looks a
 * battler's entity up by uuid. waitHint is the line drawn under Cobblemon's battle hint while the locked battle menu
 * waits for other players.
 */
public final class BossClient {
    private static final Map<UUID, Integer> LINKS = new ConcurrentHashMap<>();
    private static volatile int waiting;

    private BossClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BossLinkPayload.TYPE, (payload, context) -> {
            if (payload.entityId() < 0) LINKS.remove(payload.pokemon());
            else LINKS.put(payload.pokemon(), payload.entityId());
        });
        ClientPlayNetworking.registerGlobalReceiver(BossWaitPayload.TYPE, (payload, context) -> {
            waiting = payload.state();
        });
        ClientPlayNetworking.registerGlobalReceiver(BossSlotsPayload.TYPE, (payload, context) -> wildSlots(payload.count()));
        // nothing on the client reads the bans, but the receiver stays registered so the server still sees the channel open
        ClientPlayNetworking.registerGlobalReceiver(BossBanPayload.TYPE, (payload, context) -> {});
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LINKS.clear();
            waiting = BossWaitPayload.NONE;
        });
    }

    public static @Nullable Entity entity(UUID battlePokemonUuid) {
        Integer id = LINKS.get(battlePokemonUuid);
        ClientLevel level = Minecraft.getInstance().level;
        return id == null || level == null ? null : level.getEntity(id);
    }

    public static @Nullable MutableComponent waitHint() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || battle.getMustChoose() || battle.getSpectating()) return null;
        return waiting == BossWaitPayload.PICKS
                ? Component.translatableWithFallback("hyperspace_wormholes.boss.waiting_picks", "Waiting for all moves to be selected…")
                : null;
    }

    /** A doubles battle that gives this side one slot: a boss fight with a wave (BossActor.fitSlots). */
    public static boolean lopsided(ActiveClientBattlePokemon own) {
        int slots = 0;
        for (ActiveClientBattlePokemon ignored : own.getSidePokemon()) slots++;
        int foes = own.getAllActivePokemon().size() - slots;
        return own.getFormat().getBattleType().getPokemonPerSide() > 1 && (slots == 1 || slots != foes);
    }

    /** Whether this slot still has a Pokemon: a faint sends its tile off, where it stays until the next switch. */
    public static boolean standing(ActiveClientBattlePokemon slot) {
        ClientBattlePokemon pokemon = slot.getBattlePokemon();
        return pokemon != null && pokemon.getHpValue() > 0
                && !(slot.getAnimations().size() == 1 && slot.getAnimations().peek() instanceof MoveTileOffscreenAnimation);
    }

    private static List<Targetable> standing(List<Targetable> targets) {
        return targets.stream().filter(target -> target instanceof ActiveClientBattlePokemon slot && standing(slot)).toList();
    }

    /** One foe left in a lopsided fight, so nothing for the target picker to ask (OneTargetMixin). */
    public static boolean nothingToPick(BattleTargetSelection picker) {
        ActiveClientBattlePokemon own = picker.getRequest().getActivePokemon();
        if (!lopsided(own)) return false;
        int foes = 0;
        for (ActiveClientBattlePokemon slot : own.getAllActivePokemon()) if (!slot.isAllied(own) && standing(slot)) foes++;
        List<Targetable> targets = picker.getSelectableTargetList();
        return foes == 1 && (targets == null || standing(targets).size() == 1);
    }

    public static @Nullable String onlyTarget(BattleTargetSelection picker) {
        List<Targetable> targets = picker.getSelectableTargetList();
        return targets == null ? null : standing(targets).getFirst().getPNX();
    }

    private static void wildSlots(int count) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        for (ClientBattleSide side : battle.getSides()) {
            for (ClientBattleActor actor : side.getActors()) {
                if (actor.getType() != ActorType.WILD) continue;
                List<ActiveClientBattlePokemon> active = actor.getActivePokemon();
                while (active.size() < count) active.add(new ActiveClientBattlePokemon(actor, null));
                while (active.size() > count) active.removeLast();
            }
        }
    }
}
