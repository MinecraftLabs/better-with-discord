package com.sajmonoriginal.betterwithdiscord.image.entity;

import java.util.List;
import java.util.Map;

public record Geometry(String id, double textureWidth, double textureHeight, Map<String, Bone> bones) {
    public record Bone(String name, String parent, double[] pivot, double[] rotation, double inflate, List<Cube> cubes) {
    }

    public record Cube(double[] origin, double[] size, Face[] faces, double inflate, double[] pivot, double[] rotation) {
    }

    public record Face(double u, double v, double width, double height, int rotation) {
    }
}
