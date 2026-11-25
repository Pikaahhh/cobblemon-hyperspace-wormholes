package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Objects;

/**
 * Shows the trip's task list on screen in Hyperspace; the Hoopa event glitches its characters (HoopaGlitch).
 *
 * <p>It sits in the top left, every line on one left edge: in the corner above Cobblemon's party icons, or at the top
 * beside them when a big GUI scale leaves too little room over the party. It stays at the top and never runs past the
 * screen's middle, so it never gets near the hotbar, and shrinks to fit, down to SMALLEST_SCALE.
 *
 * <p>Hidden with F1, the debug screen and Cobblemon battles, and for LINGER ticks after any of those so it doesn't
 * flash on the way into or out of a battle.
 */
public class TasksHud {
    private static final int BOX = 7;
    private static final int INDENT = BOX + 4;
    private static final int LINE = 11;
    private static final int GAP = 4;
    private static final int ENTRY = 20;
    /** Its usual scale (the corner always gets it when the list fits there at it); never past SMALLEST_SCALE. */
    private static final float SCALE = 0.9f, SMALLEST_SCALE = 0.5f;
    /** Left and top margin, and the gap kept round anything else. */
    private static final int LEFT = 4, TOP = 4, CLEAR = 4;
    /** Cobblemon's party icons (PartyOverlay): a slot's height, and where the column ends on the right (a 62 wide slot,
     *  the selected one drawn 6 further right). */
    private static final int PARTY_SLOT = 30, PARTY_RIGHT = 68;
    /** Kept clear of the bottom (chat box and hotbar row) beside the party. */
    private static final int BOTTOM_KEEP = 48;
    private static final int LINGER = 20;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int DARK = 0xFF555555;
    private static final int GREEN = 0xFF55FF55;
    private static final int GLITCH_STEP = 3;
    private static final String TITLE = "Current Hyperspace Tasks:", ALL_DONE = "All Tasks Complete! A Golden Pokeball can now be found.",
            MISSION_TITLE = "Hyperspace Task:";

    private static List<Tasks.Line> tasks = List.of();
    private static List<Tasks.Line> missions = List.of();
    private static int eaten, counted, total, widest, tall;
    private static float shown = SCALE;
    private static int atX, atY;
    private static long ticks, lastHidden = -1000;

    public static void register() {
        Packets.onClient(Tasks.Sync.TYPE, payload -> {
            tasks = payload.tasks();
            missions = payload.missions();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            tasks = List.of();
            missions = List.of();
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            ticks++;
            if (mc.player != null && hiddenNow(mc)) lastHidden = ticks;
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> render(event.getGuiGraphics(), event.getPartialTick()));
    }

    private static boolean hiddenNow(Minecraft mc) {
        return mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || CobblemonClient.INSTANCE.getBattle() != null
                || mc.screen instanceof BattleGUI;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (HoopaGlitch.hidden()) {
            glitch(graphics, shown);
            return;
        }
        if (tasks.isEmpty() && missions.isEmpty() || hiddenNow(mc) || ticks - lastHidden <= LINGER) return;
        Font font = mc.font;
        boolean all = !tasks.isEmpty() && tasks.stream().allMatch(Tasks.Line::done);
        measure(font, all);
        place(graphics.guiWidth(), graphics.guiHeight());
        eaten = HoopaGlitch.eaten(total, GLITCH_STEP);
        counted = 0;
        graphics.pose().pushPose();
        graphics.pose().translate(atX, atY, 0);
        graphics.pose().scale(shown, shown, 1f);
        int y = 0;
        if (!tasks.isEmpty()) {
            title(graphics, font, chars(TITLE).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), y);
            y += LINE;
            for (Tasks.Line line : tasks) {
                line(graphics, font, line, y, all || line.done());
                y += ENTRY;
            }
            if (all) {
                title(graphics, font, chars(ALL_DONE).withStyle(ChatFormatting.YELLOW), y);
                y += LINE;
            }
        }
        if (!missions.isEmpty()) {
            if (!tasks.isEmpty()) y += GAP;
            title(graphics, font, chars(MISSION_TITLE).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), y);
            y += LINE;
            for (Tasks.Line line : missions) {
                line(graphics, font, line, y, line.done());
                y += ENTRY;
            }
        }
        graphics.pose().popPose();
        glitch(graphics, shown);
        total = counted;
    }

    /** The list's size before scaling: widest (the longest line, a task with its box) and tall. */
    private static void measure(Font font, boolean all) {
        widest = 0;
        tall = 0;
        if (!tasks.isEmpty()) {
            widest = Math.max(widest, font.width(Component.literal(TITLE).withStyle(ChatFormatting.BOLD)));
            tall += LINE;
            for (Tasks.Line line : tasks) {
                widest = Math.max(widest, entryWidth(font, line));
                tall += ENTRY;
            }
            if (all) {
                widest = Math.max(widest, font.width(ALL_DONE));
                tall += LINE;
            }
        }
        if (!missions.isEmpty()) {
            if (!tasks.isEmpty()) tall += GAP;
            widest = Math.max(widest, font.width(Component.literal(MISSION_TITLE).withStyle(ChatFormatting.BOLD)));
            tall += LINE;
            for (Tasks.Line line : missions) {
                widest = Math.max(widest, entryWidth(font, line));
                tall += ENTRY;
            }
        }
    }

    /** atX, atY and shown: the top left corner over the party if it fits there at SCALE, else the corner or the top
     *  beside the party, whichever shows it bigger. */
    private static void place(int width, int height) {
        if (widest <= 0 || tall <= 0) return;
        // Both spots are at the top, so both can run up to the screen's middle: the hotbar is far below them.
        int right = width / 2 - CLEAR;
        List<Pokemon> party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
        // Cobblemon draws nothing for an empty party, else every slot (empty ones collapsed) down from the same top.
        boolean drawn = party.stream().anyMatch(Objects::nonNull);
        int partyTop = drawn ? height / 2 - party.size() * PARTY_SLOT / 2 - 10 - CLEAR : height - BOTTOM_KEEP;
        float corner = fit(partyTop - TOP, right - LEFT);
        int besideX = PARTY_RIGHT + CLEAR;
        float beside = fit(height - BOTTOM_KEEP - TOP, right - besideX);
        // Beside the party when that shows it bigger; never the corner below SMALLEST_SCALE, where it would run into the party.
        if (!drawn || corner >= SMALLEST_SCALE && (corner >= SCALE || corner >= beside))
            set(LEFT, TOP, Math.max(corner, SMALLEST_SCALE));
        else set(besideX, TOP, Math.max(beside, SMALLEST_SCALE));
    }

    /** The scale (at most SCALE) that fits the list in room tall and wide GUI pixels. */
    private static float fit(int room, int wide) {
        return Math.min(SCALE, Math.min(Math.max(0, room) / (float) tall, Math.max(0, wide) / (float) widest));
    }

    private static void set(int x, int y, float scale) {
        atX = x;
        atY = y;
        shown = scale;
    }

    private static void glitch(GuiGraphics graphics, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(atX, atY, 0);
        graphics.pose().scale(scale, scale, 1f);
        HoopaGlitch.draw(graphics, 0, 0, widest, tall);
        graphics.pose().popPose();
    }

    /** This text, with as much of it as the glitch has reached so far gone to glitch text. */
    private static MutableComponent chars(String text) {
        int gone = Math.min(eaten, text.length());
        eaten -= gone;
        counted += text.length();
        return HoopaEvent.scrambled(text, gone);
    }

    private static String text(Tasks.Line line) {
        return line.text() + (line.goal() > 1 ? " " + line.progress() + "/" + line.goal() : "");
    }

    private static String reward(Tasks.Line line) {
        return "Reward: " + line.reward();
    }

    private static int entryWidth(Font font, Tasks.Line line) {
        return INDENT + Math.max(font.width(text(line)), font.width(reward(line)));
    }

    private static void title(GuiGraphics graphics, Font font, MutableComponent text, int y) {
        graphics.drawString(font, text, 0, y, WHITE, true);
    }

    /** A task: its box, then its text with its reward under it. */
    private static void line(GuiGraphics graphics, Font font, Tasks.Line line, int y, boolean faded) {
        int left = 0;
        graphics.renderOutline(left, y, BOX, BOX, faded ? DARK : WHITE);
        if (line.done()) graphics.drawString(font, "✔", left, y - 1, GREEN, true);
        MutableComponent text = chars(text(line));
        text.withStyle(style -> style.withStrikethrough(line.done()));
        graphics.drawString(font, text, left + INDENT, y, faded ? DARK : WHITE, !faded);
        graphics.drawString(font, chars(reward(line)).withStyle(style -> style.withStrikethrough(line.done())), left + INDENT, y + LINE - 1, faded ? DARK : GREY, !faded);
    }
}
