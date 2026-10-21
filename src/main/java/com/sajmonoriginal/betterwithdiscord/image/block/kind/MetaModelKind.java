package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

final class MetaModelKind implements ModelKind {
    private final IntFunction<List<BlockModel>> resolver;
    private final AtomicReferenceArray<List<BlockModel>> cache = new AtomicReferenceArray<>(256);

    MetaModelKind(IntFunction<List<BlockModel>> resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<BlockModel> layers(int meta) {
        int index = meta & 255;
        List<BlockModel> layers = cache.get(index);
        if (layers == null) {
            layers = List.copyOf(resolver.apply(index));
            cache.set(index, layers);
        }
        return layers;
    }
}
