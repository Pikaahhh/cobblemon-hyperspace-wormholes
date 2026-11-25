package dev.hyperspacewormholes.wormholes.lodestone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where every named lodestone is and what it's called, so they can be listed without loading their chunks. The block
 * entities keep it in step, and an entry whose block turns out to be gone is dropped.
 */
public class LodestoneIndex extends SavedData {
    record Entry(GlobalPos at, String name) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.MAP_CODEC.forGetter(Entry::at),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)).apply(i, Entry::new));
    }

    private static final Codec<LodestoneIndex> CODEC = Entry.CODEC.listOf().fieldOf("lodestones").codec()
            .xmap(LodestoneIndex::new, LodestoneIndex::entries);

    private final Map<GlobalPos, String> names = new HashMap<>();

    private LodestoneIndex(List<Entry> entries) {
        entries.forEach(entry -> names.put(entry.at(), entry.name()));
    }

    static LodestoneIndex of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> new LodestoneIndex(List.of()),
                (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), null), Wormholes.MOD_ID + "_lodestones");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encode(this, NbtOps.INSTANCE, tag).getOrThrow();
    }

    private List<Entry> entries() {
        return names.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
    }

    void set(GlobalPos at, @Nullable Component name) {
        if (name == null) {
            remove(at);
        } else if (!name.getString().equals(names.put(at, name.getString()))) {
            setDirty();
        }
    }

    void remove(GlobalPos at) {
        if (names.remove(at) != null) setDirty();
    }

    /** Every entry in a dimension that exists, less those whose chunk is loaded without them in it. */
    List<Entry> existing(MinecraftServer server) {
        for (Entry entry : entries()) {
            ServerLevel level = server.getLevel(entry.at().dimension());
            if (level != null && level.isLoaded(entry.at().pos()) && Lodestones.named(level, entry.at().pos()) == null) {
                remove(entry.at());
            }
        }
        return entries().stream().filter(entry -> server.getLevel(entry.at().dimension()) != null).toList();
    }
}
