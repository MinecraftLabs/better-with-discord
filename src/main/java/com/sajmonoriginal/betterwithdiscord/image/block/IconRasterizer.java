package com.sajmonoriginal.betterwithdiscord.image.block;

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
import java.util.Arrays;
import java.util.Comparator;
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
    private static final int ALPHA_TEST = 26;

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

        List<Quad> quads = new ArrayList<>();
        for (ModelElement element : model.elements()) {
            for (Map.Entry<Direction, ModelFace> entry : element.faces().entrySet()) {
                BufferedImage texture = textures.apply(entry.getValue().texture());
                if (texture != null) {
                    quads.add(quad(element, entry.getKey(), entry.getValue(), texture, masks.apply(entry.getValue().texture()), transform, tint, scale));
                }
            }
        }

        int size = 16 * scale;
        int[] pixels = new int[size * size];
        float[] depth = new float[size * size];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        quads.removeIf(quad -> !quad.visible);
        quads.sort(Comparator.comparingDouble(quad -> quad.depth));
        for (Quad quad : quads) {
            quad.draw(pixels, depth, size);
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, size, size, pixels, 0, size);
        return image;
    }

    private static Quad quad(ModelElement element, Direction side, ModelFace face, BufferedImage texture, BufferedImage mask, Matrix4f transform,
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

        Quad quad = new Quad(texture, mask);
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

    static int tint(int texel, int tint, BufferedImage mask, int tx, int ty, BufferedImage texture) {
        if (tint == -1) {
            return texel;
        }
        float amount = 1.0F;
        if (mask != null) {
            int mx = tx * mask.getWidth() / texture.getWidth();
            int my = ty * mask.getWidth() / texture.getWidth();
            amount = my < mask.getHeight() ? (mask.getRGB(mx, my) >> 16 & 0xFF) / 255.0F : 0.0F;
        }
        int red = mixChannel(texel >> 16 & 0xFF, tint >> 16 & 0xFF, amount);
        int green = mixChannel(texel >> 8 & 0xFF, tint >> 8 & 0xFF, amount);
        int blue = mixChannel(texel & 0xFF, tint & 0xFF, amount);
        return texel & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int mixChannel(int texel, int tint, float amount) {
        return Math.round(texel * (1.0F + (tint / 255.0F - 1.0F) * amount));
    }

    private static final class Quad {
        final BufferedImage texture;
        final BufferedImage mask;
        final float[] x = new float[4];
        final float[] y = new float[4];
        final float[] z = new float[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        boolean visible;
        float depth;
        float light;
        int tint;

        Quad(BufferedImage texture, BufferedImage mask) {
            this.texture = texture;
            this.mask = mask;
        }

        void draw(int[] pixels, float[] depthBuffer, int size) {
            triangle(0, 1, 2, pixels, depthBuffer, size);
            triangle(0, 2, 3, pixels, depthBuffer, size);
        }

        private void triangle(int a, int b, int c, int[] pixels, float[] depthBuffer, int size) {
            float area = edge(x[a], y[a], x[b], y[b], x[c], y[c]);
            if (Math.abs(area) < 1.0E-6F) {
                return;
            }
            int minX = Math.max(0, (int) Math.floor(Math.min(x[a], Math.min(x[b], x[c]))));
            int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(x[a], Math.max(x[b], x[c]))));
            int minY = Math.max(0, (int) Math.floor(Math.min(y[a], Math.min(y[b], y[c]))));
            int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(y[a], Math.max(y[b], y[c]))));
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    float sx = px + 0.5F;
                    float sy = py + 0.5F;
                    float wa = edge(x[b], y[b], x[c], y[c], sx, sy) / area;
                    float wb = edge(x[c], y[c], x[a], y[a], sx, sy) / area;
                    float wc = 1.0F - wa - wb;
                    if (wa < 0 || wb < 0 || wc < 0) {
                        continue;
                    }
                    int index = py * size + px;
                    float pz = wa * z[a] + wb * z[b] + wc * z[c];
                    if (pz < depthBuffer[index] - 1.0E-4F) {
                        continue;
                    }
                    float pu = wa * u[a] + wb * u[b] + wc * u[c];
                    float pv = wa * v[a] + wb * v[b] + wc * v[c];
                    int tx = Math.max(0, Math.min(texture.getWidth() - 1, (int) Math.floor(pu)));
                    int ty = Math.max(0, Math.min(texture.getHeight() - 1, (int) Math.floor(pv)));
                    int texel = texture.getRGB(tx, ty);
                    int alpha = texel >>> 24;
                    if (alpha < ALPHA_TEST) {
                        continue;
                    }
                    int tinted = tint(texel, tint, mask, tx, ty, texture);
                    int r = Math.min(255, Math.round((tinted >> 16 & 0xFF) * light));
                    int g = Math.min(255, Math.round((tinted >> 8 & 0xFF) * light));
                    int bl = Math.min(255, Math.round((tinted & 0xFF) * light));
                    pixels[index] = blend(pixels[index], alpha << 24 | r << 16 | g << 8 | bl);
                    if (alpha == 255) {
                        depthBuffer[index] = pz;
                    }
                }
            }
        }

        private static float edge(float ax, float ay, float bx, float by, float px, float py) {
            return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        }

        private static int blend(int dst, int src) {
            int srcAlpha = src >>> 24;
            if (srcAlpha == 255) {
                return src;
            }
            int dstAlpha = dst >>> 24;
            int outAlpha = srcAlpha + dstAlpha * (255 - srcAlpha) / 255;
            if (outAlpha == 0) {
                return 0;
            }
            int r = ((src >> 16 & 0xFF) * srcAlpha + (dst >> 16 & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            int g = ((src >> 8 & 0xFF) * srcAlpha + (dst >> 8 & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            int b = ((src & 0xFF) * srcAlpha + (dst & 0xFF) * dstAlpha * (255 - srcAlpha) / 255) / outAlpha;
            return outAlpha << 24 | r << 16 | g << 8 | b;
        }
    }
}
