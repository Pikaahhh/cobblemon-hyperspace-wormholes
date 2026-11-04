package dev.hyperspacewormholes.wormholes.msd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.jetbrains.annotations.Nullable;

/**
 * What H&W changes in Mega Showdown's files, built at runtime so no copies are shipped. The jar holds only
 * msd_patches/: our settings, the core overlay and the rule for the Ultra glow; everything else comes from the
 * player's own Mega Showdown, read by {@link MsdDerivedPack}. Each output names its source and that source's
 * SHA-256, so a Mega Showdown update that changes a file skips that piece (the pack logs it) instead of patching
 * something else. No Minecraft classes here, so a plain JVM can run these against the stock jar.
 */
public final class MsdTransforms {
    private static final String PATCHES = "/msd_patches/";
    private static final String GRADIENT = "/particle_effect/components/minecraft:particle_appearance_tinting/color/gradient/";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static @Nullable JsonObject manifest;

    private MsdTransforms() {}

    public record Output(String path, String source, String sha256, JsonObject spec) {}

    /** All-or-nothing: a skipped piece leaves skipFile (a shipped resolver that names the outputs) as skipJson. */
    public record Piece(String name, @Nullable String skipFile, @Nullable JsonElement skipJson, @Nullable JsonObject text, List<Output> outputs) {}

    public static synchronized List<Piece> pieces() throws IOException {
        if (manifest == null) {
            manifest = JsonParser.parseString(new String(patch("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<Piece> pieces = new ArrayList<>();
        for (JsonElement entry : manifest.getAsJsonArray("pieces")) {
            JsonObject piece = entry.getAsJsonObject();
            List<Output> outputs = new ArrayList<>();
            for (JsonElement output : piece.getAsJsonArray("outputs")) {
                JsonObject spec = output.getAsJsonObject();
                outputs.add(new Output(spec.get("path").getAsString(), spec.get("source").getAsString(), spec.get("sha256").getAsString(), spec));
            }
            pieces.add(new Piece(piece.get("name").getAsString(), piece.has("skip_file") ? piece.get("skip_file").getAsString() : null,
                    piece.get("skip_json"), piece.getAsJsonObject("text"), outputs));
        }
        return pieces;
    }

    public static boolean intact(@Nullable byte[] source, String sha256) {
        if (source == null) return false;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)).equals(sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] build(Piece piece, Output output, byte[] source) throws IOException {
        return switch (output.spec().get("op").getAsString()) {
            case "json" -> patchJson(piece, output.spec(), source);
            case "overlay" -> overlay(output.spec(), source);
            case "dim" -> dim(output.spec(), source);
            default -> throw new IOException("unknown op in " + output.path());
        };
    }

    public static byte[] skipBytes(Piece piece) {
        return GSON.toJson(piece.skipJson()).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] patch(String name) throws IOException {
        try (InputStream in = MsdTransforms.class.getResourceAsStream(PATCHES + name)) {
            if (in == null) throw new IOException("missing " + PATCHES + name);
            return in.readAllBytes();
        }
    }

    private static byte[] patchJson(Piece piece, JsonObject spec, byte[] source) {
        JsonElement root = JsonParser.parseString(new String(source, StandardCharsets.UTF_8));
        if (piece.text() != null) root = replaceText(root, piece.text());
        if (spec.has("remove")) {
            for (JsonElement pointer : spec.getAsJsonArray("remove")) {
                String[] steps = steps(pointer.getAsString());
                parent(root, steps).getAsJsonObject().remove(steps[steps.length - 1]);
            }
        }
        if (spec.has("set")) {
            for (Map.Entry<String, JsonElement> set : spec.getAsJsonObject("set").entrySet()) {
                set(root, set.getKey(), set.getValue());
            }
        }
        if (spec.has("gradient")) {
            for (Map.Entry<String, JsonElement> stop : spec.getAsJsonObject("gradient").entrySet()) {
                set(root, GRADIENT + stop.getKey(), stop.getValue());
            }
        }
        if (spec.has("append")) {
            for (Map.Entry<String, JsonElement> append : spec.getAsJsonObject("append").entrySet()) {
                walk(root, steps(append.getKey())).getAsJsonArray().add(append.getValue());
            }
        }
        if (spec.has("insert_before")) {
            for (JsonElement insert : spec.getAsJsonArray("insert_before")) {
                JsonObject entry = insert.getAsJsonObject();
                insertBefore(walk(root, steps(entry.get("at").getAsString())).getAsJsonObject(), entry.get("before").getAsString(),
                        entry.get("key").getAsString(), entry.get("value"));
            }
        }
        if (spec.has("split_bones")) {
            JsonObject split = spec.getAsJsonObject("split_bones");
            JsonArray bones = walk(root, steps(split.get("at").getAsString())).getAsJsonArray();
            for (JsonElement bone : split.getAsJsonArray("bones")) splitBone(bones, bone.getAsJsonObject());
        }
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonElement replaceText(JsonElement element, JsonObject text) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), replaceText(entry.getValue(), text));
            }
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement entry : element.getAsJsonArray()) out.add(replaceText(entry, text));
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            for (Map.Entry<String, JsonElement> swap : text.entrySet()) {
                value = value.replace(swap.getKey(), swap.getValue().getAsString());
            }
            return new JsonPrimitive(value);
        }
        return element;
    }

    private static void set(JsonElement root, String pointer, JsonElement value) {
        String[] steps = steps(pointer);
        JsonElement parent = parent(root, steps);
        String last = steps[steps.length - 1];
        if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(last), value);
        else parent.getAsJsonObject().add(last, value);
    }

    /** Pose order matters to Cobblemon: the first pose whose condition holds wins. */
    private static void insertBefore(JsonObject object, String before, String key, JsonElement value) {
        Map<String, JsonElement> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) entries.put(entry.getKey(), entry.getValue());
        entries.keySet().forEach(object::remove);
        entries.forEach((name, element) -> {
            if (name.equals(before)) object.add(key, value);
            object.add(name, element);
        });
    }

    /** Moves one cube of a bone into a new child bone, placed right after it, so the cube can be hidden alone. */
    private static void splitBone(JsonArray bones, JsonObject split) {
        String parentName = split.get("parent").getAsString();
        List<JsonElement> list = new ArrayList<>(bones.asList());
        for (int i = 0; i < list.size(); i++) {
            JsonObject parent = list.get(i).getAsJsonObject();
            if (!parent.get("name").getAsString().equals(parentName)) continue;
            JsonObject bone = new JsonObject();
            bone.add("name", split.get("bone"));
            bone.add("parent", split.get("parent"));
            bone.add("pivot", split.get("pivot"));
            JsonArray cubes = new JsonArray();
            cubes.add(parent.getAsJsonArray("cubes").remove(split.get("cube").getAsInt()));
            bone.add("cubes", cubes);
            list.add(i + 1, bone);
            while (!bones.isEmpty()) bones.remove(bones.size() - 1);
            list.forEach(bones::add);
            return;
        }
        throw new IllegalStateException("no bone " + parentName);
    }

    private static String[] steps(String pointer) {
        return pointer.substring(1).split("/");
    }

    private static JsonElement parent(JsonElement root, String[] steps) {
        return walk(root, Arrays.copyOf(steps, steps.length - 1));
    }

    private static JsonElement walk(JsonElement root, String[] steps) {
        JsonElement at = root;
        for (String step : steps) {
            at = at.isJsonArray() ? at.getAsJsonArray().get(Integer.parseInt(step)) : at.getAsJsonObject().get(step);
        }
        return at;
    }

    /** The overlay only fills pixels that are clear in Mega Showdown's glow layer: the core's own UV space. */
    private static byte[] overlay(JsonObject spec, byte[] source) throws IOException {
        BufferedImage image = read(source);
        BufferedImage overlay = read(patch(spec.get("image").getAsString()));
        int left = spec.get("x").getAsInt(), top = spec.get("y").getAsInt();
        for (int y = 0; y < overlay.getHeight(); y++) {
            for (int x = 0; x < overlay.getWidth(); x++) {
                int argb = overlay.getRGB(x, y);
                if (argb >>> 24 != 0) image.setRGB(left + x, top + y, argb);
            }
        }
        return write(image);
    }

    /**
     * The Ultra texture with the glow draining from its hands, claws, legs and armour: the pixels in glow_rects go a
     * stage of the way from their colour towards a dark colour worked out from their own brightness, which reaches
     * Necrozma's black at the last stage. Truncating is what makes this match the textures it replaced.
     */
    private static byte[] dim(JsonObject spec, byte[] source) throws IOException {
        JsonObject rule = manifest.getAsJsonObject("dim");
        double stage = spec.get("stage").getAsDouble(), stages = rule.get("stages").getAsDouble();
        double[] base = doubles(rule.getAsJsonArray("dark_base")), per = doubles(rule.getAsJsonArray("dark_per_brightness"));
        BufferedImage image = read(source);
        for (JsonElement entry : rule.getAsJsonArray("glow_rects")) {
            JsonArray rect = entry.getAsJsonArray();
            int left = rect.get(0).getAsInt(), top = rect.get(1).getAsInt();
            for (int y = top; y < top + rect.get(3).getAsInt(); y++) {
                for (int x = left; x < left + rect.get(2).getAsInt(); x++) {
                    int argb = image.getRGB(x, y);
                    int[] rgb = {argb >> 16 & 255, argb >> 8 & 255, argb & 255};
                    double brightness = (rgb[0] + rgb[1] + rgb[2]) / 3.0;
                    int out = argb & 0xFF000000;
                    for (int c = 0; c < 3; c++) {
                        double dark = base[c] + per[c] * brightness;
                        out |= (int) (rgb[c] + (dark - rgb[c]) * stage / stages) << 16 - 8 * c;
                    }
                    image.setRGB(x, y, out);
                }
            }
        }
        return write(image);
    }

    private static double[] doubles(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsDouble();
        return values;
    }

    private static BufferedImage read(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(png)));
        if (image == null) throw new IOException("not a PNG");
        return image;
    }

    private static byte[] write(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(image, "png", out)) throw new IOException("no PNG writer");
        }
        return bytes.toByteArray();
    }
}
