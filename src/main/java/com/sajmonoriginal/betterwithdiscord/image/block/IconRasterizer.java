package com.sajmonoriginal.betterwithdiscord.image.block;

import com.sajmonoriginal.betterwithdiscord.image.Raster;
import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.Direction;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ElementRotation;
import com.sajmonoriginal.betterwithdiscord.image.block.model.GuiDisplay;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelElement;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelFace;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

final class IconRasterizer {
    private static final float AMBIENT = 0.4F;
    private static final float DIFFUSE = 0.5F;
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Matrix4f NORMAL_TRANSFORM = new Matrix4f()
        .rotateY((float) Math.toRadians(-30.0F))
        .rotateX((float) Math.toRadians(155.0F))
        .invert();

    private IconRasterizer() {
    }

    static BufferedImage render(BlockModel model, GuiDisplay display, Function<String, BufferedImage> textures, Function<String, BufferedImage> masks,
                                IntUnaryOperator tint, int scale) {
        Matrix4f transform = new Matrix4f()
            .translate(8.0F, 8.0F, 8.0F)
            .scale(16.0F, -16.0F, 16.0F)
            .translate(display.tx(), display.ty(), display.tz())
            .rotateX((float) Math.toRadians(display.rx()))
            .rotateY((float) Math.toRadians(display.ry()))
            .rotateZ((float) Math.toRadians(display.rz()))
            .scale(display.sx(), display.sy(), display.sz());

        List<Raster.Quad> quads = new ArrayList<>();
        for (ModelElement element : model.elements()) {
            for (Map.Entry<Direction, ModelFace> entry : element.faces().entrySet()) {
                BufferedImage texture = textures.apply(entry.getValue().texture());
                if (texture != null) {
                    quads.add(quad(element, entry.getKey(), entry.getValue(), texture, masks.apply(entry.getValue().texture()), transform, tint, scale));
                }
            }
        }

        return Raster.draw(quads, 16 * scale, 16 * scale);
    }

    private static Raster.Quad quad(ModelElement element, Direction side, ModelFace face, BufferedImage texture, BufferedImage mask, Matrix4f transform,
                             IntUnaryOperator tint, int scale) {
        float x0 = element.fromX();
        float y0 = element.fromY();
        float z0 = element.fromZ();
        float x1 = element.toX();
        float y1 = element.toY();
        float z1 = element.toZ();
        float[][] corners = switch (side) {
            case NORTH -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case DOWN -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
        float[][] uvs = {{face.u1(), face.v1()}, {face.u2(), face.v1()}, {face.u2(), face.v2()}, {face.u1(), face.v2()}};
        int turns = Math.floorMod(face.rotation() / 90, 4);

        Vector3f normal = new Vector3f(side.x, side.y, side.z);
        ElementRotation rotation = element.rotation();
        Matrix4f elementTransform = elementTransform(rotation);
        elementTransform.transformDirection(normal);

        Raster.Quad quad = new Raster.Quad(texture, mask);
        for (int i = 0; i < 4; i++) {
            Vector4f point = new Vector4f(corners[i][0], corners[i][1], corners[i][2], 1.0F);
            elementTransform.transform(point);
            point.set(point.x / 16.0F - 0.5F, point.y / 16.0F - 0.5F, point.z / 16.0F - 0.5F, 1.0F);
            transform.transform(point);
            float[] uv = uvs[Math.floorMod(i - turns, 4)];
            quad.x[i] = point.x * scale;
            quad.y[i] = point.y * scale;
            quad.z[i] = point.z;
            quad.u[i] = uv[0] / 16.0F * texture.getWidth();
            quad.v[i] = uv[1] / 16.0F * texture.getHeight();
        }

        Vector4f facing = new Vector4f(normal, 0.0F);
        transform.transform(facing);
        quad.visible = facing.z > 1.0E-4F;
        quad.depth = (quad.z[0] + quad.z[1] + quad.z[2] + quad.z[3]) / 4.0F;

        Vector3f lit = element.shade() ? new Vector3f(normal) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector4f lightNormal = new Vector4f(lit, 0.0F);
        transform.transform(lightNormal);
        NORMAL_TRANSFORM.transform(lightNormal);
        Vector3f n = new Vector3f(lightNormal.x, lightNormal.y, lightNormal.z).normalize();
        float light = Math.min(1.0F, AMBIENT + DIFFUSE * (Math.max(0.0F, n.dot(LIGHT_0)) + Math.max(0.0F, n.dot(LIGHT_1))));
        quad.light = light;
        quad.tint = face.tintIndex() >= 0 ? tint.applyAsInt(face.tintIndex()) : -1;
        return quad;
    }

    private static Matrix4f elementTransform(ElementRotation rotation) {
        Matrix4f matrix = new Matrix4f();
        if (rotation == null || rotation.angle() == 0.0F) {
            return matrix;
        }
        float angle = (float) Math.toRadians(rotation.angle());
        matrix.translate(rotation.originX(), rotation.originY(), rotation.originZ());
        switch (rotation.axis()) {
            case 'x' -> matrix.rotateX(angle);
            case 'y' -> matrix.rotateY(angle);
            default -> matrix.rotateZ(angle);
        }
        matrix.translate(-rotation.originX(), -rotation.originY(), -rotation.originZ());
        return matrix;
    }
}
