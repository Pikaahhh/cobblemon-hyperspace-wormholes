package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * Gives a player CobbleDollars (through reflection so the mod stays optional) and shows how much on the action bar,
 * written the way CobbleDollars writes it. Does nothing without CobbleDollars.
 */
public class Money {
    public static void give(ServerPlayer player, long amount) {
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) return;
        BigInteger added = BigInteger.valueOf(amount);
        try {
            Class<?> money = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt");
            Method get = money.getMethod("getCobbleDollars", Player.class);
            Method set = money.getMethod("setCobbleDollars", Player.class, BigInteger.class);
            set.invoke(null, player, ((BigInteger) get.invoke(null, player)).add(added));
        } catch (ReflectiveOperationException e) {
            Hyperspace.LOGGER.error("Couldn't reach CobbleDollars to give {} {}", player.getName().getString(), amount, e);
            return;
        }
        player.displayClientMessage(Component.literal("+" + formatted(added)).withStyle(ChatFormatting.GOLD), true);
    }

    private static String formatted(BigInteger amount) {
        try {
            Method format = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.BigIntegerExtensionsKt")
                    .getMethod("formatFull", BigInteger.class, boolean.class);
            return ((Component) format.invoke(null, amount, true)).getString().replaceAll("\\s", "");
        } catch (ReflectiveOperationException | ClassCastException e) {
            return String.format("$%,d", amount);
        }
    }
}
