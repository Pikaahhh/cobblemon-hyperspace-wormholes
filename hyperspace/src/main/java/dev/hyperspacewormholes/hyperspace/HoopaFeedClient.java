package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Plays HoopaFeed's motions on this client: the clip from hoopa_confined_hyperspace.animation.json goes on Hoopa as a
 * Cobblemon primary animation, fading in and out so it starts from and returns to the idle. If something else takes its
 * place it is put back at the time it had reached. While it plays, Hoopa's yaw, body yaw and head yaw are pinned every tick
 * to the yaw the server sends: a client only swings a standing mob's body round once its head is 50-75 degrees off, so
 * otherwise Hoopa would stay half facing the player through the throw.
 */
public class HoopaFeedClient {
    private static final float FADE_IN = 0.25f, FADE_OUT = 0.4f, TURN = 30f;
    private static final int LATE = 40;
    private static final Map<Integer, Run> runs = new HashMap<>();

    private static class Run {
        String motion;
        long start;
        PrimaryAnimation playing;
        float yaw;
        Float shown;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HoopaFeed.Play.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (payload.motion().equals(HoopaFeed.Play.FACE)) {
                Run run = runs.get(payload.entity());
                if (run != null) run.yaw = payload.yaw();
                return;
            }
            Run old = runs.remove(payload.entity());
            if (payload.motion().isEmpty()) {
                if (old != null && mc.level.getEntity(payload.entity()) instanceof PokemonEntity p
                        && p.getDelegate() instanceof PosableState state && state.getPrimaryAnimation() == old.playing) {
                    state.setPrimaryAnimation(null);
                }
                return;
            }
            Run run = new Run();
            run.motion = payload.motion();
            run.start = mc.level.getGameTime();
            run.yaw = payload.yaw();
            runs.put(payload.entity(), run);
            tick(mc);
        });
        ClientTickEvents.END_CLIENT_TICK.register(HoopaFeedClient::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            runs.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, Run>> it = runs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Run> entry = it.next();
            Run run = entry.getValue();
            float elapsed = (now - run.start) / 20f;
            // "group/name" plays from another animation file (Hoopa Unbound's), a bare name from Confined Hoopa's
            int slash = run.motion.indexOf('/');
            String group = slash < 0 ? HoopaFeed.GROUP : run.motion.substring(0, slash);
            String name = run.motion.substring(slash + 1);
            BedrockAnimation animation = BedrockAnimationRepository.INSTANCE.getAnimationOrNull(group, "animation." + group + "." + name);
            if (animation == null || elapsed >= animation.getAnimationLength() - (slash >= 0 && !name.startsWith("catch") ? 0 : 0.15)) {
                it.remove();
                continue;
            }
            if (!(mc.level.getEntity(entry.getKey()) instanceof PokemonEntity pokemon) || pokemon.isRemoved()
                    || !(pokemon.getDelegate() instanceof PosableState state)) {
                if (now - run.start > LATE) it.remove();
                continue;
            }
            face(pokemon, run);
            if (run.playing != null && state.getPrimaryAnimation() == run.playing) continue;
            float length = (float) animation.getAnimationLength();
            // Unbound's summon, hold and catch (and the boss fight's summon2/3, hold2/3, catch2/3) follow one another
            // pose to pose, so they only fade where they meet the idle: summon in, catch out (a fade between them would
            // dip toward the idle and back); descend and ascend never fade (they start and end far up in the sky)
            boolean chained = slash >= 0;
            float in = chained && !name.startsWith("summon") ? 0 : FADE_IN;
            float out = chained && !name.startsWith("catch") ? 0 : FADE_OUT;
            BedrockActiveAnimation active = new BedrockActiveAnimation(animation);
            PrimaryAnimation primary = new PrimaryAnimation(active,
                    t -> Mth.clamp(Math.min(in == 0 ? 1 : t * length / in, out == 0 ? 1 : (1 - t) * length / out), 0f, 1f), Set.of(), false);
            state.addPrimaryAnimation(primary);
            primary.setStarted(primary.getStarted() - elapsed);
            active.setStartedSeconds(active.getStartedSeconds() - elapsed);
            run.playing = primary;
        }
    }

    private static void face(PokemonEntity pokemon, Run run) {
        float before = run.shown == null ? pokemon.yBodyRot : run.shown;
        float now = Mth.approachDegrees(before, run.yaw, TURN);
        run.shown = now;
        pokemon.yRotO = before;
        pokemon.setYRot(now);
        pokemon.yBodyRotO = before;
        pokemon.setYBodyRot(now);
        pokemon.yHeadRotO = before;
        pokemon.setYHeadRot(now);
    }
}
