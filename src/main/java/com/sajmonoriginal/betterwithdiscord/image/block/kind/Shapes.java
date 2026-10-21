package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.Direction;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ElementRotation;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelElement;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelFace;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Shapes {
    private static final int[] FACE_ROTATIONS = {0, 90, 270, 180};
    private static final int[] EDGE_ROTATIONS = {0, 270, 90, 180};
    private static final String ID_PREFIX = "better_with_discord:generated/";
    private static final float CROSS_HALF_WIDTH = (float) (0.45 * Math.sqrt(2.0) * 16.0);
    private static final float SLOPE_HALF_LENGTH = (float) (Math.sqrt(2.0) * 8.0);
    private static final float FIRE_HEIGHT = 1.4F;
    private static final float FIRE_CENTER_Y = FIRE_HEIGHT * 8.0F;

    private Shapes() {
    }

    public static BlockModel cube(String texture, int tintIndex) {
        Map<Direction, ModelFace> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            faces.put(direction, face(texture, 0, 0, 16, 16, 0, tintIndex));
        }
        return model("cube/" + texture, element(0, 0, 0, 16, 16, 16, null, true, faces));
    }

    public static BlockModel cube(Map<Direction, String> textures) {
        return cube(textures, new EnumMap<>(Direction.class));
    }

    public static BlockModel cube(Map<Direction, String> textures, Map<Direction, Integer> uvRotations) {
        Map<Direction, ModelFace> faces = new EnumMap<>(Direction.class);
        StringBuilder id = new StringBuilder("cube");
        for (Direction direction : Direction.values()) {
            String texture = textures.get(direction);
            int uvRotation = uvRotations.getOrDefault(direction, 0) & 3;
            boolean flipV = direction == Direction.DOWN
                || (direction == Direction.NORTH || direction == Direction.EAST) && (uvRotation == 1 || uvRotation == 2);
            int rotation = direction == Direction.NORTH || direction == Direction.EAST ? EDGE_ROTATIONS[uvRotation] : FACE_ROTATIONS[uvRotation];
            faces.put(direction, face(texture, 0, flipV ? 16 : 0, 16, flipV ? 0 : 16, rotation, 0));
            id.append('/').append(texture).append('@').append(uvRotation);
        }
        return model(id.toString(), element(0, 0, 0, 16, 16, 16, null, true, faces));
    }

    public static BlockModel cross(String texture, int tintIndex) {
        return model("cross/" + texture, crossPlane(texture, tintIndex, -45.0F), crossPlane(texture, tintIndex, 45.0F));
    }

    public static BlockModel flat(String texture, float height, int rotation, int tintIndex) {
        Map<Direction, ModelFace> faces = Map.of(Direction.UP, face(texture, 0, 0, 16, 16, rotation, tintIndex));
        return model("flat/" + texture + "/" + height + "/" + rotation, element(0, height, 0, 16, height, 16, null, false, faces));
    }

    public static BlockModel slope(String texture, Direction raised, float height, int tintIndex) {
        boolean alongZ = raised == Direction.NORTH || raised == Direction.SOUTH;
        float angle = raised == Direction.NORTH || raised == Direction.EAST ? 45.0F : -45.0F;
        float centerY = height + 8.0F;
        Map<Direction, ModelFace> faces = Map.of(Direction.UP, face(texture, 0, 0, 16, 16, alongZ ? 0 : 90, tintIndex));
        ModelElement plane = alongZ
                ? element(0, centerY, 8 - SLOPE_HALF_LENGTH, 16, centerY, 8 + SLOPE_HALF_LENGTH,
                new ElementRotation(8, centerY, 8, 'x', angle), false, faces)
                : element(8 - SLOPE_HALF_LENGTH, centerY, 0, 8 + SLOPE_HALF_LENGTH, centerY, 16,
                new ElementRotation(8, centerY, 8, 'z', angle), false, faces);
        return model("slope/" + texture + "/" + raised.name().toLowerCase(Locale.ROOT) + "/" + height, plane);
    }

    public static BlockModel floorFire(String texture) {
        return model("fire/" + texture,
                firePlaneX(texture, 0.45F, -0.5F, Direction.EAST, true),
                firePlaneX(texture, 0.55F, 0.5F, Direction.WEST, true),
                firePlaneZ(texture, 0.55F, 0.5F, Direction.NORTH, true),
                firePlaneZ(texture, 0.45F, -0.5F, Direction.SOUTH, true),
                firePlaneX(texture, 0.05F, 0.1F, Direction.WEST, false),
                firePlaneX(texture, 0.95F, -0.1F, Direction.EAST, false),
                firePlaneZ(texture, 0.95F, -0.1F, Direction.SOUTH, false),
                firePlaneZ(texture, 0.05F, 0.1F, Direction.NORTH, false));
    }

    private static ModelElement crossPlane(String texture, int tintIndex, float angle) {
        return element(8 - CROSS_HALF_WIDTH, 0, 8, 8 + CROSS_HALF_WIDTH, 16, 8,
                new ElementRotation(8, 8, 8, 'y', angle), false,
                doubleSided(texture, tintIndex, Direction.NORTH, Direction.SOUTH));
    }

    private static ModelElement firePlaneX(String texture, float center, float lean, Direction visible, boolean flipped) {
        float x = center * 16.0F;
        float halfLength = fireHalfLength(lean);
        return element(x, FIRE_CENTER_Y - halfLength, 0, x, FIRE_CENTER_Y + halfLength, 16,
                new ElementRotation(x, FIRE_CENTER_Y, 8, 'z', -fireAngle(lean)), false,
                fireFace(texture, visible, flipped));
    }

    private static ModelElement firePlaneZ(String texture, float center, float lean, Direction visible, boolean flipped) {
        float z = center * 16.0F;
        float halfLength = fireHalfLength(lean);
        return element(0, FIRE_CENTER_Y - halfLength, z, 16, FIRE_CENTER_Y + halfLength, z,
                new ElementRotation(8, FIRE_CENTER_Y, z, 'x', fireAngle(lean)), false,
                fireFace(texture, visible, flipped));
    }

    private static Map<Direction, ModelFace> fireFace(String texture, Direction visible, boolean flipped) {
        return Map.of(visible, face(texture, flipped ? 16 : 0, 0, flipped ? 0 : 16, 16, 0, -1));
    }

    private static float fireAngle(float lean) {
        return (float) Math.toDegrees(Math.atan2(lean, FIRE_HEIGHT));
    }

    private static float fireHalfLength(float lean) {
        return (float) (Math.hypot(lean, FIRE_HEIGHT) * 8.0);
    }

    private static Map<Direction, ModelFace> doubleSided(String texture, int tintIndex, Direction front, Direction back) {
        Map<Direction, ModelFace> faces = new EnumMap<>(Direction.class);
        faces.put(front, face(texture, 0, 0, 16, 16, 0, tintIndex));
        faces.put(back, face(texture, 0, 0, 16, 16, 0, tintIndex));
        return faces;
    }

    private static ModelFace face(String texture, float u1, float v1, float u2, float v2, int rotation, int tintIndex) {
        return new ModelFace(u1, v1, u2, v2, texture, rotation, tintIndex);
    }

    private static ModelElement element(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                                        ElementRotation rotation, boolean shade, Map<Direction, ModelFace> faces) {
        return new ModelElement(fromX, fromY, fromZ, toX, toY, toZ, rotation, shade, Collections.unmodifiableMap(faces));
    }

    private static BlockModel model(String id, ModelElement... elements) {
        return new BlockModel(ID_PREFIX + id, List.of(elements));
    }
}
