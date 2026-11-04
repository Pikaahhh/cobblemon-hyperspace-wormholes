package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.CraterGiants;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the Ultra Crater's giant catchable Celesteela a hitbox round its body (CraterGiants.body), so a Poke Ball or a sent-out Pokemon thrown at it from the end of the walk starts the battle; Cobblemon's hitbox for Celesteela is a block wide and tall, a speck at the feet of the giant. The hitbox is remade whenever its synced aspects change, since on the client they can arrive after its size. */
@Mixin(PokemonEntity.class)
public abstract class CraterGiantHitboxMixin {
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$craterBody(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityDimensions body = CraterGiants.body((PokemonEntity) (Object) this);
        if (body != null) cir.setReturnValue(body);
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void hyperspace_wormholes$craterBodyAgain(EntityDataAccessor<?> data, CallbackInfo ci) {
        PokemonEntity self = (PokemonEntity) (Object) this;
        if (data.equals(PokemonEntity.getASPECTS()) && CraterGiants.showsFilling(self)) self.refreshDimensions();
    }
}
