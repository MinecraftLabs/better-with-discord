package com.sajmonoriginal.betterwithdiscord.image.block.model;

import java.util.Map;

public record ModelElement(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                           ElementRotation rotation, boolean shade, Map<Direction, ModelFace> faces) {
}
