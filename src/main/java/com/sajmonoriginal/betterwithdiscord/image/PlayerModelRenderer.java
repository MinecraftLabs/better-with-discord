package com.sajmonoriginal.betterwithdiscord.image;

import com.sajmonoriginal.betterwithdiscord.image.entity.EntityGeometries;
import com.sajmonoriginal.betterwithdiscord.image.entity.EntityMesh;
import com.sajmonoriginal.betterwithdiscord.image.entity.Geometry;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlayerModelRenderer {
    private static final String RENDERER = "minecraft:player";
    private static final double AMBIENT = 0.5;
    private static final double DIFFUSE = 0.5;
    private static final Vector3d LIGHT_0 = new Vector3d(0.2, 1.0, -0.7).normalize();
    private static final Vector3d LIGHT_1 = new Vector3d(-0.2, 1.0, 0.7).normalize();
    private static final double BODY_YAW = Math.toRadians(-9.3);
    private static final double HEAD_PITCH = Math.toRadians(-4.9);
    private static final String[] ARMOR_MODELS = {"armor.helmet", "armor.chestplate", "armor.leggings", "armor.boots"};
    private static final List<Set<String>> HIDDEN_ARMOR_BONES = List.of(
        Set.of("chest", "rightArm", "leftArm", "rightLeg", "leftLeg"),
        Set.of("head", "rightLeg", "leftLeg"),
        Set.of("head", "rightArm", "leftArm"),
        Set.of("head", "rightArm", "leftArm", "chest"));
    private static final Set<String> HIDDEN_PLAYER_BONES = Set.of("cape", "rightItem", "leftItem");
    private static final String CAPE = "cape";
    private static final double CAPE_OFFSET = 0.125;
    private static final double CAPE_TILT = Math.toRadians(-5.0);

    private final ClientAssets assets;
    private final EntityGeometries geometries;

    PlayerModelRenderer(ClientAssets assets, EntityGeometries geometries) {
        this.assets = assets;
        this.geometries = geometries;
    }

    BufferedImage render(PlayerSkins.Skin skin, InventorySnapshot inventory, int width, int height, double centerX, double feetY, double pixelsPerUnit) {
        Matrix4d world = new Matrix4d()
            .translate(centerX, feetY, 0.0)
            .scale(pixelsPerUnit, -pixelsPerUnit, pixelsPerUnit)
            .rotateY(Math.PI + BODY_YAW);
        List<Raster.Quad> quads = new ArrayList<>();
        EntityGeometries.Mapping body = geometries.mapping(RENDERER, skin != null && skin.slim() ? "main.slim" : "main");
        Geometry bodyGeometry = body == null ? null : geometries.geometry(body.model());
        if (skin != null && bodyGeometry != null && !inventory.invisible()) {
            add(quads, bodyGeometry, body.inflation(), skin.image(), world, HIDDEN_PLAYER_BONES, 1.0F);
        }
        List<String> armorTextures = inventory.armorTextures();
        for (int layer = 0; layer < ARMOR_MODELS.length && layer < armorTextures.size(); layer++) {
            String texture = armorTextures.get(layer);
            EntityGeometries.Mapping armor = geometries.mapping(RENDERER, ARMOR_MODELS[layer]);
            BufferedImage image = texture == null ? null : assets.image(texture);
            if (armor != null && image != null) {
                add(quads, geometries.geometry(armor.model()), armor.inflation(), image, world, HIDDEN_ARMOR_BONES.get(layer), 1.0F);
            }
        }
        for (InventorySnapshot.WornModel model : inventory.wornModels()) {
            BufferedImage image = assets.image(model.texture());
            if (image != null) {
                add(quads, geometries.geometry(model.geometry()), model.inflation(), image, world, model.hiddenBones(), model.opacity());
            }
        }
        InventorySnapshot.Cape cape = inventory.cape();
        EntityGeometries.Mapping capeModel = geometries.mapping(RENDERER, CAPE);
        BufferedImage capeImage = cape == null ? null : assets.image(cape.texture());
        if (capeImage != null && capeModel != null && bodyGeometry != null) {
            Matrix4d attached = EntityMesh.attach(bodyGeometry, CAPE, Map.of(), world).translate(0.0, 0.0, CAPE_OFFSET);
            add(quads, geometries.geometry(capeModel.model()), capeModel.inflation(), capeImage, attached,
                Map.of(CAPE, EntityMesh.BonePose.rotation(CAPE_TILT, 0.0, 0.0)), cape.opacity());
        }
        return Raster.draw(quads, width, height);
    }

    private static void add(List<Raster.Quad> quads, Geometry geometry, double inflation, BufferedImage texture, Matrix4d world, Set<String> hidden,
                            float opacity) {
        if (geometry == null) {
            return;
        }
        Map<String, EntityMesh.BonePose> poses = new HashMap<>();
        poses.put("head", EntityMesh.BonePose.rotation(HEAD_PITCH, 0.0, 0.0));
        for (String bone : hidden) {
            poses.put(bone, new EntityMesh.BonePose(0, 0, 0, 0, 0, 0, false));
        }
        add(quads, geometry, inflation, texture, world, poses, opacity);
    }

    private static void add(List<Raster.Quad> quads, Geometry geometry, double inflation, BufferedImage texture, Matrix4d world,
                            Map<String, EntityMesh.BonePose> poses, float opacity) {
        if (geometry == null) {
            return;
        }
        double uScale = texture.getWidth() / geometry.textureWidth();
        double vScale = texture.getHeight() / geometry.textureHeight();
        for (EntityMesh.Quad source : EntityMesh.build(geometry, inflation, poses, world)) {
            Raster.Quad quad = new Raster.Quad(texture, null);
            for (int i = 0; i < 4; i++) {
                quad.x[i] = (float) source.positions()[i][0];
                quad.y[i] = (float) source.positions()[i][1];
                quad.z[i] = (float) source.positions()[i][2];
                quad.u[i] = (float) (source.uvs()[i][0] * uScale);
                quad.v[i] = (float) (source.uvs()[i][1] * vScale);
            }
            double[] normal = source.normal();
            quad.visible = normal[2] > 1.0E-4;
            quad.depth = (quad.z[0] + quad.z[1] + quad.z[2] + quad.z[3]) / 4.0F;
            Vector3d lit = new Vector3d(normal[0], -normal[1], normal[2]);
            quad.light = (float) Math.min(1.0, AMBIENT + DIFFUSE * (Math.max(0.0, lit.dot(LIGHT_0)) + Math.max(0.0, lit.dot(LIGHT_1))));
            quad.tint = -1;
            quad.opacity = opacity;
            quads.add(quad);
        }
    }
}
