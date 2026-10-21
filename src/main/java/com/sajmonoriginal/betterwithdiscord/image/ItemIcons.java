package com.sajmonoriginal.betterwithdiscord.image;

import net.minecraft.core.item.ItemBucket;
import net.minecraft.core.item.ItemMap;
import net.minecraft.core.item.ItemPaintBrush;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.DyeColor;
import net.minecraft.core.world.World;

final class ItemIcons {
    private ItemIcons() {
    }

    static String special(ItemStack stack, World world) {
        int meta = stack.getMetadata();
        if (stack.getItem() == Items.DYE) {
            return "minecraft:item/dye_" + DyeColor.colorFromItemMeta(meta & 15).colorID;
        }
        if (stack.getItem() == Items.COAL && meta == 1) {
            return "minecraft:item/charcoal";
        }
        if (stack.getItem() == Items.LABEL && stack.hasCustomName()) {
            return "minecraft:item/label_written";
        }
        if (stack.getItem() == Items.ARMOR_QUIVER && meta >= stack.getItem().getMaxDamage()) {
            return "minecraft:item/armor_quiver_empty";
        }
        if (stack.getItem() == Items.MAP && !ItemMap.hasInitialized(stack)) {
            return "minecraft:item/map_blank";
        }
        if (stack.getItem() == Items.PAINTBRUSH) {
            DyeColor color = ItemPaintBrush.getColor(stack);
            return color == null ? "minecraft:item/paintbrush" : "minecraft:item/paintbrush_" + color.colorID;
        }
        if (stack.getItem() == Items.SIGN_PAINTED) {
            return "minecraft:item/sign/" + DyeColor.colorFromItemMeta(meta & 15).colorID;
        }
        if (stack.getItem() == Items.DOOR_OAK_PAINTED) {
            return "minecraft:item/door_" + DyeColor.colorFromItemMeta(meta & 15).colorID;
        }
        if (stack.getItem() == Items.BUCKET_IRON || stack.getItem() == Items.BUCKET_STEEL) {
            ItemBucket bucket = (ItemBucket) stack.getItem();
            String prefix = stack.getItem() == Items.BUCKET_IRON ? "bucket_iron" : "bucket_steel";
            NamespaceID state = ItemBucket.getState(stack);
            if (ItemBucket.STATE_EMPTY.equals(state)) {
                return "minecraft:item/" + prefix + "/empty";
            }
            int charges = Math.max(1, Math.min(ItemBucket.getCharges(stack), bucket.maxCharges));
            return "minecraft:item/" + prefix + "/" + ItemBucket.getTextureStateName(null, world, stack) + "_" + charges;
        }
        return null;
    }
}
