package com.sajmonoriginal.betterwithdiscord.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;

final class SlotRenderer {
    private final BtaFont font;
    private final Function<ItemSnapshot, BufferedImage> icons;
    private final Function<String, BufferedImage> textures;

    SlotRenderer(BtaFont font, Function<ItemSnapshot, BufferedImage> icons, Function<String, BufferedImage> textures) {
        this.font = font;
        this.icons = icons;
        this.textures = textures;
    }

    void draw(Canvas canvas, InventorySnapshot.Slot slot) {
        draw(canvas, slot, 0, 0);
    }

    void draw(Canvas canvas, InventorySnapshot.Slot slot, int offsetX, int offsetY) {
        int x = slot.x() + offsetX;
        int y = slot.y() + offsetY;
        ItemSnapshot item = slot.item();
        if (item == null) {
            BufferedImage outline = slot.outline() == null ? null : textures.apply(slot.outline());
            if (outline != null) {
                canvas.drawTexture(outline, 0, 0, outline.getWidth(), outline.getHeight(), x, y, x + 16, y + 16);
            }
            return;
        }
        BufferedImage icon = icons.apply(item);
        if (icon != null) {
            canvas.drawTexture(icon, 0, 0, icon.getWidth(), icon.getHeight(), x, y, x + 16, y + 16);
        }
        if (item.count() != 1) {
            String count = String.valueOf(item.count());
            int width = (int) Math.ceil(font.measure(List.of(count)));
            font.draw(canvas, count, x + 19 - 2 - width, y + 6 + 3, 0xFFFFFFFF, true);
        }
        if (item.durabilityWidth() >= 0) {
            int progress = item.durabilityProgress();
            int foreground = Color.HSBtoRGB(progress / 255.0F / 3.0F, 1.0F, 1.0F) | 0xFF000000;
            int background = (255 - progress) / 4 << 16 | 16128 | 0xFF000000;
            canvas.fill(x + 2, y + 13, x + 2 + 13, y + 13 + 2, 0xFF000000);
            canvas.fill(x + 2, y + 13, x + 2 + 12, y + 13 + 1, background);
            canvas.fill(x + 2, y + 13, x + 2 + item.durabilityWidth(), y + 13 + 1, foreground);
        }
    }
}
