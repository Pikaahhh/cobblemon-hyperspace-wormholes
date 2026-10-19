package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Works out if a trainer's skin has slim arms. Cached until resources reload.
 */
public class SlimTrainers {
    private static final Map<ResourceLocation, Boolean> slim = new HashMap<>();

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "slim_trainers");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                slim.clear();
            }
        });
    }

    public static boolean slim(ResourceLocation texture) {
        return slim.computeIfAbsent(texture, SlimTrainers::check);
    }

    private static boolean check(ResourceLocation texture) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return false;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int scale = image.getWidth() / 64;
            if (scale < 1 || image.getHeight() != image.getWidth()) return false;
            for (int x = 54 * scale; x < 56 * scale; x++) {
                for (int y = 20 * scale; y < 32 * scale; y++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) != 0) return false;
                }
            }
            return true;
        } catch (Exception e) {
            Hyperspace.LOGGER.warn("Couldn't read trainer skin {}", texture, e);
            return false;
        }
    }
}
