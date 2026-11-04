package dev.hyperspacewormholes.wormholes.ride;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * No breaking or placing blocks in the Ultra Space dimensions, the way Hyperspace does it (NoBlockBreaking,
 * BlockItemMixin, BucketItemMixin): attacking a block fails on both sides, a break the server is asked for is refused,
 * and block items, buckets and items used on a block do nothing (UltraNoPlacingMixin, UltraNoBucketsMixin,
 * UltraNoUseOnMixin). Using blocks, right-clicking Pokemon, throwing Poke Balls, riding and boats still work, and
 * creative players are exempt.
 */
public final class UltraNoBuilding {
    private UltraNoBuilding() {}

    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                blocks(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !blocks(player, level));
    }

    public static boolean blocks(@Nullable Player player, Level level) {
        return UltraDimensions.isUltra(level) && (player == null || !player.isCreative());
    }
}
