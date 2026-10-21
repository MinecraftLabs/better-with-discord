package com.sajmonoriginal.betterwithdiscord.image;

import net.minecraft.core.net.command.TextFormatting;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

public final class PlayerListRenderer {
    private static final int BACKGROUND = 0xA8080808;
    private static final int CELL_BORDER = 0xFFA5A5A5;
    private static final int ROWS_PER_COLUMN = 8;
    private static final int OUTER_PADDING = 12;
    private static final int GAP = 8;
    private static final int HEAD = 8;
    private static final int HEAD_GAP = 2;
    private static final int NAME_SCORE_SPACE = 4;

    private final BtaFont font;
    private final PlayerSkins skins;

    public PlayerListRenderer(BtaFont font, PlayerSkins skins) {
        this.font = font;
        this.skins = skins;
    }

    public BufferedImage render(List<Entry> players, int maxPlayers) {
        String title = "Online Players (" + players.size() + "/" + maxPlayers + ")";
        int titleWidth = width(title);
        int longestName = 0;
        int longestScore = 0;
        for (Entry player : players) {
            longestName = Math.max(longestName, width(player.name()));
            longestScore = Math.max(longestScore, width(player.score()));
        }
        int columnWidth = HEAD + HEAD_GAP + longestName + longestScore + NAME_SCORE_SPACE;
        int columns = Math.max(1, (int) Math.ceil(players.size() / (double) ROWS_PER_COLUMN));
        int rows = (int) Math.ceil(players.size() / (double) columns);
        int listWidth = players.isEmpty() ? 0 : columns * columnWidth + GAP * (columns - 1);
        int width = Math.max(listWidth, titleWidth) + OUTER_PADDING * 2;
        int listTop = OUTER_PADDING + BtaFont.LINE_HEIGHT + GAP;
        int height = players.isEmpty() ? OUTER_PADDING * 2 + 8 : listTop + rows * 8 + GAP * (rows - 1) + OUTER_PADDING;

        Canvas canvas = new Canvas(width, height);
        canvas.fill(0, 0, width, height, BACKGROUND);
        font.draw(canvas, title, (width - titleWidth) / 2.0, OUTER_PADDING, 0xFFFFFFFF, true);
        int listLeft = (width - listWidth) / 2;
        for (int i = 0; i < players.size(); i++) {
            Entry player = players.get(i);
            int column = i % columns;
            int row = i / columns;
            int x = listLeft + column * (columnWidth + GAP);
            int y = listTop + row * (8 + GAP);
            canvas.fill(x - 2, y - 2, x + columnWidth + 2, y + 10, CELL_BORDER);
            canvas.fill(x - 1, y - 1, x + columnWidth + 1, y + 9, BACKGROUND);
            PlayerSkins.Skin skin = skins.skin(player.uuid());
            drawHead(canvas, skin == null ? null : skin.image(), x, y);
            font.draw(canvas, player.name(), x + HEAD + HEAD_GAP, y, 0xFFFFFFFF, true);
            font.draw(canvas, TextFormatting.YELLOW + player.score(), x + columnWidth - width(player.score()), y, 0xFFFFFFFF, true);
        }
        return canvas.toImage();
    }

    private int width(String text) {
        return (int) Math.ceil(font.measure(List.of(text)));
    }

    private static void drawHead(Canvas canvas, BufferedImage skin, int x, int y) {
        if (skin == null) {
            return;
        }
        double unit = skin.getWidth() / 64.0;
        canvas.drawTexture(skin, 8 * unit, 8 * unit, 16 * unit, 16 * unit, x, y, x + HEAD, y + HEAD);
        canvas.drawTexture(skin, 40 * unit, 8 * unit, 48 * unit, 16 * unit, x, y, x + HEAD, y + HEAD);
    }

    public record Entry(String name, String score, UUID uuid) {
    }
}
