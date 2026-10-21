package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import net.minecraft.core.block.BlockLogicAxisAligned;
import net.minecraft.core.block.BlockLogicFullyRotatable;
import net.minecraft.core.block.BlockLogicVeryRotatable;
import net.minecraft.core.util.helper.Direction;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.util.helper.Sides;

final class LegacySides {
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int EAST = 2;
    private static final int SOUTH = 3;
    private static final int NORTH = 4;
    private static final int WEST = 5;

    private LegacySides() {
    }

    static Side source(String className, int meta, Side side) {
        return switch (className) {
            case "BlockModelRotatable" -> lookup(Sides.orientationLookUp, 6 * meta + side.id);
            case "BlockModelHorizontalRotation" -> lookup(Sides.orientationLookUpHorizontal, 6 * Math.min(meta & 7, 5) + side.id);
            case "BlockModelAxisAligned" -> lookup(Sides.orientationLookUpXYZAligned, 6 * (meta & 3) + side.id);
            case "BlockModelFullyRotatable" -> fullyRotatable(meta, side);
            case "BlockModelVeryRotatable" -> veryRotatable(meta, side);
            default -> side;
        };
    }

    static int uvRotation(String className, int meta, Side side) {
        int[] rotations = switch (className) {
            case "BlockModelAxisAligned" -> axisAligned(meta);
            case "BlockModelVeryRotatable" -> veryRotatableUv(meta);
            case "BlockModelFullyRotatable" -> fullyRotatableUv(meta);
            default -> null;
        };
        if (rotations == null) {
            return 0;
        }
        return switch (side) {
            case TOP -> rotations[TOP];
            case BOTTOM -> rotations[BOTTOM];
            case NORTH -> rotations[EAST];
            case SOUTH -> rotations[WEST];
            case WEST -> rotations[NORTH];
            case EAST -> rotations[SOUTH];
            default -> 0;
        };
    }

    private static int[] axisAligned(int meta) {
        return switch (BlockLogicAxisAligned.metaToAxis(meta)) {
            case Z -> rotations(0, 0, 0, 1, 1, 0);
            case X -> rotations(1, 1, 1, 0, 0, 1);
            default -> rotations(0, 0, 0, 0, 0, 0);
        };
    }

    private static int[] veryRotatableUv(int meta) {
        return switch (BlockLogicVeryRotatable.metaToDirection(meta)) {
            case DOWN, UP -> rotations(0, 0, 1, 1, 2, 2);
            case NORTH, SOUTH -> rotations(1, 1, 0, 0, 0, 0);
            default -> rotations(0, 0, 0, 0, 0, 0);
        };
    }

    private static int[] fullyRotatableUv(int meta) {
        return switch (BlockLogicFullyRotatable.metaToDirection(meta)) {
            case DOWN -> rotations(0, 0, 3, 3, 3, 3);
            case NORTH -> rotations(0, 0, 0, 1, 2, 0);
            case SOUTH -> rotations(3, 3, 0, 2, 1, 0);
            case WEST -> rotations(2, 1, 1, 0, 0, 2);
            case EAST -> rotations(1, 2, 2, 0, 0, 1);
            default -> rotations(0, 0, 0, 0, 0, 0);
        };
    }

    private static int[] rotations(int top, int bottom, int east, int south, int north, int west) {
        int[] values = new int[6];
        values[TOP] = top;
        values[BOTTOM] = bottom;
        values[EAST] = east;
        values[SOUTH] = south;
        values[NORTH] = north;
        values[WEST] = west;
        return values;
    }

    private static Side lookup(int[] table, int index) {
        return index >= 0 && index < table.length ? Side.fromId(table[index]) : null;
    }

    private static Side fullyRotatable(int meta, Side side) {
        Direction direction = BlockLogicFullyRotatable.metaToDirection(meta);
        if (side == direction.side()) {
            return Side.TOP;
        }
        return side.direction() != direction.opposite() ? Side.NORTH : Side.BOTTOM;
    }

    private static Side veryRotatable(int meta, Side side) {
        return switch (BlockLogicVeryRotatable.metaToDirection(meta)) {
            case SOUTH -> side;
            case EAST -> side.direction().rotateY(3).side();
            case NORTH -> side.direction().rotateY(2).side();
            case WEST -> side.direction().rotateY(1).side();
            case UP -> switch (side) {
                case NORTH -> Side.TOP;
                case SOUTH -> Side.BOTTOM;
                case TOP -> Side.SOUTH;
                case BOTTOM -> Side.NORTH;
                default -> side;
            };
            case DOWN -> switch (side) {
                case NORTH -> Side.TOP;
                case SOUTH -> Side.BOTTOM;
                case TOP -> Side.NORTH;
                case BOTTOM -> Side.SOUTH;
                default -> side;
            };
            default -> Side.BOTTOM;
        };
    }
}
