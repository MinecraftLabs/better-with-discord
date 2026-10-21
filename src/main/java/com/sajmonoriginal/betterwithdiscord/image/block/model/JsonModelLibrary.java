package com.sajmonoriginal.betterwithdiscord.image.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.ClientAssets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonModelLibrary implements ModelLibrary {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private final ClientAssets assets;
    private final Map<String, Optional<RawModel>> raw = new ConcurrentHashMap<>();
    private final Map<String, Optional<BlockModel>> resolved = new ConcurrentHashMap<>();

    public JsonModelLibrary(ClientAssets assets) {
        this.assets = assets;
    }

    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + id : id;
    }

    @Override
    public BlockModel load(String id) {
        String key = normalize(id);
        return resolved.computeIfAbsent(key, k -> Optional.ofNullable(resolveSafely(k))).orElse(null);
    }

    private BlockModel resolveSafely(String id) {
        try {
            return resolve(id);
        } catch (RuntimeException e) {
            BetterWithDiscordMod.LOGGER.warn("Could not read block model {}: {}", id, e.toString());
            return null;
        }
    }

    private BlockModel resolve(String id) {
        List<RawModel> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = id;
        while (current != null && seen.add(current)) {
            RawModel model = raw(current);
            if (model == null) {
                if (chain.isEmpty()) {
                    return null;
                }
                break;
            }
            chain.add(model);
            current = model.parent == null ? null : normalize(model.parent);
        }

        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            RawModel model = chain.get(i);
            textures.putAll(model.textures);
            if (model.elements != null) {
                elements = model.elements;
            }
        }

        List<ModelElement> parsed = new ArrayList<>();
        if (elements != null) {
            for (JsonElement element : elements) {
                ModelElement modelElement = parseElement(element.getAsJsonObject(), textures);
                if (modelElement != null) {
                    parsed.add(modelElement);
                }
            }
        }
        return new BlockModel(id, Collections.unmodifiableList(parsed));
    }

    public String textureVariable(String id, String variable) {
        Map<String, String> textures = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<RawModel> chain = new ArrayList<>();
        String current = normalize(id);
        while (current != null && seen.add(current)) {
            RawModel model = raw(current);
            if (model == null) {
                break;
            }
            chain.add(model);
            current = model.parent == null ? null : normalize(model.parent);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            textures.putAll(chain.get(i).textures);
        }
        return textures.containsKey(variable) ? resolveTexture("#" + variable, textures) : null;
    }

    public GuiDisplay guiDisplay(String id) {
        Set<String> seen = new HashSet<>();
        String current = normalize(id);
        while (current != null && seen.add(current)) {
            RawModel model = raw(current);
            if (model == null) {
                return null;
            }
            if (model.guiDisplay != null) {
                float[] rotation = floats(model.guiDisplay, "rotation", 0.0F);
                float[] translation = floats(model.guiDisplay, "translation", 0.0F);
                float[] scale = floats(model.guiDisplay, "scale", 1.0F);
                return new GuiDisplay(translation[0] / 16.0F, translation[1] / 16.0F, translation[2] / 16.0F,
                    rotation[0], rotation[1], rotation[2], scale[0], scale[1], scale[2]);
            }
            current = model.parent == null ? null : normalize(model.parent);
        }
        return null;
    }

    private static float[] floats(JsonObject object, String key, float fallback) {
        float[] values = {fallback, fallback, fallback};
        if (object.has(key)) {
            JsonArray array = object.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) {
                values[i] = array.get(i).getAsFloat();
            }
        }
        return values;
    }

    private RawModel raw(String id) {
        return raw.computeIfAbsent(id, k -> Optional.ofNullable(readRaw(k))).orElse(null);
    }

    private RawModel readRaw(String id) {
        int colon = id.indexOf(':');
        String path = "assets/" + id.substring(0, colon) + "/models/" + id.substring(colon + 1) + ".json";
        try {
            byte[] data = assets.read(path);
            if (data == null) {
                return null;
            }
            JsonObject object = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> textures = new HashMap<>();
            if (object.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("textures").entrySet()) {
                    textures.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return new RawModel(
                object.has("parent") ? object.get("parent").getAsString() : null,
                textures,
                object.has("elements") ? object.getAsJsonArray("elements") : null,
                object.has("display") && object.getAsJsonObject("display").has("gui") ? object.getAsJsonObject("display").getAsJsonObject("gui") : null);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            BetterWithDiscordMod.LOGGER.warn("Could not read block model {}: {}", id, e.getMessage());
            return null;
        }
    }

    private ModelElement parseElement(JsonObject object, Map<String, String> textures) {
        float[] from = floats(object.getAsJsonArray("from"), 3);
        float[] to = floats(object.getAsJsonArray("to"), 3);
        if (from == null || to == null || !object.has("faces")) {
            return null;
        }

        float[] boxFrom = from.clone();
        float[] boxTo = to.clone();
        ElementRotation rotation = null;
        if (object.has("rotation")) {
            JsonObject rot = object.getAsJsonObject("rotation");
            float[] origin = rot.has("origin") ? floats(rot.getAsJsonArray("origin"), 3) : new float[] {8, 8, 8};
            if (origin != null && rot.has("axis") && rot.has("angle")) {
                char axis = rot.get("axis").getAsString().charAt(0);
                float angle = rot.get("angle").getAsFloat();
                if (rot.has("rescale") && rot.get("rescale").getAsBoolean()) {
                    rescale(boxFrom, boxTo, origin, axis, angle);
                }
                rotation = new ElementRotation(origin[0], origin[1], origin[2], axis, angle);
            }
        }

        Map<Direction, ModelFace> faces = new EnumMap<>(Direction.class);
        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("faces").entrySet()) {
            Direction side;
            try {
                side = Direction.byName(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            JsonObject face = entry.getValue().getAsJsonObject();
            if (!face.has("texture")) {
                continue;
            }
            String texture = resolveTexture(face.get("texture").getAsString(), textures);
            if (texture == null) {
                continue;
            }
            float[] uv = face.has("uv") ? floats(face.getAsJsonArray("uv"), 4) : null;
            if (uv == null) {
                uv = defaultUv(side, from, to);
            }
            faces.put(side, new ModelFace(uv[0], uv[1], uv[2], uv[3], texture,
                face.has("rotation") ? face.get("rotation").getAsInt() : 0,
                face.has("tintindex") ? face.get("tintindex").getAsInt() : -1));
        }
        if (faces.isEmpty()) {
            return null;
        }
        boolean shade = !object.has("shade") || object.get("shade").getAsBoolean();
        return new ModelElement(boxFrom[0], boxFrom[1], boxFrom[2], boxTo[0], boxTo[1], boxTo[2], rotation, shade,
            Collections.unmodifiableMap(faces));
    }

    private static void rescale(float[] from, float[] to, float[] origin, char axis, float angle) {
        float x = (45.0F - Math.abs((angle + 90.0F) % 90.0F - 45.0F)) / 45.0F;
        float scalar = (float) Math.sqrt(1.0F + x * x);
        for (int i = 0; i < 3; i++) {
            if (i == axis - 'x') {
                continue;
            }
            from[i] = (from[i] - origin[i]) * scalar + origin[i];
            to[i] = (to[i] - origin[i]) * scalar + origin[i];
        }
    }

    private static String resolveTexture(String reference, Map<String, String> textures) {
        String current = reference;
        Set<String> seen = new HashSet<>();
        while (current != null && current.startsWith("#")) {
            String key = current.substring(1);
            if (!seen.add(key)) {
                return null;
            }
            current = textures.get(key);
        }
        return current == null ? null : normalize(current);
    }

    private static float[] defaultUv(Direction side, float[] from, float[] to) {
        return switch (side) {
            case UP -> new float[] {from[0], from[2], to[0], to[2]};
            case DOWN -> new float[] {from[0], 16 - to[2], to[0], 16 - from[2]};
            case NORTH -> new float[] {16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[] {from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[] {from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[] {16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    private static float[] floats(JsonArray array, int size) {
        if (array == null || array.size() < size) {
            return null;
        }
        float[] values = new float[size];
        for (int i = 0; i < size; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private record RawModel(String parent, Map<String, String> textures, JsonArray elements, JsonObject guiDisplay) {
    }
}
