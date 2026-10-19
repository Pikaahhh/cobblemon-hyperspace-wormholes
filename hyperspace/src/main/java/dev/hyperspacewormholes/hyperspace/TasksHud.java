package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Shows the trip's task list on screen in Hyperspace. Hidden during battles.
 */
public class TasksHud {
    private static final int LEFT = 4;
    private static final int TOP = 14;
    private static final int BOX = 7;
    private static final int INDENT = BOX + 4;
    private static final int LINE = 11;
    private static final int GAP = 4;
    private static final float SCALE = 0.9f;
    private static final int ENTRY = 20;
    private static final int PARTY_SLOT = 30;
    private static final int PARTY_GAP = 4;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int DARK = 0xFF555555;
    private static final int GREEN = 0xFF55FF55;

    private static List<Tasks.Line> tasks = List.of();
    private static List<Tasks.Line> missions = List.of();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Tasks.Sync.TYPE, (payload, context) -> {
            tasks = payload.tasks();
            missions = payload.missions();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tasks = List.of();
            missions = List.of();
        });
        HudRenderCallback.EVENT.register(TasksHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (tasks.isEmpty() && missions.isEmpty() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || CobblemonClient.INSTANCE.getBattle() != null) return;
        Font font = mc.font;
        boolean all = !tasks.isEmpty() && tasks.stream().allMatch(Tasks.Line::done);
        int height = (tasks.isEmpty() ? 0 : LINE + tasks.size() * ENTRY + LINE)
                + (missions.isEmpty() ? 0 : (tasks.isEmpty() ? 0 : GAP) + LINE + missions.size() * ENTRY);
        float scale = Math.min(SCALE, (float) (partyTop(graphics) - PARTY_GAP - TOP) / height);
        graphics.pose().pushPose();
        graphics.pose().translate(LEFT, TOP, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-LEFT, -TOP, 0);
        int y = TOP;
        if (!tasks.isEmpty()) {
            graphics.drawString(font, Component.literal("Current Hyperspace Tasks:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), LEFT, y, WHITE, true);
            y += LINE;
            for (Tasks.Line line : tasks) {
                line(graphics, font, line, y, all || line.done());
                y += ENTRY;
            }
            if (all) {
                graphics.drawString(font, Component.literal("All Tasks Complete! A Golden Pokeball can now be found.").withStyle(ChatFormatting.YELLOW), LEFT, y, WHITE, true);
                y += LINE;
            }
        }
        if (!missions.isEmpty()) {
            if (!tasks.isEmpty()) y += GAP;
            graphics.drawString(font, Component.literal("Hyperspace Task:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), LEFT, y, WHITE, true);
            y += LINE;
            for (Tasks.Line line : missions) {
                line(graphics, font, line, y, line.done());
                y += ENTRY;
            }
        }
        graphics.pose().popPose();
    }

    private static int partyTop(GuiGraphics graphics) {
        int slots = CobblemonClient.INSTANCE.getStorage().getParty().getSlots().size();
        return graphics.guiHeight() / 2 - slots * PARTY_SLOT / 2 - 10;
    }

    private static void line(GuiGraphics graphics, Font font, Tasks.Line line, int y, boolean faded) {
        graphics.renderOutline(LEFT, y, BOX, BOX, faded ? DARK : WHITE);
        if (line.done()) graphics.drawString(font, "✔", LEFT, y - 1, GREEN, true);
        MutableComponent text = Component.literal(line.text());
        if (line.goal() > 1) text.append(" " + line.progress() + "/" + line.goal());
        text.withStyle(style -> style.withStrikethrough(line.done()));
        graphics.drawString(font, text, LEFT + INDENT, y, faded ? DARK : WHITE, !faded);
        graphics.drawString(font, Component.literal("Reward: " + line.reward()).withStyle(style -> style.withStrikethrough(line.done())), LEFT + INDENT, y + LINE - 1, faded ? DARK : GREY, !faded);
    }
}
