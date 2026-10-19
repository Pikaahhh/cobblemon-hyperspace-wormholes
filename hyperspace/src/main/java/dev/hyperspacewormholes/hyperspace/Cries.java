package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.Map;

/**
 * Plays a Pokemon's cry from our own sounds when its model doesn't have a cry animation, using the right form's cry if
 * there is one.
 */
public class Cries {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("658.bond", "battle-bond"),
            Map.entry("678.f", "female"),
            Map.entry("718.10-c", "10-power-construct"),
            Map.entry("718.50-c", "power-construct"),
            Map.entry("744.dusk", "own-tempo"),
            Map.entry("876.f", "female"),
            Map.entry("902.f", "female"),
            Map.entry("916.f", "female"),
            Map.entry("1017.wellspring-tera", "wellspring-mask"),
            Map.entry("1017.hearthflame-tera", "hearthflame-mask"),
            Map.entry("1017.cornerstone-tera", "cornerstone-mask"));

    public static void play(PokemonEntity entity) {
        ResourceLocation id = find(entity.getPokemon());
        if (id == null) return;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, SoundSource.NEUTRAL, 0.8f, 1f,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, entity.getX(), entity.getY(), entity.getZ(), false));
    }

    private static ResourceLocation find(Pokemon pokemon) {
        int dex = pokemon.getSpecies().getNationalPokedexNumber();
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "cry." + dex);
        String form = pokemon.getForm().getName().toLowerCase(Locale.ROOT)
                .replace(' ', '-').replace('_', '-').replace("'", "").replace("%", "");
        form = ALIASES.getOrDefault(dex + "." + form, form);
        String prefix = base.getPath() + "." + form;
        ResourceLocation partial = null;
        ResourceLocation shorter = null;
        boolean known = false;
        for (ResourceLocation sound : Minecraft.getInstance().getSoundManager().getAvailableSounds()) {
            if (!sound.getNamespace().equals(Hyperspace.MOD_ID)) continue;
            String path = sound.getPath();
            if (path.equals(prefix)) return sound;
            if (partial == null && path.startsWith(prefix + "-")) partial = sound;
            if (path.startsWith(base.getPath() + ".") && prefix.startsWith(path + "-")
                    && (shorter == null || path.length() > shorter.getPath().length())) shorter = sound;
            if (sound.equals(base)) known = true;
        }
        if (partial != null) return partial;
        if (shorter != null) return shorter;
        return known ? base : null;
    }
}
