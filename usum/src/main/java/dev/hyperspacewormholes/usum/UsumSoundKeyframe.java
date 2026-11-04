package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.molang.ExpressionLike;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.EntityProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityConditionalActionEffectKeyframe;
import com.cobblemon.mod.common.api.scheduling.SchedulingFunctionsKt;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Action effect keyframe {@value #TYPE}: one sound cue of the move's Ultra Sun sequence, the game's own sound effect from
 * the {@value UsumSounds#NAMESPACE} namespace, played "delay" seconds after the keyframe at every battler matching
 * "entityCondition", at "volume" and "pitch", and sent to players within HEARING blocks. It doesn't hold the animation,
 * and in a build without those sounds the stand-in sounds play instead (StandInKeyframe).
 * Example:
 * {"type": "horizons_movefx:usum_sound", "sound": "se.c003b", "delay": 0.2, "volume": 1.0, "pitch": 1.76,
 * "entityCondition": "q.entity.is_user == false"}.
 */
public class UsumSoundKeyframe implements ActionEffectKeyframe, EntityConditionalActionEffectKeyframe {
    public static final String TYPE = "horizons_movefx:usum_sound";
    private static final float HEARING = 48;

    public String sound = "";
    public float delay = 0;
    public float volume = 1;
    public float pitch = 1;
    public String entityCondition = "q.entity.is_user";

    private transient ExpressionLike condition;

    @Override
    public ExpressionLike getEntityCondition() {
        if (condition == null) condition = MoLangExtensionsKt.asExpressionLike(entityCondition);
        return condition;
    }

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        if (!UsumSounds.events().contains(sound)) return CompletableFuture.completedFuture(Unit.INSTANCE);
        List<Entity> at = new ArrayList<>();
        for (Object provider : context.getProviders()) {
            if (!(provider instanceof EntityProvider entities)) continue;
            boolean user = provider instanceof UsersProvider;
            for (Entity entity : entities.getEntities()) {
                if (!at.contains(entity) && test(context, entity, user)) at.add(entity);
            }
        }
        if (at.isEmpty()) return CompletableFuture.completedFuture(Unit.INSTANCE);
        SoundEvent event = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(UsumSounds.NAMESPACE, sound), HEARING);
        float vol = Mth.clamp(volume, 0, 4);
        float pit = Mth.clamp(pitch, 0.5f, 2f);
        Runnable playIt = () -> {
            for (Entity entity : at) {
                if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) continue;
                level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), event,
                        entity.getSoundSource(), vol, pit);
            }
        };
        if (delay > 0) {
            SchedulingFunctionsKt.delayedFuture(delay).thenRun(playIt);
        } else {
            playIt.run();
        }
        return CompletableFuture.completedFuture(Unit.INSTANCE);
    }
}
