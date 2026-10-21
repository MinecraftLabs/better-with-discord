package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.image.BufferedImage;

final class InventoryRenderer {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final String VANILLA_TEXTURE = "assets/minecraft/textures/gui/container/inventory.png";
    private static final String AETHER_TEXTURE = "assets/aether/textures/gui/container/inventory.png";
    private static final int SLOT_FRAME = 7;
    private static final int CRAFTING_LEFT = 80;
    private static final int AETHER_CRAFTING_LEFT = 98;
    private static final int CRAFTING_TOP = 6;
    private static final int CRAFTING_RIGHT = 170;
    private static final int CRAFTING_BOTTOM = 81;
    private static final int PANEL_SAMPLE_X = 150;
    private static final int PANEL_SAMPLE_Y = 12;
    private static final int FIGURE_LEFT = 26;
    private static final int FIGURE_TOP = 8;
    private static final int FIGURE_WIDTH = 52;
    private static final int FIGURE_HEIGHT = 70;
    private static final int FIGURE_CENTER_X = 25;
    private static final int FIGURE_FEET_Y = 68;
    private static final double FIGURE_SCALE = 30.0 / 16.0;
    private static final int BACKPACK_OVERLAP = 3;

    private final ClientAssets assets;
    private final PlayerSkins skins;
    private final PlayerModelRenderer players;
    private final SlotRenderer slots;
    private final ContainerRenderer containers;

    InventoryRenderer(ClientAssets assets, PlayerSkins skins, PlayerModelRenderer players, SlotRenderer slots, ContainerRenderer containers) {
        this.assets = assets;
        this.skins = skins;
        this.players = players;
        this.slots = slots;
        this.containers = containers;
    }

    BufferedImage render(InventorySnapshot inventory) {
        ContainerSnapshot backpack = inventory.wornBackpack();
        int width = backpack == null ? WIDTH : Math.max(WIDTH, ContainerRenderer.width(backpack));
        int height = backpack == null ? HEIGHT : HEIGHT - BACKPACK_OVERLAP + ContainerRenderer.height(backpack);
        Canvas canvas = new Canvas(width, height);
        if (backpack != null) {
            containers.draw(canvas, backpack, 0, HEIGHT - BACKPACK_OVERLAP);
        }
        BufferedImage vanilla = assets.image(VANILLA_TEXTURE);
        BufferedImage background = inventory.aether() ? assets.image(AETHER_TEXTURE) : vanilla;
        if (background != null) {
            double unit = background.getWidth() / 256.0;
            canvas.drawTexture(background, 0, 0, WIDTH * unit, HEIGHT * unit, 0, 0, WIDTH, HEIGHT);
            int panel = background.getRGB((int) (PANEL_SAMPLE_X * unit), (int) (PANEL_SAMPLE_Y * unit)) | 0xFF000000;
            canvas.fill(inventory.aether() ? AETHER_CRAFTING_LEFT : CRAFTING_LEFT, CRAFTING_TOP, CRAFTING_RIGHT, CRAFTING_BOTTOM, panel);
        }
        if (inventory.offhand() && vanilla != null) {
            double unit = vanilla.getWidth() / 256.0;
            canvas.drawTexture(vanilla, SLOT_FRAME * unit, SLOT_FRAME * unit, (SLOT_FRAME + 18) * unit, (SLOT_FRAME + 18) * unit,
                HandsCompat.SLOT_X - 1, HandsCompat.SLOT_Y - 1, HandsCompat.SLOT_X + 17, HandsCompat.SLOT_Y + 17);
        }
        BufferedImage figure = players.render(skins.skin(inventory.uuid()), inventory,
            FIGURE_WIDTH * Canvas.SCALE, FIGURE_HEIGHT * Canvas.SCALE,
            FIGURE_CENTER_X * Canvas.SCALE, FIGURE_FEET_Y * Canvas.SCALE, FIGURE_SCALE * Canvas.SCALE);
        canvas.drawTexture(figure, 0, 0, figure.getWidth(), figure.getHeight(),
            FIGURE_LEFT, FIGURE_TOP, FIGURE_LEFT + FIGURE_WIDTH, FIGURE_TOP + FIGURE_HEIGHT);
        for (InventorySnapshot.Slot slot : inventory.slots()) {
            slots.draw(canvas, slot);
        }
        return canvas.toImage();
    }
}
