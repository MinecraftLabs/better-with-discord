package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import net.minecraft.core.block.BlockLogicFullyRotatable;
import net.minecraft.core.block.BlockLogicVeryRotatable;
import net.minecraft.core.util.helper.Direction;

final class Facing {
    private Facing() {
    }

    static boolean veryRotatable(int meta) {
        return isSide(BlockLogicVeryRotatable.metaToDirection(meta));
    }

    static boolean fullyRotatable(int meta) {
        return isSide(BlockLogicFullyRotatable.metaToDirection(meta));
    }

    static boolean wallTorch(int meta) {
        int attachment = meta & 7;
        return attachment >= 1 && attachment <= 4;
    }

    private static boolean isSide(Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH, WEST, EAST -> true;
            default -> false;
        };
    }
}
