package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.Direction;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelLibrary;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicBed;
import net.minecraft.core.block.BlockLogicChest;
import net.minecraft.core.block.BlockLogicConduit;
import net.minecraft.core.block.BlockLogicFarmland;
import net.minecraft.core.block.BlockLogicFlowerStackable;
import net.minecraft.core.block.BlockLogicPortal;
import net.minecraft.core.block.BlockLogicRail;
import net.minecraft.core.block.BlockLogicRepeater;
import net.minecraft.core.block.BlockLogicSpikes;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.logic.RailDirection;
import net.minecraft.core.block.piston.PistonCommon;
import net.minecraft.core.util.helper.Side;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class ModelKinds {
    private static final String BLOCK = "minecraft:block/";
    private static final ModelKind EMPTY = meta -> List.of();

    private ModelKinds() {
    }

    public static ModelKind create(String className, List<Object> args, String allTextures, Map<Direction, String> sideTextures,
                                   ModelLibrary models, Block<?> block) {
        if (className == null || models == null) {
            return null;
        }
        try {
            Arguments arguments = new Arguments(args == null ? List.of() : args, models);
            ModelKind kind = build(className, arguments, allTextures, sideTextures, block);
            return arguments.resolvedAny() ? kind : null;
        } catch (RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not build block model {} for {}", className, block, e);
            return null;
        }
    }

    private static ModelKind build(String className, Arguments args, String allTextures, Map<Direction, String> sideTextures, Block<?> block) {
        return switch (className) {
            case "BlockModelEmpty", "BlockModelSignPainted" -> EMPTY;
            case "BlockModelGeneric", "BlockModelGenericCullBounderies" -> fixed(args.ref(0));
            case "BlockModelGenericShifted", "BlockModelGenericAxis", "BlockModelGenericRotatable", "BlockModelGenericWorkbench",
                 "BlockModelGenericStairs" -> fixed(args.ref(0));
            case "BlockModelGenericVeryRotatable" -> facing(args.ref(0), Facing::veryRotatable);
            case "BlockModelGenericFullyRotatable" -> facing(args.ref(0), Facing::fullyRotatable);
            case "BlockModelGenericFurnace" -> standalone(args, args.string(0), args.string(0) + "_filled");
            case "BlockModelGenericMatcher" -> fixed(args.load(4, i -> args.string(0) + i)[0]);
            case "BlockModelGenericActivator" -> activator(args);
            case "BlockModelGenericBasket" -> standalone(args, BLOCK + "basket", BLOCK + "basket_fill");
            case "BlockModelGenericBed" -> bed(args);
            case "BlockModelGenericBrazier" -> fixed(first(args.ref(0), Shapes.floorFire(BLOCK + "fire")));
            case "BlockModelGenericButton" -> button(args, args.string(0));
            case "BlockModelGenericButtonPainted" -> paintedButton(args);
            case "BlockModelGenericCake" -> cycle(args.load(6, i -> BLOCK + "cake/" + i));
            case "BlockModelGenericChest" -> chest(args);
            case "BlockModelGenericChestPainted" -> paintedChest(args);
            case "BlockModelGenericCropsPumpkin" -> pumpkinCrops(args);
            case "BlockModelGenericCropsWheat" -> cycle(args.load(8, i -> BLOCK + "crops_wheat/stage" + i));
            case "BlockModelGenericDoor" -> door(args, args.string(0), args.bool(1));
            case "BlockModelGenericDoorPainted" -> paintedDoor(args, args.bool(0));
            case "BlockModelGenericFarmland" -> farmland(args);
            case "BlockModelGenericFence" -> standalone(args, args.string(0) + "/post", args.string(0) + "/side");
            case "BlockModelGenericFenceThin" -> standalone(args, args.string(0) + "/post", args.string(0) + "/side",
                args.string(0) + "/side_top");
            case "BlockModelGenericFencePainted" -> paintedFence(args, BLOCK + "fence/");
            case "BlockModelGenericFenceGate" -> fenceGate(args, args.string(0));
            case "BlockModelGenericFenceGatePainted" -> paintedFenceGate(args);
            case "BlockModelGenericFlowerStackable" -> flowerStackable(args, args.string(0));
            case "BlockModelGenericGrass" -> standalone(args, BLOCK + "grass", BLOCK + "grass_snowy", BLOCK + "grass_ashy");
            case "BlockModelGenericJar" -> jar(args);
            case "BlockModelGenericLamp" -> lamp(args, args.string(0));
            case "BlockModelGenericLantern" -> lantern(args.ref(0), args.ref(1));
            case "BlockModelGenericLayer" -> layer(args, args.string(0));
            case "BlockModelGenericLayerAsh" -> {
                ModelKind ash = layer(args, BLOCK + "ash");
                layerHeights(args, BLOCK + "ash_ember");
                yield ash;
            }
            case "BlockModelGenericLeaves" -> fixed(args.load(args.string(0) + "_fancy"));
            case "BlockModelGenericLeavesCacao" -> cacaoLeaves(args);
            case "BlockModelGenericLeavesCherryFlowering" -> floweringCherryLeaves(args);
            case "BlockModelGenericLever" -> lever(args);
            case "BlockModelGenericPebbles" -> cycle(args.load(3, i -> BLOCK + "pebbles/" + i));
            case "BlockModelGenericPie" -> cycle(args.load(4, i -> BLOCK + "pumpkin_pie/" + i));
            case "BlockModelGenericPiston" -> piston(args.ref(0), args.ref(1));
            case "BlockModelGenericPistonHead" -> pistonHead(args);
            case "BlockModelGenericPlanksPainted" -> colored(args.dyed(color -> BLOCK + "planks/" + color));
            case "BlockModelGenericPortal" -> portal(args, block, args.string(0));
            case "BlockModelGenericPressurePlate" -> pressurePlate(args, args.string(0));
            case "BlockModelGenericPressurePlatePainted" -> paintedPressurePlate(args);
            case "BlockModelGenericRepeater" -> repeater(args, block != null && block.getLogic() instanceof BlockLogicRepeater logic && logic.isRepeaterPowered);
            case "BlockModelGenericRocks" -> cycle(args.load(3, i -> BLOCK + "rocks/" + i));
            case "BlockModelGenericRope" -> standalone(args, BLOCK + "rope/knot", BLOCK + "rope/line", BLOCK + "rope/pile");
            case "BlockModelGenericRubyglassCrystal" -> rubyglassCrystal(args);
            case "BlockModelGenericRubyglassGrowth" -> rubyglassGrowth(args);
            case "BlockModelGenericSlab" -> slab(args.ref(0), args.ref(1), args.ref(2));
            case "BlockModelGenericSlabPainted" -> paintedSlab(args);
            case "BlockModelGenericSpikes" -> spikes(args);
            case "BlockModelGenericStairsPainted" -> paintedStairs(args);
            case "BlockModelGenericTimer" -> timer(args);
            case "BlockModelGenericTorch" -> torch(args, args.string(0));
            case "BlockModelGenericTrapdoor" -> trapdoor(args, args.string(0));
            case "BlockModelGenericTrapdoorPainted" -> paintedTrapdoor(args);
            case "BlockModelGenericWool" -> colored(args.dyed(color -> BLOCK + "wool/" + color));
            case "BlockModelConduit" -> conduit(args);
            case "BlockModelCrossedSquares" -> fixed(Shapes.cross(required(allTextures), 0));
            case "BlockModelFire" -> fixed(Shapes.floorFire(required(allTextures)));
            case "BlockModelFluid" -> EMPTY;
            case "BlockModelGlass" -> fixed(Shapes.cube(required(allTextures), 0));
            case "BlockModelStandard", "BlockModelTransparent", "BlockModelAxisAligned", "BlockModelRotatable",
                 "BlockModelHorizontalRotation", "BlockModelVeryRotatable", "BlockModelFullyRotatable" ->
                legacyCube(className, allTextures, sideTextures);
            case "BlockModelLeaves" -> fixed(Shapes.cube(args.string(0) + "_fancy", 0));
            case "BlockModelRail" -> rail(block, required(allTextures));
            case "BlockModelWireRedstone" -> fixed(Shapes.flat(BLOCK + "wire_redstone_cross", 0.25F, 0, 0));
            default -> null;
        };
    }

    private static ModelKind activator(Arguments args) {
        BlockModel idle = args.load(BLOCK + "activator_idle");
        BlockModel active = args.load(BLOCK + "activator_active");
        return selected(meta -> (meta & 8) != 0 ? active : idle, Facing::veryRotatable);
    }

    private static ModelKind bed(Arguments args) {
        BlockModel foot = args.load(BLOCK + "bed/foot");
        BlockModel head = args.load(BLOCK + "bed/head");
        return selected(meta -> BlockLogicBed.IS_HEAD.bool(meta) ? head : foot);
    }

    private static ModelKind button(Arguments args, String key) {
        BlockModel idle = args.load(key + "/idle");
        BlockModel active = args.load(key + "/active");
        return selected(meta -> (meta & 8) != 0 ? active : idle);
    }

    private static ModelKind paintedButton(Arguments args) {
        BlockModel[] idle = args.dyed(color -> BLOCK + "button/planks/" + color + "/idle");
        BlockModel[] active = args.dyed(color -> BLOCK + "button/planks/" + color + "/active");
        return selected(meta -> ((meta & 8) != 0 ? active : idle)[paintedColor(meta)]);
    }

    private static ModelKind chest(Arguments args) {
        BlockModel single = args.ref(0);
        BlockModel left = args.ref(1);
        BlockModel right = args.ref(2);
        return selected(meta -> chestPart(meta, single, left, right));
    }

    private static ModelKind paintedChest(Arguments args) {
        BlockModel[] single = args.dyed(color -> BLOCK + "chest/single/" + color);
        BlockModel[] left = args.dyed(color -> BLOCK + "chest/left/" + color);
        BlockModel[] right = args.dyed(color -> BLOCK + "chest/right/" + color);
        return selected(meta -> {
            int color = paintedColor(meta);
            return chestPart(meta, single[color], left[color], right[color]);
        });
    }

    static BlockModel chestPart(int meta, BlockModel single, BlockModel left, BlockModel right) {
        return switch (BlockLogicChest.getTypeFromMeta(meta)) {
            case LEFT -> left;
            case RIGHT -> right;
            default -> single;
        };
    }

    private static ModelKind pumpkinCrops(Arguments args) {
        BlockModel leaf = args.load(BLOCK + "crops_pumpkin/leaf");
        BlockModel[] stages = args.load(4, i -> BLOCK + "crops_pumpkin/stage" + (i + 1));
        return byMeta(meta -> first(leaf, meta >= 1 ? stages[meta - 1 & 3] : null));
    }

    private static ModelKind door(Arguments args, String key, boolean bottom) {
        String half = bottom ? "bottom" : "top";
        BlockModel left = args.load(key + "/" + half + "_left");
        BlockModel leftOpen = args.load(key + "/" + half + "_left_open");
        BlockModel right = args.load(key + "/" + half + "_right");
        BlockModel rightOpen = args.load(key + "/" + half + "_right_open");
        return selected(meta -> doorPart(meta, left, leftOpen, right, rightOpen));
    }

    private static ModelKind paintedDoor(Arguments args, boolean bottom) {
        String half = bottom ? "bottom" : "top";
        BlockModel[] left = args.dyed(color -> BLOCK + "door/planks/" + color + "/" + half + "_left");
        BlockModel[] leftOpen = args.dyed(color -> BLOCK + "door/planks/" + color + "/" + half + "_left_open");
        BlockModel[] right = args.dyed(color -> BLOCK + "door/planks/" + color + "/" + half + "_right");
        BlockModel[] rightOpen = args.dyed(color -> BLOCK + "door/planks/" + color + "/" + half + "_right_open");
        return selected(meta -> {
            int color = paintedColor(meta);
            return doorPart(meta, left[color], leftOpen[color], right[color], rightOpen[color]);
        });
    }

    static BlockModel doorPart(int meta, BlockModel left, BlockModel leftOpen, BlockModel right, BlockModel rightOpen) {
        boolean open = (meta & 4) != 0;
        if ((meta & 8) != 0) {
            return open ? leftOpen : left;
        }
        return open ? right : rightOpen;
    }

    private static ModelKind farmland(Arguments args) {
        BlockModel dry = args.load(BLOCK + "farmland/dry");
        BlockModel dryFertilized = args.load(BLOCK + "farmland/dry_fertilized");
        BlockModel wet = args.load(BLOCK + "farmland/wet");
        BlockModel wetFertilized = args.load(BLOCK + "farmland/wet_fertilized");
        return selected(meta -> {
            boolean fertilized = BlockLogicFarmland.isFertilized(meta);
            if (BlockLogicFarmland.isWet(meta)) {
                return fertilized ? wetFertilized : wet;
            }
            return fertilized ? dryFertilized : dry;
        });
    }

    private static ModelKind fenceGate(Arguments args, String key) {
        BlockModel closed = args.load(key + "_closed");
        BlockModel open = args.load(key + "_open");
        return selected(meta -> (meta & 4) != 0 ? open : closed);
    }

    private static ModelKind paintedFenceGate(Arguments args) {
        BlockModel[] closed = args.dyed(color -> BLOCK + "fencegate/" + color + "_closed");
        BlockModel[] open = args.dyed(color -> BLOCK + "fencegate/" + color + "_open");
        return selected(meta -> ((meta & 4) != 0 ? open : closed)[paintedColor(meta)]);
    }

    private static ModelKind flowerStackable(Arguments args, String base) {
        BlockModel[] stacks = args.load(4, i -> base + i);
        return selected(meta -> stacks[BlockLogicFlowerStackable.getStackCount(meta)]);
    }

    private static ModelKind jar(Arguments args) {
        BlockModel jar = args.load(BLOCK + "jar");
        BlockModel jarDirt = args.load(BLOCK + "jar_dirt");
        return selected(meta -> meta == 1 ? jarDirt : jar);
    }

    private static ModelKind lamp(Arguments args, String keyFormat) {
        return colored(args.dyed(color -> String.format(keyFormat, color)));
    }

    private static ModelKind lantern(BlockModel floor, BlockModel hanging) {
        return selected(meta -> (meta & 1) != 0 ? hanging : floor);
    }

    private static ModelKind layer(Arguments args, String key) {
        BlockModel[] heights = layerHeights(args, key);
        return selected(meta -> layerPart(meta, heights));
    }

    private static BlockModel[] layerHeights(Arguments args, String key) {
        return args.load(7, i -> key + "/" + i);
    }

    private static BlockModel layerPart(int meta, BlockModel[] heights) {
        return heights[Math.min(meta & 7, heights.length - 1)];
    }

    private static ModelKind cacaoLeaves(Arguments args) {
        BlockModel leaves = args.load(BLOCK + "leaves/cacao_fancy");
        BlockModel[] overlays = args.load(4, i -> BLOCK + "leaves/cacao_overlay_stage" + i);
        return fixed(leaves, overlays[overlays.length - 1]);
    }

    private static ModelKind floweringCherryLeaves(Arguments args) {
        BlockModel leaves = args.load(BLOCK + "leaves/cherry_fancy");
        return fixed(leaves, args.load(BLOCK + "leaves/cherry_overlay"));
    }

    private static ModelKind lever(Arguments args) {
        BlockModel idle = args.load(BLOCK + "lever_idle");
        BlockModel active = args.load(BLOCK + "lever_active");
        return selected(meta -> (meta & 16) != 0 ? active : idle);
    }

    private static ModelKind piston(BlockModel base, BlockModel extended) {
        return selected(meta -> PistonCommon.isExtended(meta) ? extended : base, Facing::veryRotatable);
    }

    private static ModelKind pistonHead(Arguments args) {
        BlockModel[] heads = {args.ref(0), args.ref(1), args.ref(2)};
        return selected(meta -> {
            int type = PistonCommon.TYPE.get(meta);
            return heads[type >= heads.length ? 0 : type];
        }, Facing::veryRotatable);
    }

    private static ModelKind portal(Arguments args, Block<?> block, String key) {
        BlockModel[] colors = args.dyed(color -> key + color);
        if (!(block.getLogic() instanceof BlockLogicPortal logic)) {
            return null;
        }
        return meta -> present(colors[logic.fromMetadata(meta).blockMeta]);
    }

    static ModelKind paintedFence(Arguments args, String root) {
        BlockModel[] posts = args.dyed(color -> root + color + "/post");
        args.dyed(color -> root + color + "/side");
        return meta -> present(posts[meta & 15]);
    }

    private static ModelKind pressurePlate(Arguments args, String key) {
        BlockModel idle = args.load(key + "/idle");
        BlockModel active = args.load(key + "/active");
        return selected(meta -> (meta & 1) != 0 ? active : idle);
    }

    private static ModelKind paintedPressurePlate(Arguments args) {
        BlockModel[] idle = args.dyed(color -> BLOCK + "pressure_plate/planks/" + color + "/idle");
        BlockModel[] active = args.dyed(color -> BLOCK + "pressure_plate/planks/" + color + "/active");
        return selected(meta -> ((meta & 1) != 0 ? active : idle)[paintedColor(meta)]);
    }

    private static ModelKind repeater(Arguments args, boolean powered) {
        BlockModel[] delays = args.load(4, i -> BLOCK + "repeater/" + (powered ? "active" : "") + i);
        return selected(meta -> delays[(meta & 12) >> 2]);
    }

    private static ModelKind rubyglassCrystal(Arguments args) {
        BlockModel[] sizes = args.load(4, i -> BLOCK + "rubyglass/crystal/" + i);
        return selected(meta -> sizes[meta & 3]);
    }

    private static ModelKind rubyglassGrowth(Arguments args) {
        BlockModel bud = args.load(BLOCK + "rubyglass/growth/bud");
        BlockModel bottom = args.load(BLOCK + "rubyglass/growth/bottom");
        BlockModel top = args.load(BLOCK + "rubyglass/growth/top");
        return selected(meta -> switch (meta & 24) {
            case 16 -> top;
            case 8 -> bottom;
            default -> bud;
        }, Facing::fullyRotatable);
    }

    private static ModelKind slab(BlockModel lower, BlockModel upper, BlockModel full) {
        return selected(meta -> slabPart(meta, lower, upper, full));
    }

    private static ModelKind paintedSlab(Arguments args) {
        BlockModel[] lower = args.dyed(color -> BLOCK + "slab/planks/" + color + "/lower");
        BlockModel[] upper = args.dyed(color -> BLOCK + "slab/planks/" + color + "/upper");
        BlockModel[] full = args.dyed(color -> BLOCK + "slab/planks/" + color + "/full");
        return selected(meta -> {
            int color = paintedColor(meta);
            return slabPart(meta, lower[color], upper[color], full[color]);
        });
    }

    static BlockModel slabPart(int meta, BlockModel lower, BlockModel upper, BlockModel full) {
        return switch (meta & 3) {
            case 1 -> full;
            case 2 -> upper;
            default -> lower;
        };
    }

    private static ModelKind spikes(Arguments args) {
        BlockModel base = args.load(BLOCK + "spikes_base");
        BlockModel spikes = args.load(BLOCK + "spikes");
        return byMeta(meta -> first(base, BlockLogicSpikes.isSpikesUp(meta) ? spikes : null));
    }

    private static ModelKind paintedStairs(Arguments args) {
        BlockModel[] colors = args.dyed(color -> BLOCK + "stairs/planks/" + color);
        return selected(meta -> colors[paintedColor(meta)]);
    }

    private static ModelKind timer(Arguments args) {
        BlockModel[] outputA = args.load(4, i -> BLOCK + "timer/" + i + "_a");
        BlockModel[] outputB = args.load(4, i -> BLOCK + "timer/" + i + "_b");
        return selected(meta -> ((meta & 2) == 0 ? outputB : outputA)[(meta & 12) >> 2]);
    }

    private static ModelKind torch(Arguments args, String key) {
        BlockModel standing = args.load(key);
        BlockModel wall = args.load(key + "_wall");
        return selected(meta -> Facing.wallTorch(meta) ? wall : standing);
    }

    private static ModelKind trapdoor(Arguments args, String key) {
        BlockModel bottom = args.load(key + "/bottom");
        BlockModel open = args.load(key + "/open");
        BlockModel top = args.load(key + "/top");
        return selected(meta -> trapdoorPart(meta, bottom, open, top));
    }

    private static ModelKind paintedTrapdoor(Arguments args) {
        BlockModel[] bottom = args.dyed(color -> BLOCK + "trapdoor/planks/" + color + "/bottom");
        BlockModel[] open = args.dyed(color -> BLOCK + "trapdoor/planks/" + color + "/open");
        BlockModel[] top = args.dyed(color -> BLOCK + "trapdoor/planks/" + color + "/top");
        return selected(meta -> {
            int color = paintedColor(meta);
            return trapdoorPart(meta, bottom[color], open[color], top[color]);
        });
    }

    static BlockModel trapdoorPart(int meta, BlockModel bottom, BlockModel open, BlockModel top) {
        if ((meta & 4) != 0) {
            return open;
        }
        return (meta & 8) != 0 ? top : bottom;
    }

    private static ModelKind conduit(Arguments args) {
        String key = BLOCK + "rubyglass/conduit/conduit_";
        ConduitModels on = new ConduitModels(args.load(key + "core"), args.load(key + "straight"),
                args.load(key + "corner"), args.load(key + "cap"));
        ConduitModels off = new ConduitModels(args.load(key + "core_off"), args.load(key + "straight_off"),
                args.load(key + "corner_off"), args.load(key + "cap_off"));
        return selected(meta -> conduitPart(meta, on, off));
    }

    private static BlockModel conduitPart(int meta, ConduitModels on, ConduitModels off) {
        int state = meta & 15;
        int upper = meta >> 4 & 15;
        if (state != 0) {
            ConduitModels models = upper == 0 ? off : on;
            return state <= 3 ? models.straight() : models.corner();
        }
        if (BlockLogicConduit.isCap(meta)) {
            return (BlockLogicConduit.isCapPowered(meta) ? on : off).cap();
        }
        return upper == 15 ? on.core() : off.core();
    }

    private static ModelKind rail(Block<?> block, String texture) {
        if (!(block.getLogic() instanceof BlockLogicRail logic)) {
            return null;
        }
        return selected(meta -> {
            String current;
            if (block == Blocks.RAIL_POWERED && (meta & 8) != 0) {
                current = BLOCK + "rail_powered/active";
            } else if (block == Blocks.RAIL_DETECTOR && (meta & 8) != 0) {
                current = BLOCK + "rail_detector/active";
            } else {
                current = !logic.isPowered && meta >= 6 ? BLOCK + "rail/turn" : texture;
            }
            return railShape(logic.getRailDirection(meta), current);
        });
    }

    private static BlockModel railShape(RailDirection direction, String texture) {
        return switch (direction) {
            case STRAIGHT_EW, TURN_WS -> Shapes.flat(texture, 1.0F, 90, -1);
            case SLOPE_E -> Shapes.slope(texture, Direction.EAST, 1.0F, -1);
            case SLOPE_W -> Shapes.slope(texture, Direction.WEST, 1.0F, -1);
            case SLOPE_N -> Shapes.slope(texture, Direction.NORTH, 1.0F, -1);
            case SLOPE_S -> Shapes.slope(texture, Direction.SOUTH, 1.0F, -1);
            case TURN_WN -> Shapes.flat(texture, 1.0F, 180, -1);
            case TURN_EN -> Shapes.flat(texture, 1.0F, 270, -1);
            default -> Shapes.flat(texture, 1.0F, 0, -1);
        };
    }

    private static ModelKind legacyCube(String className, String allTextures, Map<Direction, String> sideTextures) {
        Map<Direction, String> textures = new EnumMap<>(Direction.class);
        if (allTextures != null) {
            for (Direction direction : Direction.values()) {
                textures.put(direction, allTextures);
            }
        }
        if (sideTextures != null) {
            textures.putAll(sideTextures);
        }
        if (textures.isEmpty()) {
            throw new IllegalArgumentException("Missing texture");
        }
        String fallback = BLOCK + "texture_unassigned";
        Direction[] directions = Direction.values();
        return selected(meta -> {
            Map<Direction, String> faces = new EnumMap<>(Direction.class);
            Map<Direction, Integer> uvRotations = new EnumMap<>(Direction.class);
            for (Direction face : directions) {
                Side side = Side.fromId(face.ordinal());
                Side source = LegacySides.source(className, meta, side);
                Direction from = source != null && source.id >= 0 && source.id < directions.length ? directions[source.id] : Direction.UP;
                faces.put(face, textures.getOrDefault(from, fallback));
                uvRotations.put(face, LegacySides.uvRotation(className, meta, side));
            }
            return Shapes.cube(faces, uvRotations);
        });
    }

    private static String required(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing texture");
        }
        return value;
    }

    static int paintedColor(int meta) {
        return meta >> 4 & 15;
    }

    static ModelKind fixed(BlockModel... models) {
        return fixed(present(models));
    }

    static ModelKind fixed(List<BlockModel> layers) {
        List<BlockModel> result = List.copyOf(layers);
        return meta -> result;
    }

    static ModelKind standalone(Arguments args, String model, String... alternatives) {
        BlockModel shown = args.load(model);
        for (String alternative : alternatives) {
            args.load(alternative);
        }
        return fixed(shown);
    }

    static ModelKind colored(BlockModel[] colors) {
        return selected(meta -> colors[meta & 15]);
    }

    static ModelKind cycle(BlockModel[] models) {
        return selected(meta -> models[meta % models.length]);
    }

    static ModelKind selected(IntFunction<BlockModel> model) {
        return byMeta(meta -> present(model.apply(meta)));
    }

    static ModelKind selected(IntFunction<BlockModel> model, IntPredicate drawn) {
        return byMeta(meta -> {
            BlockModel current = model.apply(meta);
            return drawn.test(meta) ? present(current) : List.of();
        });
    }

    private static ModelKind facing(BlockModel model, IntPredicate drawn) {
        return selected(meta -> model, drawn);
    }

    static ModelKind byMeta(IntFunction<List<BlockModel>> resolver) {
        return new MetaModelKind(resolver);
    }

    static List<BlockModel> present(BlockModel... models) {
        List<BlockModel> present = new ArrayList<>(models.length);
        for (BlockModel model : models) {
            if (model != null) {
                present.add(model);
            }
        }
        return List.copyOf(present);
    }

    private static List<BlockModel> first(BlockModel... models) {
        for (BlockModel model : models) {
            if (model != null) {
                return List.of(model);
            }
        }
        return List.of();
    }

    private record ConduitModels(BlockModel core, BlockModel straight, BlockModel corner, BlockModel cap) {
    }
}
