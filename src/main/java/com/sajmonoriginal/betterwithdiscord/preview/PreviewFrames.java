package com.sajmonoriginal.betterwithdiscord.preview;

import com.sajmonoriginal.betterwithdiscord.image.MapSnapshot;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class PreviewFrames {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_FRAMES = 300;
    private static final long MAX_PIXELS = 4096L * 4096L;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_DELAY_MS = 100;
    private static final int MIN_DELAY_MS = 20;
    private static final String GIF_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String GIF_STREAM_FORMAT = "javax_imageio_gif_stream_1.0";

    private PreviewFrames() {
    }

    static List<Frame> load(DiscordImages.Entry entry, MapPalette palette) throws IOException {
        if (entry.size() > MAX_BYTES) {
            throw new IOException("The image is larger than " + MAX_BYTES + " bytes");
        }
        byte[] data = download(entry);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new IOException("The image is too large");
                }
                List<Frame> frames = new ArrayList<>();
                if (!"gif".equalsIgnoreCase(reader.getFormatName())) {
                    frames.add(new Frame(palette.dither(fit(reader.read(0))), 0));
                    return frames;
                }
                readGif(reader, palette, frames);
                if (frames.isEmpty()) {
                    throw new IOException("The image has no frames");
                }
                return frames;
            } finally {
                reader.dispose();
            }
        }
    }

    private static void readGif(ImageReader reader, MapPalette palette, List<Frame> frames) throws IOException {
        int width = 0;
        int height = 0;
        IIOMetadata streamMetadata = reader.getStreamMetadata();
        if (streamMetadata != null) {
            Node screen = child(streamMetadata.getAsTree(GIF_STREAM_FORMAT), "LogicalScreenDescriptor");
            if (screen != null) {
                width = intAttribute(screen, "logicalScreenWidth", 0);
                height = intAttribute(screen, "logicalScreenHeight", 0);
            }
        }
        BufferedImage canvas = null;
        for (int index = 0; index < MAX_FRAMES; index++) {
            BufferedImage image;
            try {
                image = reader.read(index);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            Node tree = reader.getImageMetadata(index).getAsTree(GIF_FORMAT);
            Node descriptor = child(tree, "ImageDescriptor");
            Node control = child(tree, "GraphicControlExtension");
            int left = descriptor == null ? 0 : intAttribute(descriptor, "imageLeftPosition", 0);
            int top = descriptor == null ? 0 : intAttribute(descriptor, "imageTopPosition", 0);
            int delay = control == null ? 0 : intAttribute(control, "delayTime", 0) * 10;
            String disposal = control == null ? "none" : attribute(control, "disposalMethod", "none");
            if (canvas == null) {
                int canvasWidth = Math.max(width, left + image.getWidth());
                int canvasHeight = Math.max(height, top + image.getHeight());
                if ((long) canvasWidth * canvasHeight > MAX_PIXELS) {
                    throw new IOException("The image is too large");
                }
                canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            }
            BufferedImage previous = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
            Graphics2D graphics = canvas.createGraphics();
            graphics.drawImage(image, left, top, null);
            graphics.dispose();
            frames.add(new Frame(palette.dither(fit(canvas)), delay < MIN_DELAY_MS ? DEFAULT_DELAY_MS : delay));
            if ("restoreToBackgroundColor".equals(disposal)) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(left, top, image.getWidth(), image.getHeight());
                clear.dispose();
            } else if (previous != null) {
                canvas = previous;
            }
        }
    }

    private static byte[] download(DiscordImages.Entry entry) throws IOException {
        try (InputStream in = entry.file().download().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IOException("The image is larger than " + MAX_BYTES + " bytes");
            }
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading the image", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Could not download the image", e);
        }
    }

    private static BufferedImage fit(BufferedImage source) {
        double scale = Math.min((double) MapSnapshot.SIZE / source.getWidth(), (double) MapSnapshot.SIZE / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = scaled(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        BufferedImage result = new BufferedImage(MapSnapshot.SIZE, MapSnapshot.SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(current, (MapSnapshot.SIZE - width) / 2, (MapSnapshot.SIZE - height) / 2, width, height, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static String attribute(Node node, String name, String fallback) {
        Node attribute = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return attribute == null ? fallback : attribute.getNodeValue();
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(attribute(node, name, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    record Frame(byte[] columns, int delayMs) {
    }
}
