package dev.hyperspacewormholes.wormholes.msd;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The in-memory pack on top of every resource manager, building Mega Showdown-derived assets at runtime from the
 * player's own files, with no copies shipped (MsdTransforms' results: burst models and glow, dimmed Ultra textures,
 * power-down particles, the Necrozma poser and Hoopa model, and the power-down effect as server data).
 * A missing or changed source skips its whole piece with one log line; a piece named by a shipped resolver also blanks
 * that resolver, since Cobblemon refuses to start on a resolver whose model doesn't load.
 */
public final class MsdDerivedPack implements PackResources {
    private static final Logger LOGGER = LoggerFactory.getLogger("hyperspace_wormholes");
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private final PackLocationInfo info = new PackLocationInfo("hyperspace_wormholes_msd", Component.literal("H&W Mega Showdown builds"),
            PackSource.BUILT_IN, Optional.empty());
    private final List<PackResources> below;
    private final Map<PackType, Map<String, IoSupplier<InputStream>>> files = new EnumMap<>(PackType.class);

    private MsdDerivedPack(List<PackResources> below) {
        this.below = below;
    }

    public static List<PackResources> above(List<PackResources> packs) {
        if (packs.isEmpty() || packs.stream().anyMatch(MsdDerivedPack.class::isInstance)) return packs;
        List<PackResources> all = new ArrayList<>(packs);
        all.add(new MsdDerivedPack(List.copyOf(packs)));
        return all;
    }

    private synchronized Map<String, IoSupplier<InputStream>> files(PackType type) {
        return files.computeIfAbsent(type, this::load);
    }

    private Map<String, IoSupplier<InputStream>> load(PackType type) {
        Map<String, IoSupplier<InputStream>> built = new HashMap<>();
        try {
            for (MsdTransforms.Piece piece : MsdTransforms.pieces()) {
                if (piece.outputs().get(0).path().startsWith(type.getDirectory() + "/")) load(type, piece, built);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Couldn't read H&W's Mega Showdown patches", e);
        }
        return built;
    }

    private void load(PackType type, MsdTransforms.Piece piece, Map<String, IoSupplier<InputStream>> built) {
        Map<String, byte[]> sources = new HashMap<>();
        for (MsdTransforms.Output output : piece.outputs()) {
            byte[] source = sources.containsKey(output.source()) ? sources.get(output.source()) : read(type, output.source());
            if (!MsdTransforms.intact(source, output.sha256())) {
                if (LOGGED.add(piece.name())) {
                    LOGGER.warn("Mega Showdown's {} {}, so H&W is skipping {}", output.source(), source == null ? "is missing" : "has changed", piece.name());
                }
                if (piece.skipFile() != null) built.put(piece.skipFile(), bytes(MsdTransforms.skipBytes(piece)));
                return;
            }
            sources.put(output.source(), source);
        }
        for (MsdTransforms.Output output : piece.outputs()) {
            built.put(output.path(), lazily(piece, output, sources.get(output.source())));
        }
    }

    private @Nullable byte[] read(PackType type, String path) {
        String[] parts = path.split("/", 3);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]);
        for (int i = below.size() - 1; i >= 0; i--) {
            IoSupplier<InputStream> found = below.get(i).getResource(type, id);
            if (found == null) continue;
            try (InputStream in = found.get()) {
                return in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static IoSupplier<InputStream> bytes(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    private static IoSupplier<InputStream> lazily(MsdTransforms.Piece piece, MsdTransforms.Output output, byte[] source) {
        Supplier<byte[]> built = Suppliers.memoize(() -> {
            try {
                return MsdTransforms.build(piece, output, source);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return () -> {
            try {
                return new ByteArrayInputStream(built.get());
            } catch (RuntimeException e) {
                throw new IOException("Couldn't build " + output.path(), e);
            }
        };
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return files(type).get(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        String prefix = type.getDirectory() + "/" + namespace + "/";
        files(type).forEach((file, supplier) -> {
            if (file.startsWith(prefix + path + "/")) {
                output.accept(ResourceLocation.fromNamespaceAndPath(namespace, file.substring(prefix.length())), supplier);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new HashSet<>();
        for (String file : files(type).keySet()) namespaces.add(file.split("/", 3)[1]);
        return namespaces;
    }

    @Override
    @Nullable
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return info;
    }

    @Override
    public void close() {
    }
}
