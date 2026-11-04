package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import dev.hyperspacewormholes.wormholes.client.BurnsSkyClient;
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
 * Poses the legend's roar on top of Cobblemon's animation (head up, mouth open), and Necrozma looking straight up with
 * its mouth open for Light That Burns the Sky when its own animation is missing, then tells BurnsSkyClient where the
 * posed head and mouth are so the ball grows in front of the mouth.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelRoarMixin {
    private static final float NECK_LIFT = 18f * Mth.DEG_TO_RAD;
    private static final float HEAD_LIFT = 10f * Mth.DEG_TO_RAD;
    private static final float JAW_DROP = 35f * Mth.DEG_TO_RAD;
    private static final float SKY_CHEST_LIFT = 15f * Mth.DEG_TO_RAD;
    private static final float SKY_NECK_LIFT = 40f * Mth.DEG_TO_RAD;
    private static final float SKY_HEAD_LIFT = 30f * Mth.DEG_TO_RAD;
    private static final String[] MOUTHS = {"mouth_open", "mouth_open2"};

    @Inject(method = "applyAnimations", at = @At("TAIL"))
    private void hyperspace_wormholes$roar(Entity entity, PosableState state, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity == null) return;
        PosableModel model = (PosableModel) (Object) this;
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float roar = RoarClient.roarAmount(entity, partialTick);
        float sky = BurnsSkyClient.headLift(entity, partialTick);
        float amount = Math.max(roar, sky);
        if (amount <= 0f) {
            BurnsSkyClient.trackMouth(entity, model, state);
            return;
        }
        Map<String, ModelPart> parts = model.getRelevantPartsByName();
        ModelPart neck = parts.get("neck");
        ModelPart head = parts.get("head");
        ModelPart chest = parts.get("torso_upper");
        float neckLift = Math.max(NECK_LIFT * roar, SKY_NECK_LIFT * sky);
        float headLift = Math.max(HEAD_LIFT * roar, SKY_HEAD_LIFT * sky);
        if (chest != null) chest.xRot -= SKY_CHEST_LIFT * sky;
        if (neck != null) neck.xRot -= neckLift;
        if (head != null) head.xRot -= neck != null ? headLift : neckLift + headLift;
        ModelPart jaw = parts.get("jaw");
        if (jaw != null) jaw.xRot += JAW_DROP * amount;
        if (amount > 0.3f) {
            for (String name : MOUTHS) {
                ModelPart mouth = parts.get(name);
                if (mouth == null) continue;
                mouth.visible = true;
                mouth.xScale = mouth.yScale = mouth.zScale = 1f;
            }
        }
        BurnsSkyClient.trackMouth(entity, model, state);
    }
}
