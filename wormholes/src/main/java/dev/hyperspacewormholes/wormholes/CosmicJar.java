package dev.hyperspacewormholes.wormholes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Fills with cosmic energy from wormhole trips and the Item Analyser; once full, it calls a Cosmog with a golden apple.
 */
public final class CosmicJar extends Item {
    public static final int CAPACITY = 4;

    public static final DataComponentType<Integer> ENERGY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Wormholes.id("cosmic_energy"),
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, CAPACITY)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final CosmicJar ITEM = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("cosmic_jar"),
            new CosmicJar(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private CosmicJar(Properties properties) {
        super(properties);
    }

    public static ItemStack full() {
        ItemStack stack = new ItemStack(ITEM);
        stack.set(ENERGY, CAPACITY);
        return stack;
    }

    public static int energy(ItemStack stack) {
        return stack.is(ITEM) ? stack.getOrDefault(ENERGY, 0) : 0;
    }

    public static boolean isFull(ItemStack stack) {
        return energy(stack) >= CAPACITY;
    }

    public static void maybeCharge(ServerPlayer player) {
        if (player.getRandom().nextDouble() >= 0.1) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ITEM) || isFull(stack)) continue;
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
