package com.sajmonoriginal.betterwithdiscord.image.block;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.ClientAssets;
import com.sajmonoriginal.betterwithdiscord.image.Raster;
import com.sajmonoriginal.betterwithdiscord.image.block.kind.ModKinds;
import com.sajmonoriginal.betterwithdiscord.image.block.kind.ModelKind;
import com.sajmonoriginal.betterwithdiscord.image.block.kind.ModelKinds;
import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.GuiDisplay;
import com.sajmonoriginal.betterwithdiscord.image.block.model.JsonModelLibrary;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelElement;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelFace;
import com.sajmonoriginal.betterwithdiscord.image.block.scan.DispatchScanner;
import net.minecraft.core.block.Block;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockIcons {
    private static final Set<String> FLAT_MODELS = Set.of(
        "BlockModelCrossedSquares", "BlockModelCrossedSquaresRotatable", "BlockModelDoor", "BlockModelFire",
        "BlockModelFluid", "BlockModelRail", "BlockModelWireRedstone", "BlockModelEmpty");
    private static final double FALLBACK_TEMPERATURE = 1.0;
    private static final double FALLBACK_HUMIDITY = 0.7;

    private final ClientAssets assets;
    private final DispatchScanner scanner;
    private final JsonModelLibrary library;
    private final Map<Block<?>, Optional<ModelKind>> kinds = new ConcurrentHashMap<>();
    private final Map<String, Integer> fallbackColors = new ConcurrentHashMap<>();

    public BlockIcons(ClientAssets assets) {
        this.assets = assets;
        this.scanner = new DispatchScanner(assets);
        this.library = new JsonModelLibrary(assets);
        scanner.scan();
        BetterWithDiscordMod.LOGGER.info("Found models for {} blocks, {} dispatches could not be read", scanner.models().size(),
            scanner.failedDispatches());
    }

    public BufferedImage icon(Block<?> block, int meta, int scale) {
        BlockModel model = model(block, meta);
        if (model == null || model.isEmpty()) {
            return null;
        }
        int tint = fallbackColor(block);
        if (isFlat(block)) {
            String overlay = library.textureVariable(baseId(model), "overlay");
            String key = overlay != null ? overlay : firstTexture(model);
            BufferedImage texture = key == null ? null : assets.texture(key);
            return texture == null ? null : tinted(texture, mask(key), tint);
        }
        return IconRasterizer.render(model, display(model), assets::texture, this::mask, index -> tint, scale);
    }

    private BlockModel model(Block<?> block, int meta) {
        ModelKind kind = kinds.computeIfAbsent(block, b -> Optional.ofNullable(kindFor(b))).orElse(null);
        if (kind == null) {
            return null;
        }
        List<BlockModel> layers = kind.layers(meta);
        if (layers.isEmpty()) {
            return null;
        }
        BlockModel first = layers.get(0);
        if (layers.size() == 1) {
            return first;
        }
        List<ModelElement> elements = new ArrayList<>();
        for (BlockModel layer : layers) {
            elements.addAll(layer.elements());
        }
        return new BlockModel(first.id(), elements);
    }

    private boolean isFlat(Block<?> block) {
        if (scanner.flatItems().contains(block)) {
            return true;
        }
        DispatchScanner.ModelRegistration registration = scanner.models().get(block);
        return registration != null && FLAT_MODELS.contains(registration.type());
    }

    private BufferedImage mask(String texture) {
        return assets.texture(texture + ".cmask");
    }

    private static BufferedImage tinted(BufferedImage texture, BufferedImage mask, int tint) {
        if (tint == -1) {
            return texture;
        }
        BufferedImage result = new BufferedImage(texture.getWidth(), texture.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                result.setRGB(x, y, Raster.tint(texture.getRGB(x, y), tint, mask, x, y, texture));
            }
        }
        return result;
    }

    private static String baseId(BlockModel model) {
        int retextured = model.id().indexOf('#');
        return retextured >= 0 ? model.id().substring(0, retextured) : model.id();
    }

    private GuiDisplay display(BlockModel model) {
        String id = baseId(model);
        if (id.startsWith(BetterWithDiscordMod.MOD_ID + ":")) {
            return GuiDisplay.BLOCK;
        }
        GuiDisplay display = library.guiDisplay(id);
        return display == null ? GuiDisplay.IDENTITY : display;
    }

    private static String firstTexture(BlockModel model) {
        for (ModelElement element : model.elements()) {
            for (ModelFace face : element.faces().values()) {
                if (face.texture() != null) {
                    return face.texture();
                }
            }
        }
        return null;
    }

    private int fallbackColor(Block<?> block) {
        DispatchScanner.ColorRegistration registration = scanner.colors().get(block);
        if (registration == null || registration.colorizer() == null) {
            return -1;
        }
        return fallbackColors.computeIfAbsent(registration.colorizer(), colorizer -> {
            BufferedImage map = assets.image("assets/minecraft/textures/colormap/" + colorizer + "/default.png");
            if (map == null) {
                return -1;
            }
            double humidity = FALLBACK_HUMIDITY * FALLBACK_TEMPERATURE;
            int x = (int) ((1.0 - FALLBACK_TEMPERATURE) * 255.0);
            int y = (int) ((1.0 - humidity) * 255.0);
            return map.getRGB(x * map.getWidth() / 256, y * map.getHeight() / 256) | 0xFF000000;
        });
    }

    private ModelKind kindFor(Block<?> block) {
        DispatchScanner.ModelRegistration registration = scanner.models().get(block);
        if (registration != null) {
            ModelKind modded = ModKinds.create(registration.modelClass(), registration.modelArguments(), library, block);
            if (modded != null) {
                return modded;
            }
            ModelKind kind = ModelKinds.create(registration.type(), registration.arguments(), registration.allTextures(),
                registration.sideTextures(), library, block);
            if (kind != null) {
                return kind;
            }
        }
        BlockModel own = library.load(block.namespaceId().namespace() + ":" + block.namespaceId().value());
        if (own != null && !own.isEmpty()) {
            List<BlockModel> layers = List.of(own);
            return meta -> layers;
        }
        return null;
    }
}
