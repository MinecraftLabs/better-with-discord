package com.sajmonoriginal.betterwithdiscord.image;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemQuiver;
import net.minecraft.core.item.ItemQuiverEndless;
import net.minecraft.core.item.ItemStack;
import teamport.aether.ducks.IContainerInventoryAether;
import teamport.aether.item.AetherItemTags;
import teamport.aether.item.accessory.ItemAccessory;
import teamport.aether.item.accessory.cape.ItemCape;
import teamport.aether.item.accessory.cape.ItemCapeInvisibility;
import teamport.aether.item.accessory.gloves.ItemGloves;
import teamport.aether.item.accessory.pendant.ItemPendant;
import teamport.aether.item.accessory.trinket.ItemGoldenFeather;
import teamport.aether.item.accessory.trinket.ItemIronBubble;
import teamport.aether.item.accessory.trinket.ItemRegenStone;
import teamport.aether.item.accessory.trinket.ItemRepulsionShield;
import teamport.aether.item.accessory.trinket.ItemTrinket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AetherCompat {
    private static final String ARMOR = "geometry.humanoid.armor";
    private static final String FEATHER = "geometry.aether.player.accessory.feather";
    private static final String TEXTURES = "assets/aether/textures/armor/";
    private static final String TRINKETS = TEXTURES + "trinkets/";
    private static final double BASE_INFLATION = 1.1;
    private static final double GLOVES_INFLATION = 1.0;
    private static final double QUIVER_INFLATION = 1.05;
    private static final double SHIELD_INFLATION = 1.5;
    private static final double TRINKET_INFLATION = 1.0;
    private static final float BUBBLE_OPACITY = 0.5F;
    private static final float INVISIBLE_CAPE_OPACITY = 0.15F;
    private static final int GLOVES = 0;
    private static final int CAPE = 1;
    private static final int FIRST_TRINKET = 2;
    private static final int SECOND_TRINKET = 3;
    private static final int CHEST_ARMOR = 37;
    private static final Set<String> PARTS = Set.of("head", "chest", "rightArm", "leftArm", "rightLeg", "leftLeg");

    private AetherCompat() {
    }

    static boolean invisible(Player player) {
        return accessoryItem(player, CAPE) instanceof ItemCapeInvisibility;
    }

    static InventorySnapshot.Cape cape(Player player) {
        if (!(accessoryItem(player, CAPE) instanceof ItemCape cape)) {
            return null;
        }
        String texture = "assets/" + cape.namespaceID.namespace() + "/textures/armor/cape/" + cape.name() + ".png";
        return new InventorySnapshot.Cape(texture, cape instanceof ItemCapeInvisibility ? INVISIBLE_CAPE_OPACITY : 1.0F);
    }

    static List<InventorySnapshot.WornModel> accessories(Player player) {
        List<InventorySnapshot.WornModel> models = new ArrayList<>();
        if (accessoryItem(player, GLOVES) instanceof ItemGloves gloves) {
            String texture = "assets/" + gloves.namespaceID.namespace() + "/textures/armor/gloves/" + gloves.getTextureName() + "_gloves.png";
            models.add(model(ARMOR, GLOVES_INFLATION, texture, Set.of("rightArm", "leftArm"), 1.0F));
        }
        Item cape = accessoryItem(player, CAPE);
        if (cape instanceof ItemQuiver || cape instanceof ItemQuiverEndless) {
            models.add(model(ARMOR, QUIVER_INFLATION, quiverTexture(player, cape instanceof ItemQuiverEndless), Set.of("chest"), 1.0F));
        }
        Item first = accessoryItem(player, FIRST_TRINKET);
        Item second = accessoryItem(player, SECOND_TRINKET);
        if (first instanceof ItemRepulsionShield || second instanceof ItemRepulsionShield) {
            models.add(model(ARMOR, SHIELD_INFLATION, TEXTURES + "energyNotGlow.png", PARTS, 1.0F));
        }
        trinket(models, first, FIRST_TRINKET, first);
        trinket(models, second, SECOND_TRINKET, first);
        return models;
    }

    private static void trinket(List<InventorySnapshot.WornModel> models, Item item, int slot, Item first) {
        if (item == null || item instanceof ItemRepulsionShield || !(item instanceof ItemAccessory<?> || item.hasTag(AetherItemTags.TRINKET))) {
            return;
        }
        boolean left = slot == FIRST_TRINKET;
        String key = ItemTrinket.getTextureKey(item);
        if (item instanceof ItemGoldenFeather) {
            models.add(left
                ? model(FEATHER, TRINKET_INFLATION, TRINKETS + "feather_gold_trinket_helmet.png", Set.of("head"), 1.0F)
                : model(FEATHER, TRINKET_INFLATION, TRINKETS + "feather_gold_trinket_boots.png", Set.of("rightLeg", "leftLeg"), 1.0F));
        } else if (key != null) {
            models.add(model(ARMOR, BASE_INFLATION, TRINKETS + key + ".png", Set.of(left ? "leftLeg" : "rightLeg"), 1.0F));
        } else if (item instanceof ItemPendant pendant) {
            int variant = !left && first instanceof ItemPendant ? 1 : 0;
            String texture = "assets/" + pendant.namespaceID.namespace() + "/textures/armor/pendants/" + pendant.name() + "_pendant_" + variant + ".png";
            models.add(model(ARMOR, BASE_INFLATION, texture, Set.of("chest"), 1.0F));
        } else if (item instanceof ItemRegenStone) {
            models.add(model(ARMOR, TRINKET_INFLATION, TRINKETS + (left ? "regen_trinket_left.png" : "regen_trinket_right.png"), Set.of("head"), 1.0F));
        } else if (item instanceof ItemIronBubble && (left || !(first instanceof ItemIronBubble))) {
            models.add(model(ARMOR, TRINKET_INFLATION, TRINKETS + "bubble_trinket.png", Set.of("head"), BUBBLE_OPACITY));
        }
    }

    private static String quiverTexture(Player player, boolean endless) {
        ItemStack chest = player.inventory.getItem(CHEST_ARMOR);
        boolean flipped = chest != null && (chest.getItem() instanceof ItemQuiver || chest.getItem() instanceof ItemQuiverEndless);
        String name = endless ? "quiver_golden" : "quiver";
        return flipped ? TEXTURES + name + "_flipped.png" : "assets/minecraft/textures/armor/" + name + ".png";
    }

    private static InventorySnapshot.WornModel model(String geometry, double inflation, String texture, Set<String> visible, float opacity) {
        Set<String> hidden = new HashSet<>(PARTS);
        hidden.removeAll(visible);
        return new InventorySnapshot.WornModel(geometry, inflation, texture, Set.copyOf(hidden), opacity);
    }

    static ItemStack accessory(Player player, int slot) {
        return ((IContainerInventoryAether) player.inventory).aether$getAccessoryInventory()[slot];
    }

    private static Item accessoryItem(Player player, int slot) {
        ItemStack stack = accessory(player, slot);
        return stack == null ? null : stack.getItem();
    }
}
