package com.sajmonoriginal.betterwithdiscord.image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PlayerSkins {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String DEFAULT_SKIN = "assets/minecraft/textures/entity/char/0.png";
    private static final String MASK_STEVE = "assets/minecraft/textures/entity/char/mask_steve.png";
    private static final String MASK_ALEX = "assets/minecraft/textures/entity/char/mask_alex.png";
    private static final int SIZE = 64;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long CACHE_NANOS = TimeUnit.MINUTES.toNanos(30);
    private static final int[][] LEGACY_COPIES = {
        {24, 52, 20, 64, 4, 20, 8, 32}, {32, 52, 28, 64, 12, 20, 16, 32}, {28, 52, 24, 64, 0, 20, 4, 32},
        {20, 52, 16, 64, 8, 20, 12, 32}, {24, 48, 20, 52, 4, 16, 8, 20}, {28, 48, 24, 52, 8, 16, 12, 20},
        {40, 52, 36, 64, 44, 20, 48, 32}, {48, 52, 44, 64, 52, 20, 56, 32}, {44, 52, 40, 64, 40, 20, 44, 32},
        {36, 52, 32, 64, 48, 20, 52, 32}, {40, 48, 36, 52, 44, 16, 48, 20}, {44, 48, 40, 52, 48, 16, 52, 20}
    };
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final ClientAssets assets;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public PlayerSkins(ClientAssets assets) {
        this.assets = assets;
    }

    public Skin skin(UUID uuid) {
        Cached cached = uuid == null ? null : cache.get(uuid);
        if (cached != null && System.nanoTime() - cached.loaded < CACHE_NANOS) {
            return cached.skin;
        }
        Skin skin = uuid == null ? null : download(uuid);
        if (skin == null) {
            BufferedImage fallback = assets.image(DEFAULT_SKIN);
            skin = fallback == null ? null : new Skin(parse(fallback, false), false);
        }
        if (uuid != null && skin != null) {
            cache.put(uuid, new Cached(skin, System.nanoTime()));
        }
        return skin;
    }

    private BufferedImage parse(BufferedImage image, boolean slim) {
        int unit = Math.max(1, image.getWidth() / SIZE);
        BufferedImage skin = new BufferedImage(SIZE * unit, SIZE * unit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = skin.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        if (image.getHeight() * 2 == image.getWidth()) {
            for (int[] copy : LEGACY_COPIES) {
                graphics.drawImage(image, copy[0] * unit, copy[1] * unit, copy[2] * unit, copy[3] * unit,
                    copy[4] * unit, copy[5] * unit, copy[6] * unit, copy[7] * unit, null);
            }
        }
        graphics.dispose();
        BufferedImage mask = assets.image(slim ? MASK_ALEX : MASK_STEVE);
        if (mask != null) {
            for (int y = 0; y < skin.getHeight(); y++) {
                for (int x = 0; x < skin.getWidth(); x++) {
                    int opaque = mask.getRGB(x * mask.getWidth() / skin.getWidth(), y * mask.getHeight() / skin.getHeight()) & 0xFF;
                    skin.setRGB(x, y, skin.getRGB(x, y) | opaque << 24);
                }
            }
        }
        return skin;
    }

    private Skin download(UUID uuid) {
        try {
            JsonObject json = JsonParser.parseString(new String(get(PROFILE_URL + uuid.toString().replace("-", "")), StandardCharsets.UTF_8))
                .getAsJsonObject();
            for (JsonElement property : json.getAsJsonArray("properties")) {
                JsonObject entry = property.getAsJsonObject();
                if (!"textures".equals(entry.get("name").getAsString())) {
                    continue;
                }
                String decoded = new String(Base64.getDecoder().decode(entry.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject skin = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                if (skin == null) {
                    return null;
                }
                boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(get(skin.get("url").getAsString())));
                return image == null ? null : new Skin(parse(image, slim), slim);
            }
            return null;
        } catch (IOException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not download the skin of {}", uuid, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static byte[] get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException(url + " answered " + response.statusCode());
        }
        return response.body();
    }

    public record Skin(BufferedImage image, boolean slim) {
    }

    private record Cached(Skin skin, long loaded) {
    }
}
