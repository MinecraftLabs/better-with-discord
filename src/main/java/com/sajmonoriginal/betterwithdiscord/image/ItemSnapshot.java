package com.sajmonoriginal.betterwithdiscord.image;

import net.minecraft.core.block.Block;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.world.World;

import java.util.ArrayList;
import java.util.List;

public record ItemSnapshot(String tooltip, String name, int colorIndex, int count, List<String> icons, Block<?> block, int meta) {

    public static ItemSnapshot of(ItemStack stack) {
        return of(stack, null);
    }

    public static ItemSnapshot of(ItemStack stack, World world) {
        StringBuilder text = new StringBuilder();
        text.append(TextFormatting.get(stack.getDisplayColor())).append(stack.getDisplayName()).append(TextFormatting.RESET);
        if (stack.getData().containsKey("tileEntityData")) {
            text.append(' ').append(TextFormatting.MAGENTA).append('•').append(TextFormatting.RESET);
        }
        String description = stack.getItemDescription();
        if (!description.equals(stack.getItemKey() + ".desc") && !description.isBlank()) {
            text.append('\n').append(formatDescription(description));
        }
        Item item = stack.getItem();
        List<String> icons = new ArrayList<>();
        if (!(item instanceof ItemBlock)) {
            String special = ItemIcons.special(stack, world);
            if (special != null) {
                icons.add(special);
            }
            icons.add(item.namespaceID.toString());
            icons.add(item.namespaceID.namespace() + ":item/" + item.getKey().substring(item.getKey().indexOf('.') + 1).replace('.', '_'));
        }
        Block<?> block = item instanceof ItemBlock<?> itemBlock ? itemBlock.getBlock() : null;
        return new ItemSnapshot(text.toString(), TextFormatting.removeAllFormatting(stack.getDisplayName()), stack.getDisplayColor(), stack.stackSize, List.copyOf(icons),
                block, stack.getMetadata());
    }

    private static String formatDescription(String description) {
        StringBuilder text = new StringBuilder();
        text.append(TextFormatting.LIGHT_GRAY);
        int lineLength = 0;
        for (char c : description.toCharArray()) {
            if (c == ' ') {
                if (lineLength > 16) {
                    lineLength = 0;
                    text.append('\n').append(TextFormatting.LIGHT_GRAY);
                } else {
                    text.append(c);
                }
            } else {
                lineLength++;
                text.append(c);
            }
        }
        return text.toString();
    }
}
