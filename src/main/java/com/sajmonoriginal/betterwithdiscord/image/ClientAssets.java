package com.sajmonoriginal.betterwithdiscord.image;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.core.BuildChannel;
import net.minecraft.core.Version;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ClientAssets implements AutoCloseable {
    private static final String DOWNLOADS_URL = "https://downloads.betterthanadventure.net/bta-client/";
    private static final int TIMEOUT_MS = 30000;
    private static final Set<String> SKIPPED_MODS = Set.of("java", "minecraft", "fabricloader", "mixinextras", BetterWithDiscordMod.MOD_ID);

    private final ZipFile client;
    private final List<ModArchive> mods;
    private final Map<String, Optional<BufferedImage>> images = new ConcurrentHashMap<>();

    private ClientAssets(ZipFile client, List<ModArchive> mods) {
        this.client = client;
        this.mods = mods;
    }

    public static ClientAssets open(Path cacheDir) throws IOException {
        Path jar = download(cacheDir);
        List<ModArchive> mods = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (SKIPPED_MODS.contains(id) || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) {
                continue;
            }
            for (Path origin : mod.getOrigin().getPaths()) {
                if (Files.isDirectory(origin)) {
                    mods.add(new ModArchive(id, null, origin));
                } else if (Files.isRegularFile(origin)) {
                    mods.add(new ModArchive(id, new ZipFile(origin.toFile()), null));
                }
            }
        }
        return new ClientAssets(new ZipFile(jar.toFile()), mods);
    }

    public byte[] read(String path) throws IOException {
        ZipEntry entry = client.getEntry(path);
        if (entry != null) {
            try (InputStream in = client.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
        for (ModArchive mod : mods) {
            byte[] data = mod.read(path);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    public byte[] readClient(String path) throws IOException {
        ZipEntry entry = client.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStream in = client.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    public List<ModArchive> mods() {
        return mods;
    }

    public BufferedImage image(String path) {
        return images.computeIfAbsent(path, p -> {
            try {
                byte[] data = read(p);
                return data == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(data)));
            } catch (IOException e) {
                BetterWithDiscordMod.LOGGER.warn("Could not read {}", p, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public BufferedImage texture(String key) {
        int colon = key.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : key.substring(0, colon);
        BufferedImage image = image("assets/" + namespace + "/textures/" + key.substring(colon + 1) + ".png");
        if (image == null || image.getHeight() <= image.getWidth()) {
            return image;
        }
        return image.getSubimage(0, 0, image.getWidth(), image.getWidth());
    }

    @Override
    public void close() throws IOException {
        client.close();
        for (ModArchive mod : mods) {
            if (mod.jar() != null) {
                mod.jar().close();
            }
        }
    }

    private static Path download(Path cacheDir) throws IOException {
        String version = FabricLoader.getInstance().getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse(Version.VERSION);
        Path jar = cacheDir.resolve("bta-client-" + version + ".jar");
        if (Files.isRegularFile(jar)) {
            return jar;
        }

        Files.createDirectories(cacheDir);
        BuildChannel channel = Version.BUILD_CHANNEL;
        String folder = channel == BuildChannel.NIGHTLY ? version : "v" + version;
        String base = DOWNLOADS_URL + channel.name().toLowerCase(Locale.ROOT) + "/" + folder + "/";
        BetterWithDiscordMod.LOGGER.info("Downloading the BTA {} client for its textures and fonts", version);
        Path partial = cacheDir.resolve(jar.getFileName() + ".part");
        try {
            JsonObject client;
            try (Reader reader = new InputStreamReader(open(base + "manifest.json"), StandardCharsets.UTF_8)) {
                client = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("downloads").getAsJsonObject("client");
            }
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            try (InputStream in = new DigestInputStream(open(client.get("url").getAsString()), sha1)) {
                Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            String expected = client.get("sha1").getAsString();
            if (!expected.equalsIgnoreCase(HexFormat.of().formatHex(sha1.digest()))) {
                throw new IOException("The downloaded BTA client does not match its checksum");
            }
            Files.move(partial, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static InputStream open(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection.getInputStream();
    }

    public record ModArchive(String modId, ZipFile jar, Path directory) {
        public byte[] read(String path) throws IOException {
            if (jar != null) {
                ZipEntry entry = jar.getEntry(path);
                if (entry == null) {
                    return null;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
            Path file = directory.resolve(path);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        }

        public void forEachClass(ClassConsumer consumer) throws IOException {
            if (jar != null) {
                for (ZipEntry entry : Collections.list(jar.entries())) {
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            consumer.accept(in.readAllBytes());
                        }
                    }
                }
                return;
            }
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    consumer.accept(Files.readAllBytes(file));
                }
            }
        }
    }

    public interface ClassConsumer {
        void accept(byte[] bytes) throws IOException;
    }
}
