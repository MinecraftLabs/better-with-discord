package com.sajmonoriginal.betterwithdiscord.image.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.ClientAssets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EntityGeometries {
    private static final String MANIFEST = "/models/entity/models.json";
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;
    private static final String[] FACE_NAMES = {"down", "up", "north", "south", "west", "east"};

    private final Map<String, Geometry> geometries = new HashMap<>();
    private final Map<String, Map<String, Mapping>> renderers = new HashMap<>();

    public EntityGeometries(ClientAssets assets) {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add("minecraft");
        for (ClientAssets.ModArchive mod : assets.mods()) {
            namespaces.add(mod.modId());
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String namespace : namespaces) {
            JsonObject manifest = json(assets, "assets/" + namespace + MANIFEST);
            if (manifest == null) {
                continue;
            }
            if (manifest.has("model_paths")) {
                for (JsonElement path : manifest.getAsJsonArray("model_paths")) {
                    paths.add(path.getAsString().replaceFirst("^/", ""));
                }
            }
            if (manifest.has("renderers")) {
                for (Map.Entry<String, JsonElement> renderer : manifest.getAsJsonObject("renderers").entrySet()) {
                    Map<String, Mapping> mappings = renderers.computeIfAbsent(renderer.getKey(), k -> new HashMap<>());
                    for (Map.Entry<String, JsonElement> mapping : renderer.getValue().getAsJsonObject().entrySet()) {
                        JsonElement value = mapping.getValue();
                        if (value.isJsonPrimitive()) {
                            mappings.put(mapping.getKey(), new Mapping(value.getAsString(), 0.0));
                        } else if (value.isJsonObject()) {
                            JsonObject object = value.getAsJsonObject();
                            mappings.put(mapping.getKey(), new Mapping(object.get("model").getAsString(),
                                object.has("inflation") ? object.get("inflation").getAsDouble() : 0.0));
                        }
                    }
                }
            }
        }
        for (String path : paths) {
            JsonObject file = json(assets, path);
            if (file == null) {
                continue;
            }
            try {
                load(file);
            } catch (RuntimeException e) {
                BetterWithDiscordMod.LOGGER.warn("Could not read entity model {}", path, e);
            }
        }
    }

    public Mapping mapping(String renderer, String local) {
        Map<String, Mapping> mappings = renderers.get(renderer);
        return mappings == null ? null : mappings.get(local);
    }

    public Geometry geometry(String id) {
        return geometries.get(id);
    }

    private static JsonObject json(ClientAssets assets, String path) {
        try {
            byte[] data = assets.read(path);
            return data == null ? null : JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not read {}", path, e);
            return null;
        }
    }

    private void load(JsonObject file) {
        if (file.has("minecraft:geometry")) {
            for (JsonElement element : file.getAsJsonArray("minecraft:geometry")) {
                JsonObject object = element.getAsJsonObject();
                JsonObject description = object.getAsJsonObject("description");
                String id = description.get("identifier").getAsString();
                geometries.put(id, geometry(id, description.get("texture_width").getAsDouble(),
                    description.get("texture_height").getAsDouble(), object));
            }
            return;
        }
        for (Map.Entry<String, JsonElement> entry : file.entrySet()) {
            if (!"format_version".equals(entry.getKey()) && entry.getValue().isJsonObject()) {
                JsonObject object = entry.getValue().getAsJsonObject();
                geometries.put(entry.getKey(), geometry(entry.getKey(), object.get("texturewidth").getAsDouble(),
                    object.get("textureheight").getAsDouble(), object));
            }
        }
    }

    private static Geometry geometry(String id, double width, double height, JsonObject object) {
        Map<String, Geometry.Bone> bones = new LinkedHashMap<>();
        if (object.has("bones")) {
            for (JsonElement element : object.getAsJsonArray("bones")) {
                Geometry.Bone bone = bone(element.getAsJsonObject());
                bones.put(bone.name(), bone);
            }
        }
        return new Geometry(id, width, height, bones);
    }

    private static Geometry.Bone bone(JsonObject object) {
        boolean mirror = object.has("mirror") && object.get("mirror").getAsBoolean();
        List<Geometry.Cube> cubes = new ArrayList<>();
        if (object.has("cubes")) {
            for (JsonElement element : object.getAsJsonArray("cubes")) {
                cubes.add(cube(element.getAsJsonObject(), mirror));
            }
        }
        return new Geometry.Bone(object.get("name").getAsString(), object.has("parent") ? object.get("parent").getAsString() : null,
            vector(object, "pivot"), vector(object, "rotation"), object.has("inflate") ? object.get("inflate").getAsDouble() : 0.0, cubes);
    }

    private static Geometry.Cube cube(JsonObject object, boolean boneMirror) {
        double[] origin = vector(object, "origin");
        double[] size = vector(object, "size");
        Geometry.Face[] faces = new Geometry.Face[6];
        JsonElement uv = object.get("uv");
        if (uv != null && uv.isJsonArray()) {
            JsonArray array = uv.getAsJsonArray();
            double u = array.get(0).getAsDouble();
            double v = array.get(1).getAsDouble();
            double sx = size[0];
            double sy = size[1];
            double sz = size[2];
            faces[UP] = new Geometry.Face(u + sz, v, sx, sz, 0);
            faces[DOWN] = new Geometry.Face(u + sx + sz, v + sz, sx, -sz, 0);
            faces[NORTH] = new Geometry.Face(u + sz, v + sz, sx, sy, 0);
            faces[SOUTH] = new Geometry.Face(u + sz * 2.0 + sx, v + sz, sx, sy, 0);
            faces[EAST] = new Geometry.Face(u, v + sz, sz, sy, 0);
            faces[WEST] = new Geometry.Face(u + sz + sx, v + sz, sz, sy, 0);
        } else if (uv != null && uv.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : uv.getAsJsonObject().entrySet()) {
                int index = faceIndex(entry.getKey());
                if (index < 0) {
                    continue;
                }
                JsonObject face = entry.getValue().getAsJsonObject();
                JsonArray at = face.getAsJsonArray("uv");
                JsonArray extent = face.getAsJsonArray("uv_size");
                int rotation = face.has("uv_rotation") ? Math.floorMod(face.get("uv_rotation").getAsInt() + 360, 360) / 90 : 0;
                faces[index] = new Geometry.Face(at.get(0).getAsDouble(), at.get(1).getAsDouble(),
                    extent.get(0).getAsDouble(), extent.get(1).getAsDouble(), rotation);
            }
        }
        boolean mirror = boneMirror ^ (object.has("mirror") && object.get("mirror").getAsBoolean());
        if (mirror) {
            Geometry.Face west = faces[WEST];
            faces[WEST] = faces[EAST];
            faces[EAST] = west;
            for (int i = 0; i < faces.length; i++) {
                Geometry.Face face = faces[i];
                if (face != null) {
                    faces[i] = new Geometry.Face(face.u() + face.width(), face.v(), -face.width(), face.height(), face.rotation());
                }
            }
        }
        double[] rotation = object.has("rotation") ? vector(object, "rotation") : null;
        return new Geometry.Cube(origin, size, faces, object.has("inflate") ? object.get("inflate").getAsDouble() : 0.0,
            vector(object, "pivot"), rotation);
    }

    private static int faceIndex(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < FACE_NAMES.length; i++) {
            if (FACE_NAMES[i].equals(lower)) {
                return i;
            }
        }
        return -1;
    }

    private static double[] vector(JsonObject object, String key) {
        double[] result = new double[3];
        if (object.has(key) && object.get(key).isJsonArray()) {
            JsonArray array = object.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) {
                result[i] = array.get(i).getAsDouble();
            }
        }
        return result;
    }

    public record Mapping(String model, double inflation) {
    }
}
