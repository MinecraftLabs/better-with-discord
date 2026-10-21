package com.sajmonoriginal.betterwithdiscord.image;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.IArmorItem;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemQuiver;
import net.minecraft.core.item.ItemQuiverEndless;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.util.collection.NamespaceID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record InventorySnapshot(String playerName, UUID uuid, boolean aether, boolean offhand, List<Slot> slots, List<String> armorTextures,
                                boolean invisible, List<WornModel> wornModels, Cape cape, ContainerSnapshot wornBackpack) {
    private static final String BACKPACK_GEOMETRY = "geometry.humanoid.armor.bag";
    private static final String BEDROLL_GEOMETRY = "geometry.bag.bedroll";
    private static final String BACKPACK_TEXTURES = "assets/betterwithbags/textures/entity/";

    private static final String[] ARMOR_OUTLINES = {
        "minecraft:item/armor_helmet_outline",
        "minecraft:item/armor_chestplate_outline",
        "minecraft:item/armor_leggings_outline",
        "minecraft:item/armor_boots_outline"
    };

    private static final String[] ACCESSORY_OUTLINES = {
        "aether:item/armor_gloves_outline",
        "aether:item/armor_capes_outline",
        "aether:item/armor_wildcard_outline",
        "aether:item/armor_wildcard_outline"
    };

    public static InventorySnapshot of(Player player) {
        boolean aether = FabricLoader.getInstance().isModLoaded("aether");
        boolean offhand = FabricLoader.getInstance().isModLoaded("better_with_hands") && HandsCompat.hasOffhand(player);
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            slots.add(slot(player, 36 + i, 8, 8 + i * 18, ARMOR_OUTLINES[i]));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slots.add(slot(player, column + row * 9 + 9, 8 + column * 18, 84 + row * 18, null));
            }
        }
        for (int i = 0; i < 9; i++) {
            slots.add(slot(player, i, 8 + i * 18, 142, null));
        }
        if (aether) {
            for (int i = 0; i < ACCESSORY_OUTLINES.length; i++) {
                slots.add(slot(player, AetherCompat.accessory(player, i), 80, 8 + i * 18, ACCESSORY_OUTLINES[i]));
            }
        }
        if (offhand) {
            slots.add(slot(player, HandsCompat.offhand(player), HandsCompat.SLOT_X, HandsCompat.SLOT_Y, HandsCompat.OUTLINE));
        }
        List<String> armor = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            armor.add(armorTexture(player.inventory.getItem(36 + i), i + 1));
        }
        ItemStack chest = player.inventory.getItem(37);
        List<WornModel> worn = new ArrayList<>();
        backpackModels(worn, chest);
        if (aether) {
            worn.addAll(AetherCompat.accessories(player));
        }
        return new InventorySnapshot(TextFormatting.removeAllFormatting(player.getDisplayName()), player.uuid, aether, offhand,
            List.copyOf(slots), Collections.unmodifiableList(armor), aether && AetherCompat.invisible(player), List.copyOf(worn),
            aether ? AetherCompat.cape(player) : null, ContainerSnapshot.worn(chest, player.world));
    }

    private static void backpackModels(List<WornModel> worn, ItemStack chest) {
        String tier = chest == null ? null : ContainerSnapshot.backpackTier(chest);
        if (tier == null) {
            return;
        }
        worn.add(new WornModel(BACKPACK_GEOMETRY, 0.0, BACKPACK_TEXTURES + "backpack_" + tier + ".png", Set.of(), 1.0F));
        if (ContainerSnapshot.hasAttachment(chest)) {
            worn.add(new WornModel(BEDROLL_GEOMETRY, 0.0, BACKPACK_TEXTURES + "bedroll_attachment.png", Set.of(), 1.0F));
        }
    }

    private static String armorTexture(ItemStack stack, int layer) {
        if (stack == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof ItemQuiver) {
            return "assets/minecraft/textures/armor/quiver.png";
        }
        if (item instanceof ItemQuiverEndless) {
            return "assets/minecraft/textures/armor/quiver_golden.png";
        }
        if (item == Items.ARMOR_BOOTS_ICESKATES) {
            return "assets/minecraft/textures/armor/skates.png";
        }
        if (!(item instanceof IArmorItem<?> armor) || armor.getArmorMaterial() == null) {
            return null;
        }
        NamespaceID material = armor.getArmorMaterial().identifier;
        return "assets/" + material.namespace() + "/textures/armor/" + material.value() + "_" + (layer != 3 ? 1 : 2) + ".png";
    }

    private static Slot slot(Player player, int index, int x, int y, String outline) {
        return slot(player, player.inventory.getItem(index), x, y, outline);
    }

    private static Slot slot(Player player, ItemStack stack, int x, int y, String outline) {
        return new Slot(x, y, stack == null ? null : ItemSnapshot.contained(stack, player.world), outline);
    }

    public record Slot(int x, int y, ItemSnapshot item, String outline) {
    }

    public record WornModel(String geometry, double inflation, String texture, Set<String> hiddenBones, float opacity) {
    }

    public record Cape(String texture, float opacity) {
    }
}
