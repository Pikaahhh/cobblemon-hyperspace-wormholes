package dev.hyperspacewormholes.hyperspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Hoopa Ring. Use it and a portal to Hyperspace opens in front of you, as long as there's solid ground and room.
 * Its name shows its stars.
 */
public class HoopaRingItem extends Item {
    public HoopaRingItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return DonutItem.enchanted(stack) || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.dimension().equals(Hyperspace.HYPERSPACE)) {
            return fail(player, stack, "You cant go to Hyperspace Hyperspace, that isn't a thing!");
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer) || !Trips.ready(serverPlayer.server)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!Portals.grounded(serverPlayer)) return fail(player, stack, "The portal needs solid ground to open on!");
        if (!Portals.fits(serverPlayer)) return fail(player, stack, "The portal needs more space to open!");
        if (!Portals.open(serverPlayer, stack)) return fail(player, stack, "Hyperspace is full right now, try again soon!");
        stack.shrink(1);
        return InteractionResultHolder.success(stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    private static InteractionResultHolder<ItemStack> fail(Player player, ItemStack stack, String message) {
        if (!player.level().isClientSide) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DonutBoosts.addLore(stack, tooltip);
    }

    @Override
    public Component getName(ItemStack stack) {
        int stars = DonutBoosts.stars(stack);
        if (stars == 0) return super.getName(stack);
        return super.getName(stack).copy().append(Component.literal(" " + DonutBoosts.starText(stars)).withStyle(ChatFormatting.GOLD));
    }
}
