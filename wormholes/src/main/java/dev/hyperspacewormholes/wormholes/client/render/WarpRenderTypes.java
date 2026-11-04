package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The render types the wormholes half draws with. additive is the pixel glow (texture times vertex colour added onto what's
 * behind, both sides, depth tested but writing none). BurnsSkyClient draws with plain position-colour types (SHADE,
 * SCREEN); BurnsSkyEffects uses the BURNS_SKY family, whose shader (shaders/core/burns_sky) never discards and premultiplies
 * each texel by its alpha: LIGHT, TAKE_AWAY (subtracts the texture), SOLID (writes depth) and the SCREEN_ variants over
 * everything.
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

    public static final RenderType SHADE = RenderType.create("hyperspace_wormholes_shade",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    public static final RenderType SCREEN = RenderType.create("hyperspace_wormholes_screen",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static ShaderInstance burnsSkyShader;
    private static final RenderStateShard.ShaderStateShard BURNS_SKY_SHADER =
            new RenderStateShard.ShaderStateShard(() -> burnsSkyShader);

    private static final RenderStateShard.TransparencyStateShard PREMULTIPLIED = new RenderStateShard.TransparencyStateShard(
            "hyperspace_wormholes_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
    private static final RenderStateShard.TransparencyStateShard TAKE_AWAY = new RenderStateShard.TransparencyStateShard(
            "hyperspace_wormholes_take_away", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_ADD);
            }, () -> {
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    public static final BurnsSky BURNS_SKY_LIGHT = new BurnsSky("light", PREMULTIPLIED,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE);
    public static final BurnsSky BURNS_SKY_SOLID = new BurnsSky("solid", PREMULTIPLIED,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_DEPTH_WRITE);
    public static final BurnsSky BURNS_SKY_TAKE_AWAY = new BurnsSky("take_away", TAKE_AWAY,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE);
    public static final BurnsSky BURNS_SKY_SCREEN_LIGHT = new BurnsSky("screen_light", PREMULTIPLIED,
            RenderStateShard.NO_DEPTH_TEST, RenderStateShard.COLOR_WRITE);
    public static final BurnsSky BURNS_SKY_SCREEN_TAKE_AWAY = new BurnsSky("screen_take_away", TAKE_AWAY,
            RenderStateShard.NO_DEPTH_TEST, RenderStateShard.COLOR_WRITE);

    private WarpRenderTypes() {}

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Wormholes.id("burns_sky"), DefaultVertexFormat.POSITION_TEX_COLOR, shader -> burnsSkyShader = shader));
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    /** One blend of the BURNS_SKY family; of(texture, repeat) is its render type for a texture and wrap mode. */
    public static final class BurnsSky {
        private final String name;
        private final RenderStateShard.TransparencyStateShard transparency;
        private final RenderStateShard.DepthTestStateShard depth;
        private final RenderStateShard.WriteMaskStateShard writes;
        private final Map<ResourceLocation, RenderType> repeating = new HashMap<>(), clamped = new HashMap<>();

        private BurnsSky(String name, RenderStateShard.TransparencyStateShard transparency,
                         RenderStateShard.DepthTestStateShard depth, RenderStateShard.WriteMaskStateShard writes) {
            this.name = name;
            this.transparency = transparency;
            this.depth = depth;
            this.writes = writes;
        }

        public RenderType of(ResourceLocation texture, boolean repeat) {
            return (repeat ? repeating : clamped).computeIfAbsent(texture, id ->
                    RenderType.create("hyperspace_wormholes_burns_sky_" + name,
                            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                            RenderType.CompositeState.builder()
                                    .setShaderState(BURNS_SKY_SHADER)
                                    .setTextureState(wrapped(id, repeat))
                                    .setTransparencyState(transparency)
                                    .setCullState(RenderStateShard.NO_CULL)
                                    .setDepthTestState(depth)
                                    .setWriteMaskState(writes)
                                    .createCompositeState(false)));
        }
    }

    // A texture's wrap mode lives on the texture, not on a sampler, so it's set before each draw.
    private static RenderStateShard.EmptyTextureStateShard wrapped(ResourceLocation id, boolean repeat) {
        return new RenderStateShard.EmptyTextureStateShard(() -> {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            texture.setFilter(false, false);
            texture.bind();
            int wrap = repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
            RenderSystem.setShaderTexture(0, id);
        }, () -> {});
    }
}
