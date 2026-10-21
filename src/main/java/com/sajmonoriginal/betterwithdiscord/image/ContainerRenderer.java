package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.image.BufferedImage;

final class ContainerRenderer {
    private static final int PANEL_WIDTH = 176;
    private static final int WORN_TAB_WIDTH = 36;
    private static final int TITLE_Y = 6;
    private static final int WORN_TITLE_Y = 7;
    private static final int TAB_X = 174;
    private static final int TAB_Y = 13;
    private static final int TAB_WIDTH = 25;
    private static final int TAB_HEIGHT = 26;
    private static final int ROW_HEIGHT = 18;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private final ClientAssets assets;
    private final BtaFont font;
    private final SlotRenderer slots;

    ContainerRenderer(ClientAssets assets, BtaFont font, SlotRenderer slots) {
        this.assets = assets;
        this.font = font;
        this.slots = slots;
    }

    BufferedImage render(ContainerSnapshot container) {
        Canvas canvas = new Canvas(width(container), height(container));
        draw(canvas, container, 0, 0);
        return canvas.toImage();
    }

    static int width(ContainerSnapshot container) {
        return container.attachment() ? TAB_X + tabWidth(container) : PANEL_WIDTH;
    }

    static int height(ContainerSnapshot container) {
        return container.rows() * ROW_HEIGHT + 17 + 7;
    }

    private static int tabWidth(ContainerSnapshot container) {
        return container.worn() ? WORN_TAB_WIDTH : TAB_WIDTH;
    }

    void draw(Canvas canvas, ContainerSnapshot container, int x, int y) {
        int height = height(container);
        BufferedImage texture = assets.image(container.texture());
        if (texture != null) {
            double unit = texture.getWidth() / 256.0;
            canvas.drawTexture(texture, 0, 0, PANEL_WIDTH * unit, height * unit, x, y, x + PANEL_WIDTH, y + height);
            if (container.attachment()) {
                int tab = tabWidth(container);
                canvas.drawTexture(texture, TAB_X * unit, TAB_Y * unit, (TAB_X + tab) * unit, (TAB_Y + TAB_HEIGHT) * unit,
                    x + TAB_X, y + TAB_Y, x + TAB_X + tab, y + TAB_Y + TAB_HEIGHT);
            }
        }
        font.draw(canvas, container.title(), x + 8, y + (container.worn() ? WORN_TITLE_Y : TITLE_Y), TITLE_COLOR, false);
        for (InventorySnapshot.Slot slot : container.slots()) {
            slots.draw(canvas, slot, x, y);
        }
    }
}
