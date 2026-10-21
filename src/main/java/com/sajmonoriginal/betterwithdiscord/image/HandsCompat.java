package com.sajmonoriginal.betterwithdiscord.image;

import com.sajmonoriginal.betterwithhands.util.OffhandCapable;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;

final class HandsCompat {
    static final String OUTLINE = "better_with_hands:item/offhand_slot_outline";
    static final int SLOT_X = 152;
    static final int SLOT_Y = 62;

    private HandsCompat() {
    }

    static boolean hasOffhand(Player player) {
        return player.inventory instanceof OffhandCapable hands && hands.betterWithHands$isOffhandEnabled();
    }

    static ItemStack offhand(Player player) {
        return ((OffhandCapable) player.inventory).betterWithHands$getOffhandItem();
    }
}
