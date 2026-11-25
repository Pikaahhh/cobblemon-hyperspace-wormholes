package dev.hyperspacewormholes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Every payload the mod sends. The halves declare theirs while the mod is constructed and NeoForge collects them in
 * {@link RegisterPayloadHandlersEvent}. Client handlers are added separately by the client classes, so the dedicated
 * server never loads them.
 */
public final class Packets {
    private static final List<Channel<?>> CHANNELS = new ArrayList<>();
    private static final Map<CustomPacketPayload.Type<?>, Consumer<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();

    private Packets() {
    }

    /** A payload the server sends to players. */
    public static <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        CHANNELS.add(new Channel<>(type, codec, true, null));
    }

    /** A payload players send to the server, handled on the server thread. */
    public static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        CHANNELS.add(new Channel<>(type, codec, false, handler));
    }

    /** What the client does with a payload declared by {@link #toClient}; runs on the client thread. */
    public static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        CLIENT_HANDLERS.put(type, handler);
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (Channel<?> channel : CHANNELS) {
            channel.register(registrar);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handleOnClient(T payload) {
        Consumer<T> handler = (Consumer<T>) CLIENT_HANDLERS.get(payload.type());
        if (handler != null) {
            handler.accept(payload);
        }
    }

    private record Channel<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean toClient, BiConsumer<T, ServerPlayer> handler) {
        void register(PayloadRegistrar registrar) {
            if (toClient) {
                registrar.playToClient(type, codec, (payload, context) -> handleOnClient(payload));
            } else {
                registrar.playToServer(type, codec, (payload, context) -> handler.accept(payload, (ServerPlayer) context.player()));
            }
        }
    }
}
