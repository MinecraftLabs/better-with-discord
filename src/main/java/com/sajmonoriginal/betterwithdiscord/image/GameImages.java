package com.sajmonoriginal.betterwithdiscord.image;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.block.BlockIcons;
import com.sajmonoriginal.betterwithdiscord.image.entity.EntityGeometries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public final class GameImages {
    private static final int ICON_SCALE = 4;

    private static volatile GameImages instance;

    private final ClientAssets assets;
    private final BtaFont font;
    private final TooltipRenderer tooltips;
    private final PlayerListRenderer playerList;
    private final BlockIcons blocks;
    private final InventoryRenderer inventories;
    private final ContainerRenderer containers;

    private GameImages(ClientAssets assets, BtaFont font, TooltipRenderer tooltips) {
        this.assets = assets;
        this.font = font;
        this.tooltips = tooltips;
        PlayerSkins skins = new PlayerSkins(assets);
        this.playerList = new PlayerListRenderer(font, skins);
        this.blocks = new BlockIcons(assets);
        SlotRenderer slots = new SlotRenderer(font, item -> iconImage(item, Canvas.SCALE), assets::texture);
        this.containers = new ContainerRenderer(assets, font, slots);
        PlayerModelRenderer players = new PlayerModelRenderer(assets, new EntityGeometries(assets));
        this.inventories = new InventoryRenderer(assets, skins, players, slots, containers);
    }

    public static void load(Path cacheDir, String tooltipStyle) {
        try {
            ClientAssets assets = ClientAssets.open(cacheDir);
            BtaFont font = new BtaFont(assets);
            instance = new GameImages(assets, font, new TooltipRenderer(assets, font, tooltipStyle));
            BetterWithDiscordMod.LOGGER.info("Item images are ready");
        } catch (IOException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.error("Item images are turned off because the BTA client could not be loaded", e);
        }
    }

    public static GameImages get() {
        return instance;
    }

    public static void close() {
        GameImages images = instance;
        instance = null;
        if (images != null) {
            try {
                images.assets.close();
            } catch (IOException e) {
                BetterWithDiscordMod.LOGGER.warn("Could not close the BTA client", e);
            }
        }
    }

    public int color(int chatColor) {
        return font.chatColor(chatColor) & 0xFFFFFF;
    }

    public byte[] tooltip(ItemSnapshot item) {
        BufferedImage below = item.container() == null ? null : containers.render(item.container());
        return png(tooltips.render(item.tooltip(), below));
    }

    public byte[] playerList(List<PlayerListRenderer.Entry> players, int maxPlayers) {
        return png(playerList.render(players, maxPlayers));
    }

    public byte[] inventory(InventorySnapshot inventory) {
        return png(inventories.render(inventory));
    }

    public byte[] icon(ItemSnapshot item) {
        BufferedImage icon = iconImage(item, ICON_SCALE);
        if (icon == null) {
            return null;
        }
        int size = 16 * ICON_SCALE;
        return png(icon.getWidth() < size ? Canvas.upscale(icon, Math.max(1, size / icon.getWidth())) : icon);
    }

    private BufferedImage iconImage(ItemSnapshot item, int scale) {
        if (item.block() != null) {
            return blocks.icon(item.block(), item.meta(), scale);
        }
        for (String icon : item.icons()) {
            BufferedImage texture = assets.texture(icon);
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
