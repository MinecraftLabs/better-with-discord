package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.Direction;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelLibrary;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicChest;
import net.minecraft.core.util.helper.DyeColor;
import net.minecraft.core.util.helper.Side;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class ModKinds {
    private static final String AETHER_MODELS = "teamport/aether/models/";
    private static final String STARDEW_MODELS = "luke/stardew/model/";
    private static final String AETHER = "aether:block/";
    private static final String MULTIPART = "sunsetsatellite/catalyst/multipart/block/model/BlockModelMultipart";

    private ModKinds() {
    }

    public static ModelKind create(String modelClass, List<Object> values, ModelLibrary library, Block<?> block) {
        if (modelClass == null || library == null) {
            return null;
        }
        try {
            Arguments args = new Arguments(values == null ? List.of() : values, library);
            ModelKind kind = build(modelClass, args);
            return kind != null && args.resolvedAny() ? kind : null;
        } catch (RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not build block model {} for {}", modelClass, block, e);
            return null;
        }
    }

    private static ModelKind build(String modelClass, Arguments args) {
        return switch (modelClass) {
            case MULTIPART -> meta -> List.of();
            case AETHER_MODELS + "dungeon/BlockModelMimic" -> mimic(meta -> args.string(0));
            case AETHER_MODELS + "dungeon/BlockModelPaintedOakMimic" ->
                mimic(meta -> "minecraft:block/chest/planks_" + DyeColor.colorFromBlockMeta(meta >> 4).colorID + "/");
            case AETHER_MODELS + "skyroot/BlockModelPaintedSkyrootMimic" ->
                mimic(meta -> AETHER + "chest/" + DyeColor.colorFromBlockMeta(meta >> 4).colorID + "/");
            case AETHER_MODELS + "BlockModelFixedAxis" -> ModelKinds.fixed(args.ref(0));
            case AETHER_MODELS + "BlockModelGrassAether" -> ModelKinds.standalone(args, AETHER + "grass_aether/grass_aether",
                AETHER + "grass_aether/snowy", AETHER + "grass_aether/ashy");
            case AETHER_MODELS + "BlockModelGenericFreezer", AETHER_MODELS + "BlockModelGenericIncubator" ->
                ModelKinds.standalone(args, args.string(0), args.string(0) + "_filled");
            case AETHER_MODELS + "skyroot/BlockModelGenericPlanksSkyrootPainted" ->
                ModelKinds.colored(args.dyed(color -> AETHER + "planks_skyroot/" + color));
            case AETHER_MODELS + "skyroot/BlockModelGenericStairsSkyrootPainted" -> {
                BlockModel[] colors = args.dyed(color -> AETHER + "stairs/planks_skyroot/" + color);
                yield ModelKinds.selected(meta -> colors[ModelKinds.paintedColor(meta)]);
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericSlabSkyrootPainted" -> {
                BlockModel[] lower = args.dyed(color -> AETHER + "slab/planks_skyroot/" + color + "/lower");
                BlockModel[] upper = args.dyed(color -> AETHER + "slab/planks_skyroot/" + color + "/upper");
                BlockModel[] full = args.dyed(color -> AETHER + "slab/planks_skyroot/" + color + "/full");
                yield ModelKinds.selected(meta -> {
                    int color = ModelKinds.paintedColor(meta);
                    return ModelKinds.slabPart(meta, lower[color], upper[color], full[color]);
                });
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericTrapdoorSkyrootPainted" -> {
                BlockModel[] bottom = args.dyed(color -> AETHER + "trapdoor/planks_skyroot/" + color + "/bottom");
                BlockModel[] open = args.dyed(color -> AETHER + "trapdoor/planks_skyroot/" + color + "/open");
                BlockModel[] top = args.dyed(color -> AETHER + "trapdoor/planks_skyroot/" + color + "/top");
                yield ModelKinds.selected(meta -> {
                    int color = ModelKinds.paintedColor(meta);
                    return ModelKinds.trapdoorPart(meta, bottom[color], open[color], top[color]);
                });
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericChestSkyrootPainted" -> {
                BlockModel[] single = args.dyed(color -> AETHER + "chest/single/" + color);
                BlockModel[] left = args.dyed(color -> AETHER + "chest/left/" + color);
                BlockModel[] right = args.dyed(color -> AETHER + "chest/right/" + color);
                yield ModelKinds.selected(meta -> {
                    int color = ModelKinds.paintedColor(meta);
                    return ModelKinds.chestPart(meta, single[color], left[color], right[color]);
                });
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericButtonSkyrootPainted" -> {
                BlockModel[] idle = args.dyed(color -> AETHER + "button/planks_skyroot/" + color + "/idle");
                BlockModel[] active = args.dyed(color -> AETHER + "button/planks_skyroot/" + color + "/active");
                yield ModelKinds.selected(meta -> ((meta & 8) != 0 ? active : idle)[ModelKinds.paintedColor(meta)]);
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericPressurePlateSkyrootPainted" -> {
                BlockModel[] idle = args.dyed(color -> AETHER + "pressure_plate/planks_skyroot/" + color + "/idle");
                BlockModel[] active = args.dyed(color -> AETHER + "pressure_plate/planks_skyroot/" + color + "/active");
                yield ModelKinds.selected(meta -> ((meta & 1) != 0 ? active : idle)[ModelKinds.paintedColor(meta)]);
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericFenceSkyrootPainted" -> ModelKinds.paintedFence(args, AETHER + "fence/");
            case AETHER_MODELS + "skyroot/BlockModelGenericFenceGateSkyrootPainted" -> {
                BlockModel[] closed = args.dyed(color -> AETHER + "fencegate/" + color + "_closed");
                BlockModel[] open = args.dyed(color -> AETHER + "fencegate/" + color + "_open");
                yield ModelKinds.selected(meta -> ((meta & 4) != 0 ? open : closed)[ModelKinds.paintedColor(meta)]);
            }
            case AETHER_MODELS + "skyroot/BlockModelGenericDoorSkyrootPainted" -> {
                String half = args.bool(0) ? "bottom" : "top";
                BlockModel[] left = args.dyed(color -> AETHER + "door/planks_skyroot/" + color + "/" + half + "_left");
                BlockModel[] leftOpen = args.dyed(color -> AETHER + "door/planks_skyroot/" + color + "/" + half + "_left_open");
                BlockModel[] right = args.dyed(color -> AETHER + "door/planks_skyroot/" + color + "/" + half + "_right");
                BlockModel[] rightOpen = args.dyed(color -> AETHER + "door/planks_skyroot/" + color + "/" + half + "_right_open");
                yield ModelKinds.selected(meta -> {
                    int color = ModelKinds.paintedColor(meta);
                    return ModelKinds.doorPart(meta, left[color], leftOpen[color], right[color], rightOpen[color]);
                });
            }
            case STARDEW_MODELS + "BlockModelGenericProgressive" ->
                ModelKinds.cycle(args.load(args.integer(1), i -> args.string(0) + "/" + i));
            case STARDEW_MODELS + "BlockModelCropPumplike" -> {
                BlockModel[] stages = args.load(args.integer(1), i -> args.string(0) + "/" + i);
                yield meta -> ModelKinds.present(stages[meta % stages.length]);
            }
            case STARDEW_MODELS + "BlockModelBush" -> {
                BlockModel[] seasons = args.load(5, i -> args.string(0) + "/" + i);
                yield meta -> ModelKinds.present(seasons[meta % seasons.length]);
            }
            case STARDEW_MODELS + "BlockModelCandle" -> {
                BlockModel[] counts = args.load(4, i -> "stardew:block/candle/" + i);
                yield ModelKinds.selected(meta -> counts[meta % counts.length]);
            }
            case STARDEW_MODELS + "BlockModelLeavesFlowering" ->
                ModelKinds.fixed(args.load(args.string(0) + "_fancy"), args.load(args.string(1)));
            default -> null;
        };
    }

    private static ModelKind mimic(IntFunction<String> root) {
        return ModelKinds.selected(meta -> {
            String prefix = root.apply(meta);
            Side facing = BlockLogicChest.getDirectionFromMeta(meta).side();
            Map<Direction, String> faces = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                String part;
                if (direction == Direction.UP || direction == Direction.DOWN) {
                    part = "top";
                } else {
                    part = direction.ordinal() == facing.id ? "front" : "side";
                }
                faces.put(direction, prefix + part);
            }
            return Shapes.cube(faces);
        });
    }
}
