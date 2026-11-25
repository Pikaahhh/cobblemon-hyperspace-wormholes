package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.Wormholes;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the Ultra Space Wilds' Water World, water shallower than half a block (the path's waterlogged slabs, ankle deep as
 * in the game) doesn't count as water for players, so they walk and sprint through it at land speed without swimming.
 * The water is dropped from the entity's fluid heights too, since movement and jumping go by those.
 */
@Mixin(Entity.class)
public abstract class WildsShallowWaterMixin {
    private static final double SHALLOW = 0.5;

    @Shadow
    protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;

    @Inject(method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$wadeShallowWater(TagKey<Fluid> fluid, double motionScale, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (cir.getReturnValueZ() && fluid == FluidTags.WATER && self instanceof Player
                && self.level().dimension() == Wormholes.WILDS_WATER && self.getFluidHeight(FluidTags.WATER) < SHALLOW) {
            forgeFluidTypeHeight.removeDouble(NeoForgeMod.WATER_TYPE.value());
            cir.setReturnValue(false);
        }
    }
}
