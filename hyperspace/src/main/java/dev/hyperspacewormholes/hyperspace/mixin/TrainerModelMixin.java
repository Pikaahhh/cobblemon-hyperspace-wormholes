package dev.hyperspacewormholes.hyperspace.mixin;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.hyperspace.SlimTrainers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Radical Cobblemon Trainers with slim arms when their skin is slim.
 */
@Mixin(LivingEntityRenderer.class)
public class TrainerModelMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    @Mutable
    protected M model;

    @Unique
    private M hyperspace_wormholes$wide;

    @Unique
    private M hyperspace_wormholes$slim;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    @SuppressWarnings("unchecked")
    private void hyperspace_wormholes$slimTrainers(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof TrainerMob)) return;
        if (hyperspace_wormholes$wide == null) {
            if (!(model instanceof PlayerModel)) return;
            hyperspace_wormholes$wide = model;
            hyperspace_wormholes$slim = (M) new PlayerModel<T>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        boolean slim = SlimTrainers.slim(((EntityRenderer<T>) (Object) this).getTextureLocation(entity));
        model = slim ? hyperspace_wormholes$slim : hyperspace_wormholes$wide;
    }
}
