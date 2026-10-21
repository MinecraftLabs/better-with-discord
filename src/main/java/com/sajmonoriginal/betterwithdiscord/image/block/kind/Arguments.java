package com.sajmonoriginal.betterwithdiscord.image.block.kind;

import com.sajmonoriginal.betterwithdiscord.image.block.model.BlockModel;
import com.sajmonoriginal.betterwithdiscord.image.block.model.ModelLibrary;
import net.minecraft.core.util.helper.DyeColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

final class Arguments {
    private final List<Object> values;
    private final ModelLibrary library;
    private final Map<String, BlockModel> loaded = new HashMap<>();

    Arguments(List<Object> values, ModelLibrary library) {
        this.values = values;
        this.library = library;
    }

    String string(int index) {
        if (value(index) instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Expected a string argument at index " + index);
    }

    int integer(int index) {
        if (value(index) instanceof Integer integer) {
            return integer;
        }
        throw new IllegalArgumentException("Expected an integer argument at index " + index);
    }

    boolean bool(int index) {
        if (value(index) instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Expected a boolean argument at index " + index);
    }

    BlockModel ref(int index) {
        if (value(index) instanceof ModelRef ref && ref.id() != null) {
            return load(ref.id());
        }
        throw new IllegalArgumentException("Expected a model argument at index " + index);
    }

    BlockModel load(String id) {
        if (loaded.containsKey(id)) {
            return loaded.get(id);
        }
        BlockModel model = library.load(id);
        loaded.put(id, model);
        return model;
    }

    boolean resolvedAny() {
        return loaded.isEmpty() || loaded.values().stream().anyMatch(model -> model != null);
    }

    BlockModel[] load(int count, IntFunction<String> ids) {
        BlockModel[] models = new BlockModel[count];
        for (int i = 0; i < count; i++) {
            models[i] = load(ids.apply(i));
        }
        return models;
    }

    BlockModel[] dyed(Function<String, String> ids) {
        return load(16, color -> ids.apply(DyeColor.colorFromBlockMeta(color).colorID));
    }

    private Object value(int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
