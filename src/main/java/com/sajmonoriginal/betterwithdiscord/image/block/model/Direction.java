package com.sajmonoriginal.betterwithdiscord.image.block.model;

import java.util.Locale;

public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public final int x;
    public final int y;
    public final int z;

    Direction(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Direction byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
