package dev.hyperspacewormholes.usum;

import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.EntityProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The move-animation steps the signature-move animations' data can't do on its own, added to Cobblemon's action effect keyframe types: a camera shake, a wait for a projectile to cross the real user-to-target gap, a render-only model motion of the user or target, the real Ultra Sun move sounds and the stand-in sounds of a plain build.
 * Shake and model motion run with the battle on the server and hand the effect to players within {@value #RANGE} blocks of the battling Pokemon; the clients draw it (client.MoveFxClient, client.ModelMotion), the shake behind its own toggle in config/horizons_movefx.json. The assets use the {@value #NAMESPACE} namespace.
 */
public final class MoveFx implements ModInitializer {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final String NAMESPACE = "horizons_movefx";
    public static final double RANGE = 48;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(CameraShakePayload.TYPE, CameraShakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModelMotionPayload.TYPE, ModelMotionPayload.CODEC);
        ActionEffectKeyframe.Companion.getTypes().put(CameraShakeKeyframe.TYPE, CameraShakeKeyframe.class);
        ActionEffectKeyframe.Companion.getTypes().put(TravelKeyframe.TYPE, TravelKeyframe.class);
        ActionEffectKeyframe.Companion.getTypes().put(UsumSoundKeyframe.TYPE, UsumSoundKeyframe.class);
        ActionEffectKeyframe.Companion.getTypes().put(StandInKeyframe.TYPE, StandInKeyframe.class);
        ActionEffectKeyframe.Companion.getTypes().put(ModelMotionKeyframe.TYPE, ModelMotionKeyframe.class);
    }

    static List<Entity> battlers(ActionEffectContext context) {
        List<Entity> battlers = new ArrayList<>();
        for (Object provider : context.getProviders()) {
            if (!(provider instanceof EntityProvider entities)) continue;
            for (Entity entity : entities.getEntities()) {
                if (!battlers.contains(entity)) battlers.add(entity);
            }
        }
        return battlers;
    }

    static Vec3 centre(List<Entity> battlers) {
        Vec3 sum = Vec3.ZERO;
        for (Entity entity : battlers) sum = sum.add(entity.position());
        return sum.scale(1.0 / battlers.size());
    }

    static void sendNear(List<Entity> battlers, CustomPacketPayload payload) {
        if (battlers.isEmpty() || !(battlers.getFirst().level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            for (Entity entity : battlers) {
                if (entity.level() == level && player.distanceToSqr(entity) < RANGE * RANGE) {
                    ServerPlayNetworking.send(player, payload);
                    break;
                }
            }
        }
    }
}
