package dev.hyperspacewormholes.wormholes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Wormholes advancements tab (data/hyperspace_wormholes/advancement/wormholes/). One trigger, hyperspace_wormholes:wormholes,
 * whose conditions name an "event" fired through the static methods here; it is kept apart from HyperspaceAdvancements so
 * either half builds alone. It also decides what the tab shows: the line up to Ride Of Your Life always, the rest once
 * the one before is done, and hidden ones only once done themselves.
 */
public class WormholesAdvancements extends SimpleCriterionTrigger<WormholesAdvancements.Event> {
    public static final ResourceLocation ROOT = Wormholes.id("wormholes/root");
    // shown from the first join, before they're done
    private static final Set<ResourceLocation> FROM_START = Set.of(ROOT, Wormholes.id("wormholes/magical_summoner"),
            Wormholes.id("wormholes/cosmic_filling"), Wormholes.id("wormholes/sun_and_moon"),
            Wormholes.id("wormholes/ride_of_your_life"));
    public static final List<ResourceKey<Level>> ULTRA_WORLDS = List.of(Wormholes.ULTRA_WARP, Wormholes.ULTRA_DEEP_SEA,
            Wormholes.ULTRA_JUNGLE, Wormholes.ULTRA_PLANT, Wormholes.ULTRA_DESERT, Wormholes.ULTRA_CRATER,
            Wormholes.ULTRA_MEGALOPOLIS, Wormholes.ULTRA_RUIN, Wormholes.WILDS_CLIFF, Wormholes.WILDS_CRAG, Wormholes.WILDS_CAVE,
            Wormholes.WILDS_WATER);
    // rarity rings it takes for "Maximum Rings"; 3 is the game's top tier
    public static final int MAXIMUM_RINGS = 3;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Wormholes.MOD_ID);
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES = DeferredRegister.create(Registries.TRIGGER_TYPE, Wormholes.MOD_ID);

    public static final Supplier<AttachmentType<List<ResourceLocation>>> VISITED = ATTACHMENT_TYPES.register("ultra_worlds_visited",
            () -> AttachmentType.<List<ResourceLocation>>builder(() -> null).serialize(ResourceLocation.CODEC.listOf()).copyOnDeath().build());

    private static final WormholesAdvancements TRIGGER = new WormholesAdvancements();

    public record Event(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Event::player),
                Codec.STRING.fieldOf("event").forGetter(Event::event)
        ).apply(i, Event::new));
    }

    public static void register(IEventBus modBus) {
        TRIGGER_TYPES.register("wormholes", () -> TRIGGER);
        ATTACHMENT_TYPES.register(modBus);
        TRIGGER_TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> entered((ServerPlayer) event.getEntity(), event.getTo()));
    }

    @Override
    public Codec<Event> codec() {
        return Event.CODEC;
    }

    /** Makeshift Teleportation: an Item Analyser with a recipe loaded, powered by lightning or an Electric move. */
    public static void machine(ServerPlayer player) {
        fire(player, "machine");
    }

    /** Magical Summoner: a Pokemon pulled through by the Analyser's type gem + Blaze Powder recipe. */
    public static void summoned(ServerPlayer player) {
        fire(player, "summoned");
    }

    /** Cosmic Filling: a Cosmic Jar taking a charge (its first stage counts). */
    public static void jarCharged(ServerPlayer player) {
        fire(player, "jar");
    }

    /** Sun & Moon: the Analyser's full Cosmic Jar + golden apple recipe calling a Cosmog. */
    public static void cosmog(ServerPlayer player) {
        fire(player, "cosmog");
    }

    /** Ride Of Your Life: an Ultra Warp Ride starting for its rider. */
    public static void ride(ServerPlayer player) {
        fire(player, "ride");
    }

    /** Maximum Rings: going through a wormhole on a ride with at least MAXIMUM_RINGS rarity rings. */
    public static void wormholeEntered(ServerPlayer player, int rings) {
        if (rings >= MAXIMUM_RINGS) fire(player, "rings");
    }

    /** Light Recaptured: the Megalopolis Necrozma (Ultra Necrozma) beaten. */
    public static void ultraNecrozma(ServerPlayer player) {
        fire(player, "ultra_necrozma");
    }

    private static void entered(ServerPlayer player, ResourceKey<Level> dimension) {
        if (!ULTRA_WORLDS.contains(dimension)) return;
        List<ResourceLocation> visited = player.getExistingData(VISITED).orElse(List.of());
        ResourceLocation id = dimension.location();
        if (!visited.contains(id)) {
            List<ResourceLocation> more = new ArrayList<>(visited);
            more.add(id);
            visited = List.copyOf(more);
            player.setData(VISITED, visited);
        }
        for (ResourceKey<Level> world : ULTRA_WORLDS) {
            if (!visited.contains(world.location())) return;
        }
        fire(player, "ultra_worlds");
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
        boolean shown = done || !hidden && (parent == null || FROM_START.contains(node.holder().id()) || isDone.test(parent));
        output.accept(node, shown);
        return done;
    }
}
