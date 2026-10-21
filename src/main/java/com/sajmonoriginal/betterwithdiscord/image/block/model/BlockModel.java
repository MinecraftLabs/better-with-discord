package com.sajmonoriginal.betterwithdiscord.image.block.model;

import java.util.List;

public record BlockModel(String id, List<ModelElement> elements) {
    public boolean isEmpty() {
        return elements.isEmpty();
    }
}
