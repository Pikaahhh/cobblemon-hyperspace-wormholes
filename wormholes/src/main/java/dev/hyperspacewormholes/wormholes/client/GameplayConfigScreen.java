package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.GameplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * The Mod Menu page for GameplayConfig: one row per setting, grouped under headings, with Done saving to the same file
 * server owners edit by hand. A box left empty or not a number keeps the value it opened with. Hovering a name shows what
 * it does. Ring rarity is file only, and the griefing toggle sets all three of the file's Data Monitor switches, showing
 * Custom until it's pressed when the file has them mixed.
 */
public final class GameplayConfigScreen extends Screen {
    private static final String KEY = "config.hyperspace_wormholes.";
    private static final int ROW = 24, BOX_WIDTH = 64, ROW_WIDTH = 340, BUTTON_WIDTH = 100, FOOTER = 58, HEADER = 34, TIP_WIDTH = 240;

    private final Screen parent;
    private final List<Consumer<GameplayConfig>> reads = new ArrayList<>();
    private GameplayConfig shown;
    private Rows rows;

    public GameplayConfigScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        shown = copy(GameplayConfig.local());
    }

    private static GameplayConfig copy(GameplayConfig from) {
        GameplayConfig to = GameplayConfig.defaults();
        to.hoopaPity = from.hoopaPity;
        to.necrozmaPity = from.necrozmaPity;
        to.wormholeBaseShinyChance = from.wormholeBaseShinyChance;
        to.wormholeShinyMultiplierPerRing = from.wormholeShinyMultiplierPerRing;
        to.wormholeRingWeights = from.wormholeRingWeights.clone();
        to.sparklingPowerMultiplier = from.sparklingPowerMultiplier;
        to.alphaPowerMultiplier = from.alphaPowerMultiplier;
        to.humungoPowerMultiplier = from.humungoPowerMultiplier;
        to.teensyPowerMultiplier = from.teensyPowerMultiplier;
        to.itemPowerMultiplier = from.itemPowerMultiplier;
        to.bigHaulPowerMultiplier = from.bigHaulPowerMultiplier;
        to.encounterPowerMultiplier = from.encounterPowerMultiplier;
        to.catchingPowerMultiplier = from.catchingPowerMultiplier;
        to.maxDistortionsPerPlayer = from.maxDistortionsPerPlayer;
        to.distortionStarWeights = from.distortionStarWeights.clone();
        to.bossHardModeAlwaysOn = from.bossHardModeAlwaysOn;
        to.monitorBreaksFromLightning = from.monitorBreaksFromLightning;
        to.monitorBreaksFromElectricMoves = from.monitorBreaksFromElectricMoves;
        to.monitorBreaksFromOtherMoves = from.monitorBreaksFromOtherMoves;
        return to;
    }

    @Override
    protected void init() {
        reads.clear();
        rows = addRenderableWidget(new Rows(minecraft, width, height - HEADER - FOOTER, HEADER));
        GameplayConfig c = shown;
        heading("bosses", null);
        whole("hoopaPity", c.hoopaPity, value -> shown.hoopaPity = value);
        whole("necrozmaPity", c.necrozmaPity, value -> shown.necrozmaPity = value);
        toggle("hardMode", c.bossHardModeAlwaysOn, value -> shown.bossHardModeAlwaysOn = value);
        heading("wormholes", null);
        decimal("baseShiny", c.wormholeBaseShinyChance, value -> shown.wormholeBaseShinyChance = value);
        decimal("shinyPerRing", c.wormholeShinyMultiplierPerRing, value -> shown.wormholeShinyMultiplierPerRing = value);
        griefing(c);
        heading("hyperspace", null);
        whole("maxDistortions", c.maxDistortionsPerPlayer, value -> shown.maxDistortionsPerPlayer = value);
        heading("stars", tip("stars"));
        for (int i = 0; i < GameplayConfig.STARS; i++) {
            int star = i;
            whole(Component.translatable(KEY + "star", star + 1), null, c.distortionStarWeights[star], value -> shown.distortionStarWeights[star] = value);
        }
        heading("donuts", tip("donuts"));
        decimal("sparkling", c.sparklingPowerMultiplier, value -> shown.sparklingPowerMultiplier = value);
        decimal("alpha", c.alphaPowerMultiplier, value -> shown.alphaPowerMultiplier = value);
        decimal("humungo", c.humungoPowerMultiplier, value -> shown.humungoPowerMultiplier = value);
        decimal("teensy", c.teensyPowerMultiplier, value -> shown.teensyPowerMultiplier = value);
        decimal("item", c.itemPowerMultiplier, value -> shown.itemPowerMultiplier = value);
        decimal("bigHaul", c.bigHaulPowerMultiplier, value -> shown.bigHaulPowerMultiplier = value);
        decimal("encounter", c.encounterPowerMultiplier, value -> shown.encounterPowerMultiplier = value);
        decimal("catching", c.catchingPowerMultiplier, value -> shown.catchingPowerMultiplier = value);

        int y = height - FOOTER + 8, left = width / 2 - BUTTON_WIDTH * 3 / 2 - 4;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "reset"), b -> {
            shown = GameplayConfig.defaults();
            rebuildWidgets();
        }).bounds(left, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            for (Consumer<GameplayConfig> read : reads) read.accept(shown);
            GameplayConfig.save(shown, minecraft.getConnection() == null || minecraft.hasSingleplayerServer());
            onClose();
        }).bounds(left + (BUTTON_WIDTH + 4) * 2, y, BUTTON_WIDTH, 20).build());
    }

    private static Component tip(String name) {
        return Component.translatable(KEY + name + ".tip");
    }

    private void heading(String name, @Nullable Component tip) {
        rows.add(new Row(Component.translatable(KEY + name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD, ChatFormatting.UNDERLINE), null, tip));
    }

    private void whole(String name, int value, IntConsumer set) {
        whole(Component.translatable(KEY + name), tip(name), value, set);
    }

    private void whole(Component label, @Nullable Component tip, int value, IntConsumer set) {
        EditBox box = box(label, String.valueOf(value), "[0-9]*");
        reads.add(c -> {
            try {
                set.accept(Integer.parseInt(box.getValue()));
            } catch (NumberFormatException ignored) {
            }
        });
        rows.add(new Row(label, box, tip));
    }

    private void decimal(String name, double value, DoubleConsumer set) {
        Component label = Component.translatable(KEY + name);
        EditBox box = box(label, value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value), "[0-9]*[.]?[0-9]*");
        reads.add(c -> {
            try {
                set.accept(Double.parseDouble(box.getValue()));
            } catch (NumberFormatException ignored) {
            }
        });
        rows.add(new Row(label, box, tip(name)));
    }

    private EditBox box(Component label, String value, String allowed) {
        EditBox box = new EditBox(font, 0, 0, BOX_WIDTH, 18, label);
        box.setMaxLength(9);
        box.setValue(value);
        box.setFilter(text -> text.matches(allowed));
        return box;
    }

    private void toggle(String name, boolean value, Consumer<Boolean> set) {
        boolean[] on = {value};
        Button button = Button.builder(CommonComponents.optionStatus(value), b -> {
            on[0] = !on[0];
            b.setMessage(CommonComponents.optionStatus(on[0]));
        }).size(BOX_WIDTH, 18).build();
        reads.add(c -> set.accept(on[0]));
        rows.add(new Row(Component.translatable(KEY + name), button, tip(name)));
    }

    private void griefing(GameplayConfig c) {
        boolean same = c.monitorBreaksFromLightning == c.monitorBreaksFromElectricMoves && c.monitorBreaksFromElectricMoves == c.monitorBreaksFromOtherMoves;
        boolean[] on = {same && c.monitorBreaksFromLightning}, chosen = {same};
        Button button = Button.builder(same ? CommonComponents.optionStatus(on[0]) : Component.translatable(KEY + "custom"), b -> {
            on[0] = !on[0];
            chosen[0] = true;
            b.setMessage(CommonComponents.optionStatus(on[0]));
        }).size(BOX_WIDTH, 18).build();
        reads.add(config -> {
            if (!chosen[0]) return;
            config.monitorBreaksFromLightning = on[0];
            config.monitorBreaksFromElectricMoves = on[0];
            config.monitorBreaksFromOtherMoves = on[0];
        });
        rows.add(new Row(Component.translatable(KEY + "griefing"), button, tip("griefing")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(KEY + "server").withStyle(ChatFormatting.GRAY), width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Component label;
        private final AbstractWidget widget;
        private final Component tip;

        private Row(Component label, @Nullable AbstractWidget widget, @Nullable Component tip) {
            this.label = label;
            this.widget = widget;
            this.tip = tip;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, label, left, top + 6, 0xFFFFFF);
            if (tip != null && hovered && mouseX >= left && mouseX < left + font.width(label)) setTooltipForNextRenderPass(font.split(tip, TIP_WIDTH));
            if (widget == null) return;
            widget.setPosition(left + width - BOX_WIDTH, top + 1);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widget == null ? List.of() : List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widget == null ? List.of() : List.of(widget);
        }
    }

    private final class Rows extends ContainerObjectSelectionList<Row> {
        private Rows(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW);
        }

        private void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + ROW_WIDTH / 2 + 8;
        }
    }
}
