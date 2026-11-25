package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holds the mount still through Cobblemon's own movement too during the ride and always poses it as flying. Also poses
 * the Ultra Space Wilds' hidden Pokemon (WildsShadows), which have no AI for Cobblemon to pose them by: Cliff World's
 * flying while it circles and swoops, the others standing (their idle) while they wait and walking while they run and
 * leap. Keeps the Megalopolis Necrozma (NecrozmaFight) and Pokemon met past a wormhole (WormholeDestinations.held) from
 * being pushed off their spot by anything bumping them.
 */
@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMoveMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pinMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (hyperspace_wormholes$held()) ci.cancel();
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 hyperspace_wormholes$straightUpAndDown(Vec3 movement) {
        return WormholeDestinations.held((PokemonEntity) (Object) this) ? new Vec3(0, movement.y, 0) : movement;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pinTravel(Vec3 input, CallbackInfo ci) {
        if (hyperspace_wormholes$held()) ci.cancel();
    }

    private boolean hyperspace_wormholes$held() {
        PokemonEntity self = (PokemonEntity) (Object) this;
        return UltraWarpRules.isPinned(self) || NecrozmaFight.isBoss(self);
    }

    @Inject(method = "getCurrentPoseType", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$pose(CallbackInfoReturnable<PoseType> cir) {
        PokemonEntity self = (PokemonEntity) (Object) this;
        if (UltraWarpRules.isPinned(self) || WildsShadows.isFlyingShadow(self)) {
            cir.setReturnValue(PoseType.FLY);
        } else if (WildsShadows.isShadow(self)) {
            boolean moving = Mth.square(self.getX() - self.xo) + Mth.square(self.getZ() - self.zo) > 1e-4;
            cir.setReturnValue(moving ? PoseType.WALK : PoseType.STAND);
        }
    }
}
