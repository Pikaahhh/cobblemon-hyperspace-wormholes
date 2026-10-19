package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Pulls the third-person camera way back when riding Dawn Wings Necrozma, otherwise its wings fill the whole screen.
 */
@Mixin(Camera.class)
public abstract class DawnWingsCameraMixin {
    @Shadow
    public abstract Entity getEntity();

    @ModifyArg(method = "setup", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float hyperspace_wormholes$dawnWingsDistance(float distance) {
        if (Minecraft.getInstance().options.getCameraType() != CameraType.THIRD_PERSON_BACK) return distance;
        Entity entity = getEntity();
        if (entity == null || !(entity.getVehicle() instanceof PokemonEntity mount)) return distance;
        Pokemon pokemon = mount.getPokemon();
        boolean dawnWings = pokemon.getSpecies().getResourceIdentifier().getPath().equals("necrozma")
                && pokemon.getAspects().contains("dawn-fusion");
        return dawnWings ? Math.max(distance, 14f) : distance;
    }
}
