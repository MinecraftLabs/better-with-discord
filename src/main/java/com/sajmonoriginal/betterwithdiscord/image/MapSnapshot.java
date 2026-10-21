package com.sajmonoriginal.betterwithdiscord.image;

import net.minecraft.core.block.material.MaterialColor;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.maps.ItemMapSavedData;
import net.minecraft.core.world.saveddata.maps.MapWaypoint;

import java.util.ArrayList;
import java.util.List;

public record MapSnapshot(int[] pixels, List<Marker> decorations, List<Waypoint> waypoints) {
    public static final int SIZE = 128;

    static MapSnapshot of(ItemStack stack, World world) {
        if (world == null || stack.getItem() != Items.MAP) {
            return null;
        }
        ItemMapSavedData data = Items.MAP.getOrCreateSavedData(stack, world);
        return data == null || data.colors == null ? null : of(data);
    }

    static MapSnapshot of(ItemMapSavedData data) {
        int[] pixels = new int[SIZE * SIZE];
        for (int i = 0; i < pixels.length && i < data.colors.length; i++) {
            int colorIndex = data.colors[i];
            if (colorIndex >> 2 == 0) {
                pixels[i] = ((i + i / SIZE & 1) * 8 + 16) << 24;
                continue;
            }
            pixels[i] = color(colorIndex);
        }
        List<Marker> decorations = new ArrayList<>();
        for (ItemMapSavedData.MapDecoration decoration : data.decorations) {
            decorations.add(new Marker(decoration.type, decoration.x, decoration.y, decoration.rot));
        }
        List<Waypoint> waypoints = new ArrayList<>();
        for (MapWaypoint waypoint : data.mapWaypoints) {
            waypoints.add(new Waypoint(waypoint.mapCoordX, waypoint.mapCoordZ, waypoint.colors.clone()));
        }
        return new MapSnapshot(pixels, List.copyOf(decorations), List.copyOf(waypoints));
    }

    public static int color(int colorIndex) {
        int color = MaterialColor.getColorFromIndex(colorIndex >> 2);
        int brightness = switch (colorIndex & 3) {
            case 0 -> 180;
            case 2 -> 255;
            default -> 220;
        };
        int red = (color >> 16 & 0xFF) * brightness / 255;
        int green = (color >> 8 & 0xFF) * brightness / 255;
        int blue = (color & 0xFF) * brightness / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public record Marker(int type, int x, int y, int rotation) {
    }

    public record Waypoint(int x, int z, byte[] colors) {
    }
}
