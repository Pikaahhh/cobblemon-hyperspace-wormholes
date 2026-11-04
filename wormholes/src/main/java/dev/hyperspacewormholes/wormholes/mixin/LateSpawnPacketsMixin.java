package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.LateSpawns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands entity data, riders and removals for an entity the client doesn't have yet to LateSpawns, so they still apply
 * when its spawn packet runs a moment later (on the game thread only: a packet first seen on the network thread is run
 * again there).
 */
@Mixin(ClientPacketListener.class)
public abstract class LateSpawnPacketsMixin {
    @Shadow
    private ClientLevel level;

    @Shadow
    public abstract void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket packet);

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void hyperspace_wormholes$keepData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (level.getEntity(packet.id()) == null) {
            LateSpawns.later(packet.id(), entity -> entity.getEntityData().assignValues(packet.packedItems()));
        }
    }

    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepRiders(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread() || level.getEntity(packet.getVehicle()) != null) return;
        LateSpawns.later(packet.getVehicle(), vehicle -> handleSetEntityPassengersPacket(packet));
        ci.cancel();
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void hyperspace_wormholes$keepRemovals(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        packet.getEntityIds().forEach(id -> {
            if (level.getEntity(id) == null) LateSpawns.removed(id);
        });
    }
}
