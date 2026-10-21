package com.sajmonoriginal.betterwithdiscord.image;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.block.BlockIcons;

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

    private GameImages(ClientAssets assets, BtaFont font, TooltipRenderer tooltips) {
        this.assets = assets;
        this.font = font;
        this.tooltips = tooltips;
        this.playerList = new PlayerListRenderer(font, new PlayerSkins(assets));
        this.blocks = new BlockIcons(assets);
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
        return png(tooltips.render(item.tooltip()));
    }

    public byte[] playerList(List<PlayerListRenderer.Entry> players, int maxPlayers) {
        return png(playerList.render(players, maxPlayers));
    }

    public byte[] icon(ItemSnapshot item) {
        if (item.block() != null) {
            BufferedImage block = blocks.icon(item.block(), item.meta(), ICON_SCALE);
            if (block != null) {
                return png(block.getWidth() < 16 * ICON_SCALE ? Canvas.upscale(block, 16 * ICON_SCALE / block.getWidth()) : block);
            }
            return null;
        }
        for (String icon : item.icons()) {
            BufferedImage texture = assets.texture(icon);
            if (texture != null) {
                return png(Canvas.upscale(texture, ICON_SCALE));
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
