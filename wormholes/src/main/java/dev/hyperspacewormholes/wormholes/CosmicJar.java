package dev.hyperspacewormholes.wormholes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Fills with cosmic energy from wormhole trips and the Item Analyser; once full, it calls a Cosmog with a golden apple.
 */
public final class CosmicJar extends Item {
    public static final int CAPACITY = 4;

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Wormholes.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wormholes.MOD_ID);

    public static final DataComponentType<Integer> ENERGY =
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, CAPACITY)).networkSynchronized(ByteBufCodecs.VAR_INT).build();
    public static final DeferredItem<CosmicJar> ITEM = ITEMS.register("cosmic_jar",
            () -> new CosmicJar(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        COMPONENTS.register("cosmic_energy", () -> ENERGY);
    }

    private CosmicJar(Properties properties) {
        super(properties);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
    }

    public static ItemStack full() {
        ItemStack stack = new ItemStack(ITEM.get());
        stack.set(ENERGY, CAPACITY);
        return stack;
    }

    public static int energy(ItemStack stack) {
        return stack.is(ITEM.get()) ? stack.getOrDefault(ENERGY, 0) : 0;
    }

    public static boolean isFull(ItemStack stack) {
        return energy(stack) >= CAPACITY;
    }

    public static void maybeCharge(ServerPlayer player) {
        if (player.getRandom().nextDouble() >= 0.1) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ITEM.get()) || isFull(stack)) continue;
            int energy = energy(stack) + 1;
            stack.set(ENERGY, energy);
            WormholesAdvancements.jarCharged(player);
            player.displayClientMessage(Component.translatable(energy >= CAPACITY
                    ? "hyperspace_wormholes.jar.full" : "hyperspace_wormholes.jar.charged", energy, CAPACITY)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.8f + energy * 0.15f);
            return;
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        int energy = energy(stack);
        if (energy <= 0) return Component.translatable("item.hyperspace_wormholes.cosmic_jar.empty");
        if (energy >= CAPACITY) return Component.translatable("item.hyperspace_wormholes.cosmic_jar.full");
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFull(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hyperspace_wormholes.cosmic_jar.energy", energy(stack), CAPACITY)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
