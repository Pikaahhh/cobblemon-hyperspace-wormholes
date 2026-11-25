package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.hyperspacewormholes.wormholes.lodestone.LodestoneBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * A named lodestone's name above it, drawn like a distortion's label (RiftRenderer) but turned to face you: fades in
 * while you look at the lodestone from within RANGE blocks, hidden behind blocks.
 */
public class LodestoneLabelRenderer implements BlockEntityRenderer<LodestoneBlockEntity> {
    private static final double RANGE = 7;
    private static final float ABOVE = 1.3f;
    private static final float FADE = 4;
    private static final int WIDTH = 200;
    private static final int BACKGROUND = 0x40;
    private static final int VIEW_DISTANCE = 32;

    private final Font font;

    public LodestoneLabelRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    private static boolean lookedAt(LodestoneBlockEntity lodestone, float partialTick) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null) return false;
        Vec3 eye = viewer.getEyePosition(partialTick);
        if (eye.distanceToSqr(Vec3.atCenterOf(lodestone.getBlockPos())) > (RANGE + 1) * (RANGE + 1)) return false;
        BlockHitResult hit = lodestone.getLevel().clip(new ClipContext(eye, eye.add(viewer.getViewVector(partialTick).scale(RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(lodestone.getBlockPos());
    }

    @Override
    public void render(LodestoneBlockEntity lodestone, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (lodestone.getLevel() == null || !lodestone.hasCustomName()) return;
        double tick = lodestone.getLevel().getGameTime() + (double) partialTick;
        lodestone.labelFade = Mth.clamp(lodestone.labelFade + (lookedAt(lodestone, partialTick) ? 1 : -1) * (float) (tick - lodestone.labelAt) / FADE, 0, 1);
        lodestone.labelAt = tick;
        if (lodestone.labelFade <= 0) return;
        List<FormattedCharSequence> lines = font.split(lodestone.getCustomName(), WIDTH);
        if (lines.isEmpty()) return;
        int width = 0;
        for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
        int height = lines.size() * (font.lineHeight + 1) - 1;
        pose.pushPose();
        pose.translate(0.5, ABOVE, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
        pose.scale(0.025f, -0.025f, 0.025f);
        pose.translate(1 - width / 2f, -height, 0);
        Matrix4f m = pose.last().pose();
        int background = Math.round(lodestone.labelFade * BACKGROUND) << 24;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        vc.addVertex(m, -1, -1, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, -1, height, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, width, height, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, width, -1, 0).setColor(background).setLight(LightTexture.FULL_BRIGHT);
        // Font turns a colour with almost no alpha opaque, which would flash at the ends of the fade
        int colour = Math.max(4, Math.round(lodestone.labelFade * 255)) << 24 | 0xFFFFFF;
        float y = 0;
        for (FormattedCharSequence line : lines) {
            font.drawInBatch(line, (width - font.width(line)) / 2f, y, colour, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            y += font.lineHeight + 1;
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
