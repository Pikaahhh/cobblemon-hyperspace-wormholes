package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientStorageManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import org.joml.Matrix4fStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import dev.hyperspacewormholes.hyperspace.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Client side of quick battles: aiming at a target with your crosshair, the four move tiles in the bottom right, the
 * hotbar keys that fire them, and the marker over whatever you're aiming at.
 */
public class QuickBattleTargeting {
    public static final KeyMapping TARGET = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hyperspace_wormholes.target",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.hyperspace_wormholes"));

    private static final ResourceLocation TILE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_move.png");
    private static final ResourceLocation TILE_OVERLAY = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_move_overlay.png");
    private static final int TILE_WIDTH = 92;
    private static final int TILE_HEIGHT = 24;
    private static final int NAME_GAP = 3;
    private static final int COLOURED_BOTTOM_END = 44;
    private static final int GAP = 14;
    private static final int ROW_GAP = 12;
    private static final int ICON_SIZE = 18;
    private static final int MARGIN = 8;
    private static final int REFRESH = 20;
    private static final double CONTACT_AT = 0.5;
    private static final double TARGET_RANGE = 8;
    private static final double CONE = Math.cos(Math.toRadians(20));
    private static final float MONITOR_MARKER = 0.8f;
    private static final int MONEY_CLEARANCE = 24;

    public static int target = -1;
    public static BlockPos monitor;
    private static Optional<UUID> lastSelected = Optional.empty();
    private static boolean aiming, holding, busy;
    private static Method moneyPosition;
    private static Object moneyConfig;
    private static int aimTicks;
    private static List<QuickBattles.MoveInfo> moves = List.of();
    private static final int[] cooldowns = new int[4];

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(QuickBattleTargeting::tick);
        ClientPlayNetworking.registerGlobalReceiver(QuickBattles.Moves.TYPE, (payload, context) -> {
            moves = payload.moves();
            busy = payload.busy();
            for (int slot = 0; slot < 4; slot++) cooldowns[slot] = slot < moves.size() ? moves.get(slot).cooldown() : 0;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            moves = List.of();
            busy = false;
        });
        HudRenderCallback.EVENT.register(QuickBattleTargeting::render);
        WorldRenderEvents.AFTER_ENTITIES.register(QuickBattleTargeting::renderTarget);
    }

    private static void tick(Minecraft mc) {
        for (int slot = 0; slot < 4; slot++) if (cooldowns[slot] > 0) cooldowns[slot]--;
        boolean was = aiming;
        boolean shared = TARGET.same(mc.options.keyPickItem);
        int presses = 0;
        while (TARGET.consumeClick()) presses++;
        if (shared) while (mc.options.keyPickItem.consumeClick()) presses++;
        boolean held = held(mc);
        // Only worked out when the key is in use, since finding your sent-out Pokemon searches 48 blocks around you.
        boolean ready = (presses > 0 || held && holding) && mc.player != null && mc.level != null && mc.screen == null
                && CobblemonClient.INSTANCE.getBattle() == null && commander(mc) != null;
        if (presses > 0) {
            boolean onBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
            holding = ready && (!onBlock || pick(mc) >= 0 || aimedMonitor(mc) != null);
            if (shared && !holding) ((MinecraftAccessor) mc).hyperspace_wormholes$pickBlock();
        }
        if (!held) holding = false;
        aiming = ready && held && holding;
        if (!aiming) {
            target = -1;
            monitor = null;
            moves = List.of();
            return;
        }
        Optional<UUID> chosen = selected();
        if (!was || ++aimTicks % REFRESH == 0 || !chosen.equals(lastSelected)) {
            lastSelected = chosen;
            aimTicks = 0;
            if (ClientPlayNetworking.canSend(QuickBattles.Aim.TYPE)) ClientPlayNetworking.send(new QuickBattles.Aim(chosen));
        }
        monitor = aimedMonitor(mc);
        target = monitor != null ? -1 : pick(mc);
        for (int slot = 0; slot < 4; slot++) {
            while (mc.options.keyHotbarSlots[slot].consumeClick()) {
                if ((target >= 0 || monitor != null) && usable(slot) && ClientPlayNetworking.canSend(QuickBattles.Payload.TYPE)) {
                    ClientPlayNetworking.send(monitor != null ? new QuickBattles.Strike(monitor, slot, contact(mc, slot), chosen, runs(mc))
                            : new QuickBattles.Payload(target, slot, contact(mc, slot), chosen, runs(mc)));
                    cooldowns[slot] = QuickBattles.cooldown(Moves.getByName(moves.get(slot).name()));
                    busy = true;
                }
            }
        }
    }

    private static int contact(Minecraft mc, int slot) {
        PokemonEntity pokemon = commander(mc);
        MoveTemplate move = Moves.getByName(moves.get(slot).name());
        if (pokemon == null || move == null) return -1;
        String species = pokemon.getPokemon().getSpecies().getResourceIdentifier().getPath();
        boolean physical = move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
        for (String name : QuickBattles.animations(move.getName(), physical)) {
            BedrockAnimation animation = BedrockAnimationRepository.INSTANCE.getAnimationOrNull(species, "animation." + species + "." + name);
            if (animation != null) return Math.max(1, (int) Math.round(animation.getAnimationLength() * 20 * CONTACT_AT));
        }
        return -1;
    }

    private static boolean runs(Minecraft mc) {
        PokemonEntity pokemon = commander(mc);
        if (pokemon == null || !(pokemon.getDelegate() instanceof PosableState state)) return false;
        try {
            PosableModel model = VaryingModelRepository.INSTANCE.getPoser(pokemon.getPokemon().getSpecies().getResourceIdentifier(), state);
            for (String pose : model.getPoses().keySet()) {
                String name = pose.toLowerCase(Locale.ROOT);
                if (!name.contains("ride") && (name.contains("run") || name.contains("sprint"))) return true;
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    private static boolean usable(int slot) {
        if (slot >= moves.size()) return false;
        MoveTemplate move = Moves.getByName(moves.get(slot).name());
        return !busy && move != null && QuickBattles.quickUsable(move) && moves.get(slot).pp() > 0 && cooldowns[slot] <= 0;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!aiming || target < 0 && monitor == null || moves.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int left = graphics.guiWidth() - MARGIN - 2 * TILE_WIDTH - GAP;
        int top = graphics.guiHeight() - MARGIN - 2 * TILE_HEIGHT - ROW_GAP - (moneyBottomRight() ? MONEY_CLEARANCE : 0);
        RenderSystem.enableBlend();
        for (int slot = 0; slot < Math.min(4, moves.size()); slot++) {
            QuickBattles.MoveInfo info = moves.get(slot);
            MoveTemplate move = Moves.getByName(info.name());
            if (move == null) continue;
            int x = left + slot % 2 * (TILE_WIDTH + GAP);
            int y = top + slot / 2 * (TILE_HEIGHT + ROW_GAP);
            boolean usable = usable(slot);
            float alpha = usable ? 1f : 0.5f;
            int hue = move.getElementalType().getHue();
            graphics.setColor((hue >> 16 & 255) / 255f, (hue >> 8 & 255) / 255f, (hue & 255) / 255f, alpha);
            graphics.blit(TILE, x, y, 0, usable ? 0 : TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT * 2);
            graphics.setColor(1f, 1f, 1f, alpha);
            graphics.blit(TILE_OVERLAY, x, y, 0, 0, TILE_WIDTH, TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT);
            graphics.setColor(1f, 1f, 1f, 1f);
            new TypeIcon(x - ICON_SIZE / 2, y + 3, move.getElementalType(), null, false, false, 15f, 7.5f, alpha).render(graphics);
            MutableComponent right = cooldowns[slot] > 0
                    ? Component.literal((cooldowns[slot] + 19) / 20 + "s").withStyle(ChatFormatting.RED)
                    : large(Component.literal(info.pp() + "/" + info.maxPp())).withStyle(s -> s.withBold(true));
            int room = TILE_WIDTH - 12 - 6;
            int second = Math.min(room - mc.font.width(right) - NAME_GAP, COLOURED_BOTTOM_END - 12);
            MutableComponent name = large(move.getDisplayName().copy()).withStyle(s -> s.withBold(true));
            MoveNames.draw(graphics, mc.font, name, x + 12, y + 2, room, second, 0xFFFFFF);
            graphics.drawString(mc.font, right, x + TILE_WIDTH - 6 - mc.font.width(right), y + 13, 0xFFFFFF, true);
            Component key = mc.options.keyHotbarSlots[slot].getTranslatedKeyMessage();
            graphics.drawString(mc.font, key, x - mc.font.width(key) / 2, y + 3 - mc.font.lineHeight, 0xFFFFFF, true);
        }
        RenderSystem.disableBlend();
    }

    private static MutableComponent large(MutableComponent text) {
        return text.withStyle(s -> s.withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
    }

    private static void renderTarget(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        Entity aimed = target < 0 || mc.level == null ? null : mc.level.getEntity(target);
        if (monitor == null && !(aimed instanceof PokemonEntity)) return;
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = context.camera().getPosition();
        Vec3 at = monitor != null ? Vec3.atCenterOf(monitor) : aimed.getPosition(partial).add(0, aimed.getBbHeight() / 2, 0);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        PoseStack pose = new PoseStack();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        float radius = monitor != null ? MONITOR_MARKER : Math.max(aimed.getBbWidth(), aimed.getBbHeight()) * 0.6f + 0.2f;
        FloatingBallRenderer.target(pose, buffers, mc.getEntityRenderDispatcher().cameraOrientation(), (float) at.distanceTo(cam), radius,
                mc.level.getGameTime() + partial);
        buffers.endBatch(RenderType.debugQuads());
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static boolean moneyBottomRight() {
        try {
            if (moneyPosition == null) {
                Class<?> client = Class.forName("fr.harmex.cobbledollars.common.client.CobbleDollarsClient");
                Object instance = client.getField("INSTANCE").get(null);
                Object config = client.getMethod("getConfig").invoke(instance);
                moneyPosition = config.getClass().getMethod("getOverlayPosition");
                moneyConfig = config;
            }
            return String.valueOf(moneyPosition.invoke(moneyConfig)).equalsIgnoreCase("BOTTOM_RIGHT");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    private static boolean held(Minecraft mc) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(TARGET);
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    private static Optional<UUID> selected() {
        ClientStorageManager storage = CobblemonClient.INSTANCE.getStorage();
        Pokemon pokemon = storage.getParty().get(storage.getSelectedSlot());
        return pokemon == null ? Optional.empty() : Optional.of(pokemon.getUuid());
    }

    private static PokemonEntity commander(Minecraft mc) {
        return QuickBattles.sentOut(mc.player, selected());
    }

    private static BlockPos aimedMonitor(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1f).scale(TARGET_RANGE));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && ItemAnalyser.isMonitor(mc.level.getBlockState(hit.getBlockPos())) ? hit.getBlockPos() : null;
    }
    private static int pick(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        int best = -1;
        double bestDot = CONE;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(TARGET_RANGE),
                e -> e instanceof FloatingBall ball && ball.belongsTo(mc.player) || QuickBattles.wild(e))) {
            Vec3 centre = entity.getBoundingBox().getCenter();
            Vec3 to = centre.subtract(eye);
            if (to.lengthSqr() > TARGET_RANGE * TARGET_RANGE) continue;
            double dot = to.normalize().dot(look);
            if (dot <= bestDot) continue;
            if (mc.level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            best = entity.getId();
            bestDot = dot;
        }
        return best;
    }
}
