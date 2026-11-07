package dev.hyperspacewormholes.hyperspace;

import dev.hyperspacewormholes.wormholes.GameplayConfig;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Distortion Splicer, the way into Hyperspace without a Hoopa. Used at a spatial distortion (anywhere else it points
 * you to the nearest one) it's used up and splices the distortion open: a flickering portal to a Hyperspace of the
 * distortion's rank and types, with none of a donut's powers. The trip lasts a hidden MIN_SECONDS to MAX_SECONDS, shown as
 * "???"; being pulled out when it runs out (collapse) costs HEALTH_LOST of your health and ARMOUR_LOST of every worn armour
 * piece's durability, and walking out is safe. A splice has a HOOPA_CHANCE to be the Hoopa Unbound event (HoopaEvent),
 * guaranteed after GameplayConfig.hoopaPity splices in a row without it unless the player has beaten Hoopa. It breaks after USES splices.
 */
public class DistortionSplicer extends Item {
    public static final int MIN_SECONDS = 90;
    public static final int MAX_SECONDS = 300;
    public static final float HEALTH_LOST = 0.5f;
    public static final float ARMOUR_LOST = 0.35f;
    public static final float HOOPA_CHANCE = 0.05f;
    public static final int USES = 4;
    private static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    public DistortionSplicer(Properties properties) {
        super(properties.durability(USES).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.dimension().equals(Hyperspace.HYPERSPACE)) {
            if (player instanceof ServerPlayer serverPlayer && HoopaEvent.splice(serverPlayer)) {
                stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                return InteractionResultHolder.success(stack);
            }
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("You cant go to Hyperspace Hyperspace, that isn't a thing!").withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer) || !Trips.ready(serverPlayer.server)) {
            return InteractionResultHolder.fail(stack);
        }
        Distortions.Distortion here = Distortions.near(serverPlayer);
        if (here == null) {
            Distortions.noneNearby(serverPlayer);
            return InteractionResultHolder.fail(stack);
        }
        if (!Distortions.splice(serverPlayer, here, false)) return InteractionResultHolder.fail(stack);
        stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.splicer").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.splicer.unstable").withStyle(ChatFormatting.RED));
    }

    static int seconds(RandomSource random) {
        return MIN_SECONDS + random.nextInt(MAX_SECONDS - MIN_SECONDS + 1);
    }

    static boolean rollHoopa(ServerPlayer player, boolean forced) {
        if (forced) return true;
        int needed = GameplayConfig.get().hoopaPity;
        boolean pity = needed > 0 && !player.getAttachedOrElse(Wormholes.HOOPA_BEATEN, false)
                && player.getAttachedOrElse(Wormholes.SPLICES_WITHOUT_HOOPA, 0) >= needed;
        return pity || player.getRandom().nextFloat() < HOOPA_CHANCE;
    }

    static void counted(ServerPlayer player, boolean hoopa) {
        player.setAttached(Wormholes.SPLICES_WITHOUT_HOOPA, hoopa ? 0 : player.getAttachedOrElse(Wormholes.SPLICES_WITHOUT_HOOPA, 0) + 1);
    }

    static void collapse(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (player.getAbilities().invulnerable) return;
        player.setHealth(Math.max(1f, player.getHealth() - player.getMaxHealth() * HEALTH_LOST));
        player.level().broadcastDamageEvent(player, player.damageSources().magic());
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack armour = player.getItemBySlot(slot);
            if (armour.isDamageableItem()) armour.hurtAndBreak(Mth.ceil(armour.getMaxDamage() * ARMOUR_LOST), player, slot);
        }
    }
}
