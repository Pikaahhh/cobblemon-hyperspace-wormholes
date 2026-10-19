package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.hyperspacewormholes.wormholes.client.RoarClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Poses the legend's roar on top of Cobblemon's animation: head up, mouth open.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelRoarMixin {
    private static final float NECK_LIFT = 18f * Mth.DEG_TO_RAD;
    private static final float HEAD_LIFT = 10f * Mth.DEG_TO_RAD;
    private static final float JAW_DROP = 35f * Mth.DEG_TO_RAD;

    @Inject(method = "applyAnimations", at = @At("TAIL"))
    private void hyperspace_wormholes$roar(Entity entity, PosableState state, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity == null) return;
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float roar = RoarClient.roarAmount(entity, partialTick);
        if (roar <= 0f) return;
        Map<String, ModelPart> parts = ((PosableModel) (Object) this).getRelevantPartsByName();
        ModelPart neck = parts.get("neck");
        ModelPart head = parts.get("head");
        if (neck != null) neck.xRot -= NECK_LIFT * roar;
        if (head != null) head.xRot -= neck != null ? HEAD_LIFT * roar : (NECK_LIFT + HEAD_LIFT) * roar;
        ModelPart jaw = parts.get("jaw");
        if (jaw != null) jaw.xRot += JAW_DROP * roar;
        if (roar <= 0.3f) return;
        for (String name : new String[]{"mouth_open", "mouth_open2"}) {
            ModelPart mouth = parts.get(name);
            if (mouth == null) continue;
            mouth.visible = true;
            mouth.xScale = mouth.yScale = mouth.zScale = 1f;
        }
    }
}
