package com.sajmonoriginal.betterwithdiscord.image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PlayerSkins {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String DEFAULT_SKIN = "assets/minecraft/textures/entity/char/0.png";
    private static final int TIMEOUT_MS = 5000;
    private static final long CACHE_NANOS = TimeUnit.MINUTES.toNanos(30);

    private final ClientAssets assets;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public PlayerSkins(ClientAssets assets) {
        this.assets = assets;
    }

    public BufferedImage skin(UUID uuid) {
        Cached cached = uuid == null ? null : cache.get(uuid);
        if (cached != null && System.nanoTime() - cached.loaded < CACHE_NANOS) {
            return cached.skin;
        }
        BufferedImage skin = uuid == null ? null : download(uuid);
        if (skin == null) {
            skin = assets.image(DEFAULT_SKIN);
        }
        if (uuid != null && skin != null) {
            cache.put(uuid, new Cached(skin, System.nanoTime()));
        }
        return skin;
    }

    private static BufferedImage download(UUID uuid) {
        try {
            HttpURLConnection profile = open(PROFILE_URL + uuid.toString().replace("-", ""));
            if (profile.getResponseCode() != 200) {
                return null;
            }
            JsonObject json;
            try (InputStreamReader reader = new InputStreamReader(profile.getInputStream(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
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
                try (InputStream in = open(skin.get("url").getAsString()).getInputStream()) {
                    return ImageIO.read(in);
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not download the skin of {}", uuid, e);
            return null;
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection;
    }

    private record Cached(BufferedImage skin, long loaded) {
    }
}
