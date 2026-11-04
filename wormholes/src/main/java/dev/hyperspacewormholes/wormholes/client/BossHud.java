package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.render.RenderHelperKt;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import dev.hyperspacewormholes.wormholes.client.cinematic.UltraBurstCinematic;
import dev.hyperspacewormholes.wormholes.network.BossHudPayload;
import kotlin.Pair;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * The boss HP bar (BossHudPayload), styled on Cobblemon's battle tile HP bar. It takes the vanilla boss bar's place at
 * the top centre (182 wide), stands in for vanilla boss bars while it shows (BossBarMixin), and draws the boss's major
 * status and stat change boxes with it. Several bosses each get their own bar, stacked from the top; all of it hides
 * during boss transitions, cutscenes and F1.
 */
public final class BossHud {
    private static final int BAR_WIDTH = 182, BAR_HEIGHT = 8, BAR_TOP = 13, NAME_TOP = 3, LEVEL_GAP = 4, SIDE_ROOM = 156;
    private static final int GLYPH_HEIGHT = 7, MIN_WIDTH = 80;
    private static final int NAME_BASELINE = 7, STATUS_WIDTH = 37, STATUS_HEIGHT = 7, STATUS_SHOWN = 24, STATUS_GAP = 4;
    private static final Set<String> STATUSES = Set.of("par", "brn", "psn", "tox", "slp", "frz");
    private static final int OUTLINE = 0x2F2F2F, EMPTY = 0x676767, TEXT = 0xFFFFFF;
    private static final int BOX_HEIGHT = 11, BOX_GAP = 3, BOX_EDGE = 0x8D8D8D, BOX_FILL = 0x676767;
    private static final float MIN_SPEED = 0.15f, EASE_RATE = 2.5f, FADE_SPEED = 4f, MIN_ALPHA = 0.05f;
    private static final String[] STATS = {"atk", "def", "spa", "spd", "spe", "accuracy", "evasion"};
    private static final String[] STAT_LABELS = {"ATK", "DEF", "SPA", "SPD", "SPE", "ACC", "EVA"};
    private static final Style LARGE = Style.EMPTY
            .withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()).withBold(true);

    private static final int STACK_GAP = -2;

    private static final class Bar {
        BossHudPayload payload;
        boolean shown;
        float display, visibility;

        Bar(BossHudPayload payload) {
            this.payload = payload;
            this.display = Mth.clamp(payload.hp(), 0f, 1f);
        }
    }

    private static final Map<Integer, Bar> bars = new LinkedHashMap<>();

    private BossHud() {}

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void receive(BossHudPayload payload) {
        Bar bar = bars.get(payload.entityId());
        if (!payload.shown()) {
            if (bar != null) bar.shown = false;
            return;
        }
        if (bar == null) {
            bar = new Bar(payload);
            bars.put(payload.entityId(), bar);
        } else if (!bar.shown && bar.visibility <= 0f) {
            bar.display = Mth.clamp(payload.hp(), 0f, 1f);
        }
        bar.payload = payload;
        bar.shown = true;
    }

    public static boolean hidesOpponentTiles() {
        if (CobblemonClient.INSTANCE.getBattle() == null) return false;
        for (Bar bar : bars.values()) if (bar.shown) return true;
        return false;
    }

    private static void reset() {
        bars.clear();
    }

    public static boolean draw(GuiGraphics g, DeltaTracker delta) {
        if (bars.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        float dt = mc.isPaused() ? 0f : delta.getRealtimeDeltaTicks() / 20f;
        boolean battle = CobblemonClient.INSTANCE.getBattle() != null;
        boolean any = false;
        for (Iterator<Bar> it = bars.values().iterator(); it.hasNext(); ) {
            Bar bar = it.next();
            bar.visibility = Mth.clamp(bar.visibility + (bar.shown && battle ? dt : -dt) * FADE_SPEED, 0f, 1f);
            float target = Mth.clamp(bar.payload.hp(), 0f, 1f);
            float gap = target - bar.display;
            float step = Math.max(MIN_SPEED, Math.abs(gap) * EASE_RATE) * dt;
            bar.display = Math.abs(gap) <= step ? target : bar.display + Math.signum(gap) * step;
            if (bar.visibility <= 0f) {
                if (!bar.shown) it.remove();
                continue;
            }
            any = true;
        }
        if (!any) return false;
        if (mc.options.hideGui || BossTransition.hidesUi() || UltraBurstCinematic.isShowing() || Cinematics.isShowing()) {
            return true;
        }
        int scale = Math.max(1, (int) mc.getWindow().getGuiScale());
        int screenWidth = g.guiWidth();
        int width = Math.max(MIN_WIDTH, Math.min(BAR_WIDTH, screenWidth - 2 * SIDE_ROOM));
        int slant = (BAR_HEIGHT - 1) / 2;
        int left = (screenWidth - width - slant) / 2;
        float centre = left + (width + slant) / 2f;
        int top = 0;
        for (Bar bar : bars.values()) {
            if (bar.visibility <= 0f) continue;
            BossHudPayload p = bar.payload;
            int bottom = stagesBottom(mc.font, p, width + slant);
            if (bar.visibility >= MIN_ALPHA) {
                int alpha = Math.round(bar.visibility * 255f) << 24;
                g.pose().pushPose();
                g.pose().translate(0, top, 0);
                g.pose().pushPose();
                g.pose().scale(1f / scale, 1f / scale, 1f);
                bar(g, scale, left, width, alpha, p.hp() > 0f, bar.display);
                percentage(g, mc.font, scale, centre, alpha, p.hp() > 0f, bar.display);
                g.pose().popPose();
                title(g, mc.font, scale, p, centre, alpha);
                stages(g, mc.font, p, centre, width + slant, alpha);
                g.pose().popPose();
            }
            top += bottom + STACK_GAP;
        }
        return true;
    }

    private static void bar(GuiGraphics g, int s, int left, int width, int alpha, boolean alive, float display) {
        Pair<Float, Float> redGreen = RenderHelperKt.getDepletableRedGreen(display, 0.5f, 0.2f);
        int hp = alpha | channel(redGreen.getFirst() * 0.8f) << 16 | channel(redGreen.getSecond() * 0.8f) << 8 | channel(0.27f);
        int outline = alpha | OUTLINE, empty = alpha | EMPTY;
        for (int row = 0; row < BAR_HEIGHT; row++) {
            int x0 = (left + shift(row)) * s, x1 = x0 + width * s;
            int y0 = (BAR_TOP + row) * s, y1 = y0 + s;
            if (row == 0 || row == BAR_HEIGHT - 1) {
                g.fill(x0, y0, x1, y1, outline);
                continue;
            }
            int inner0 = x0 + s, inner1 = x1 - s;
            int filled = Math.round(display * (inner1 - inner0));
            if (alive) filled = Math.max(filled, 1);
            int split = inner0 + filled;
            g.fill(x0, y0, inner0, y1, outline);
            g.fill(inner1, y0, x1, y1, outline);
            if (split > inner0) g.fill(inner0, y0, split, y1, hp);
            if (split < inner1) g.fill(split, y0, inner1, y1, empty);
        }
    }

    private static void percentage(GuiGraphics g, Font font, int s, float centre, int alpha, boolean alive, float display) {
        int percent = Mth.clamp(Mth.ceil(display * 100f - 0.001f), 0, 100);
        if (alive) percent = Math.max(percent, 1);
        String text = percent + "%";
        int inside = (BAR_HEIGHT - 2) * s;
        int k = Math.max(1, Math.min(s - 1, inside / GLYPH_HEIGHT));
        int x = Math.round(centre * s / k - font.width(text) / 2f);
        int y = Math.round(((BAR_TOP + 1) * s + (inside - GLYPH_HEIGHT * k) / 2f) / k);
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        g.drawString(font, text, x, y, alpha | TEXT, true);
        g.pose().popPose();
    }

    private static void title(GuiGraphics g, Font font, int s, BossHudPayload p, float centre, int alpha) {
        Component name = (Language.getInstance().has(p.name()) ? Component.translatable(p.name()) : Component.literal(p.name()))
                .withStyle(LARGE);
        Component level = Component.translatable("cobblemon.ui.lv").append(String.valueOf(p.level()));
        String major = null;
        for (String part : (p.status() == null ? "" : p.status()).toLowerCase(Locale.ROOT).split(",")) {
            String id = part.trim();
            if (major == null && STATUSES.contains(id)) major = id;
        }
        int k = Math.max(1, s / 2);
        float small = k / (float) s;
        int nameWidth = font.width(name);
        float levelWidth = font.width(level) * small;
        float badgeWidth = major != null ? STATUS_GAP + STATUS_SHOWN : 0;
        float total = nameWidth + LEVEL_GAP + levelWidth + badgeWidth;
        int x = Math.round(centre - total / 2f);
        g.drawString(font, name, x, NAME_TOP, alpha | TEXT, true);
        float levelX = (x + nameWidth + LEVEL_GAP) * s / (float) k;
        float levelY = (NAME_TOP + NAME_BASELINE) * s / (float) k - GLYPH_HEIGHT;
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        g.pose().scale(k, k, 1f);
        g.drawString(font, level, Math.round(levelX), Math.round(levelY), alpha | TEXT, true);
        g.pose().popPose();
        if (major == null) return;
        int badgeX = Mth.ceil(x + nameWidth + LEVEL_GAP + levelWidth) + STATUS_GAP;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/battle/battle_status_" + major + ".png");
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, (alpha >>> 24) / 255f);
        g.blit(texture, badgeX, NAME_TOP + 1, STATUS_WIDTH, 0, STATUS_WIDTH, STATUS_HEIGHT, STATUS_WIDTH * 2, STATUS_HEIGHT);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        g.drawString(font, Component.translatable("cobblemon.ui.status." + major).withStyle(LARGE), badgeX + 3, NAME_TOP,
                alpha | TEXT, false);
    }

    private static List<Component> boxes(BossHudPayload p) {
        List<Component> boxes = new ArrayList<>();
        for (int i = 0; i < STATS.length; i++) {
            Integer stage = p.stages().get(STATS[i]);
            if (stage == null || stage == 0) continue;
            Component label = Component.translatableWithFallback("gui.battle.mega_showdown.stat." + STATS[i], STAT_LABELS[i]);
            MutableComponent text = Component.translatableWithFallback("gui.battle.mega_showdown.stat", "%s %s", label,
                    stage > 0 ? "+" + stage : String.valueOf(stage));
            boxes.add(text.setStyle(LARGE));
        }
        return boxes;
    }

    /** Where a bar's stage boxes end (the bar's own bottom when there are none). */
    private static int stagesBottom(Font font, BossHudPayload p, int room) {
        List<Component> boxes = boxes(p);
        int y = BAR_TOP + BAR_HEIGHT, from = 0;
        if (!boxes.isEmpty()) y += BOX_GAP + 1;
        while (from < boxes.size()) {
            int to = from, rowWidth = 1;
            while (to < boxes.size() && (to == from || rowWidth + boxWidth(font, boxes.get(to)) - 1 <= room)) {
                rowWidth += boxWidth(font, boxes.get(to)) - 1;
                to++;
            }
            from = to;
            y += BOX_HEIGHT - 1;
        }
        return y;
    }

    private static void stages(GuiGraphics g, Font font, BossHudPayload p, float centre, int room, int alpha) {
        List<Component> boxes = boxes(p);
        int y = BAR_TOP + BAR_HEIGHT + BOX_GAP;
        int from = 0;
        while (from < boxes.size()) {
            int to = from, rowWidth = 1;
            while (to < boxes.size() && (to == from || rowWidth + boxWidth(font, boxes.get(to)) - 1 <= room)) {
                rowWidth += boxWidth(font, boxes.get(to)) - 1;
                to++;
            }
            int x = Math.round(centre - rowWidth / 2f);
            for (int i = from; i < to; i++) x += box(g, font, boxes.get(i), x, y, alpha) - 1;
            from = to;
            y += BOX_HEIGHT - 1;
        }
    }

    private static int boxWidth(Font font, Component text) {
        return font.width(text) + 5;
    }

    private static int box(GuiGraphics g, Font font, Component text, int x, int y, int alpha) {
        int width = boxWidth(font, text);
        g.fill(x, y, x + width, y + BOX_HEIGHT, alpha | BOX_EDGE);
        g.fill(x + 2, y + 2, x + width - 2, y + BOX_HEIGHT - 2, alpha | BOX_FILL);
        g.renderOutline(x, y, width, BOX_HEIGHT, alpha | OUTLINE);
        g.drawString(font, text, x + 3, y + 1, alpha | TEXT, false);
        return width;
    }

    private static int shift(int row) {
        return (BAR_HEIGHT - 1 - row) / 2;
    }

    private static int channel(float value) {
        return Mth.clamp(Math.round(value * 255f), 0, 255);
    }
}
