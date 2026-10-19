package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * The render types the wormholes half uses: additive pixel glow, plain coloured glow, see-through shade that writes
 * depth, a depth-only pass (so a light ball hides whatever's behind it) and flat silhouettes.
 */
public final class WarpRenderTypes {
    private static final RenderStateShard.ShaderStateShard POSITION_TEX_COLOR =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(texture ->
            RenderType.create("hyperspace_wormholes_additive",
                    DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_TEX_COLOR)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }
}
