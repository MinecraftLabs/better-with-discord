package com.sajmonoriginal.betterwithdiscord.image.entity;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EntityMesh {
    private static final int[][][] CORNERS = {
        {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},
        {{0, 1, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}},
        {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}},
        {{0, 0, 1}, {0, 1, 1}, {1, 1, 1}, {1, 0, 1}},
        {{1, 0, 1}, {1, 1, 1}, {1, 1, 0}, {1, 0, 0}},
        {{0, 0, 1}, {0, 0, 0}, {0, 1, 0}, {0, 1, 1}}
    };
    private static final int[][] UVS = {
        {0, 1, 1, 1, 1, 0, 0, 0},
        {0, 0, 0, 1, 1, 1, 1, 0},
        {0, 1, 1, 1, 1, 0, 0, 0},
        {1, 1, 1, 0, 0, 0, 0, 1},
        {1, 1, 1, 0, 0, 0, 0, 1},
        {0, 1, 1, 1, 1, 0, 0, 0}
    };
    private static final double[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    private EntityMesh() {
    }

    public static List<Quad> build(Geometry geometry, double inflation, Map<String, BonePose> poses, Matrix4d world) {
        List<Quad> quads = new ArrayList<>();
        for (Geometry.Bone bone : geometry.bones().values()) {
            Matrix4d transform = new Matrix4d();
            if (boneTransform(geometry, bone, poses, transform)) {
                continue;
            }
            Matrix4d full = new Matrix4d(world).mul(transform);
            Matrix3d normals = new Matrix3d();
            full.normal(normals);
            for (Geometry.Cube cube : bone.cubes()) {
                addCube(quads, cube, bone.inflate() + inflation, full, normals);
            }
        }
        return quads;
    }

    public static Matrix4d attach(Geometry geometry, String boneName, Map<String, BonePose> poses, Matrix4d world) {
        Matrix4d result = new Matrix4d(world);
        Geometry.Bone bone = geometry.bones().get(boneName);
        if (bone != null) {
            Matrix4d transform = new Matrix4d();
            boneTransform(geometry, bone, poses, transform);
            result.mul(transform).translate(bone.pivot()[0], bone.pivot()[1], bone.pivot()[2]);
        }
        return result;
    }

    private static boolean boneTransform(Geometry geometry, Geometry.Bone bone, Map<String, BonePose> poses, Matrix4d dest) {
        boolean hidden = false;
        Geometry.Bone parent = bone.parent() == null ? null : geometry.bones().get(bone.parent());
        if (parent != null) {
            hidden = boneTransform(geometry, parent, poses, dest);
        }
        BonePose pose = poses.getOrDefault(bone.name(), BonePose.NONE);
        hidden |= !pose.visible();
        double[] pivot = bone.pivot();
        double[] rotation = bone.rotation();
        dest.translate(pivot[0], pivot[1], pivot[2])
            .translate(pose.x(), pose.y(), pose.z())
            .rotateZ(-pose.rotZ() - Math.toRadians(rotation[2]))
            .rotateY(pose.rotY() + Math.toRadians(rotation[1]))
            .rotateX(-pose.rotX() - Math.toRadians(rotation[0]))
            .translate(-pivot[0], -pivot[1], -pivot[2]);
        return hidden;
    }

    private static void addCube(List<Quad> quads, Geometry.Cube cube, double inflate, Matrix4d transform, Matrix3d normals) {
        double grow = cube.inflate() + inflate;
        double[] min = new double[3];
        double[] max = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            min[axis] = cube.origin()[axis] - grow;
            max[axis] = min[axis] + cube.size()[axis] + grow * 2.0;
        }
        for (int index = 0; index < 6; index++) {
            Geometry.Face face = cube.faces()[index];
            if (face == null) {
                continue;
            }
            double[] us = {face.u(), face.u() + face.width()};
            double[] vs = {face.v(), face.v() + face.height()};
            double[][] positions = new double[4][];
            double[][] uvs = new double[4][];
            for (int corner = 0; corner < 4; corner++) {
                int[] select = CORNERS[index][corner];
                Vector3d point = new Vector3d(select[0] == 1 ? max[0] : min[0], select[1] == 1 ? max[1] : min[1], select[2] == 1 ? max[2] : min[2]);
                rotate(point, cube, true);
                transform.transformPosition(point);
                positions[corner] = new double[] {point.x, point.y, point.z};
                int slot = (corner * 2 + face.rotation() * 2) % 8;
                uvs[corner] = new double[] {us[UVS[index][slot]], vs[UVS[index][slot + 1]]};
            }
            Vector3d normal = new Vector3d(NORMALS[index][0], NORMALS[index][1], NORMALS[index][2]);
            rotate(normal, cube, false);
            normals.transform(normal);
            if (normal.lengthSquared() > 0.0) {
                normal.normalize();
            }
            quads.add(new Quad(positions, uvs, new double[] {normal.x, normal.y, normal.z}));
        }
    }

    private static void rotate(Vector3d point, Geometry.Cube cube, boolean aroundPivot) {
        double[] rotation = cube.rotation();
        if (rotation == null) {
            return;
        }
        double[] pivot = cube.pivot();
        if (aroundPivot) {
            point.sub(pivot[0], pivot[1], pivot[2]);
        }
        point.rotateX(-Math.toRadians(rotation[0])).rotateY(Math.toRadians(rotation[1])).rotateZ(-Math.toRadians(rotation[2]));
        if (aroundPivot) {
            point.add(pivot[0], pivot[1], pivot[2]);
        }
    }

    public record Quad(double[][] positions, double[][] uvs, double[] normal) {
    }

    public record BonePose(double rotX, double rotY, double rotZ, double x, double y, double z, boolean visible) {
        public static final BonePose NONE = new BonePose(0, 0, 0, 0, 0, 0, true);

        public static BonePose rotation(double rotX, double rotY, double rotZ) {
            return new BonePose(rotX, rotY, rotZ, 0, 0, 0, true);
        }
    }
}
