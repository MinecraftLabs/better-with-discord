package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;

import java.util.List;

@FunctionalInterface
public interface ModelKind {
    List<BlockModel> layers(int meta);
}
