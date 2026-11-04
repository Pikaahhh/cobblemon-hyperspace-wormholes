package dev.hyperspacewormholes.hyperspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The Hyperspace advancements tab (data/hyperspace_wormholes/advancement/hyperspace/). One trigger, hyperspace_wormholes:hyperspace, whose conditions name an event (legendary, mega, shiny, hoopa, popped, golden, all_popped, tasks) that the rest of the mod fires through the static methods here.
 * It also decides what the tab shows: the root always, the rest only once the one before is done, hidden ones only once they're done themselves. Vanilla would show two steps ahead.
 */
public class HyperspaceAdvancements extends SimpleCriterionTrigger<HyperspaceAdvancements.Event> {
    public static final ResourceLocation ROOT = Hyperspace.id("hyperspace/root");
    private static final HyperspaceAdvancements TRIGGER = new HyperspaceAdvancements();

    public record Event(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Event::player),
                Codec.STRING.fieldOf("event").forGetter(Event::event)
        ).apply(i, Event::new));
    }

    public static void register() {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Hyperspace.id("hyperspace"), TRIGGER);
    }

    @Override
    public Codec<Event> codec() {
        return Event.CODEC;
    }

    public static void legendary(ServerPlayer player) {
        fire(player, "legendary");
    }

    public static void mega(ServerPlayer player) {
        fire(player, "mega");
    }

    public static void shiny(ServerPlayer player) {
        fire(player, "shiny");
    }

    public static void hoopa(ServerPlayer player) {
        fire(player, "hoopa");
    }

    public static void popped(ServerPlayer player) {
        fire(player, "popped");
    }

    public static void golden(ServerPlayer player) {
        fire(player, "golden");
    }

    public static void allPopped(ServerPlayer player) {
        fire(player, "all_popped");
    }

    public static void tasks(ServerPlayer player) {
        fire(player, "tasks");
    }

    private static void fire(ServerPlayer player, String event) {
        TRIGGER.trigger(player, conditions -> conditions.event().equals(event));
    }

    public static boolean ours(AdvancementNode root) {
        return root.holder().id().equals(ROOT);
    }

    public static boolean visibility(AdvancementNode node, Predicate<AdvancementNode> isDone, AdvancementVisibilityEvaluator.Output output) {
        boolean done = isDone.test(node);
        for (AdvancementNode child : node.children()) done |= visibility(child, isDone, output);
        AdvancementNode parent = node.parent();
        boolean hidden = node.advancement().display().map(DisplayInfo::isHidden).orElse(true);
        output.accept(node, done || parent == null || !hidden && isDone.test(parent));
        return done;
    }
}
