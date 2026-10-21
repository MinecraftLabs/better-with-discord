package com.sajmonoriginal.betterwithdiscord.image;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ContainerSnapshot(String texture, int rows, boolean attachment, boolean worn, String title, List<InventorySnapshot.Slot> slots) {
    private static final String BAGS = "betterwithbags";
    private static final String BACKPACK_PREFIX = "item/backpack_";
    private static final Map<String, Integer> BACKPACK_ROWS = Map.of("small", 1, "medium", 2, "large", 3, "reinforced", 4, "nether", 3);
    private static final List<String> BACKPACKS_WITH_ATTACHMENT = List.of("large", "reinforced", "nether");
    private static final int SLOTS_LEFT = 8;
    private static final int SLOTS_TOP = 18;
    private static final int SLOT_SPACING = 18;
    private static final int ATTACHMENT_X = 178;

    static ContainerSnapshot of(ItemStack stack, World world) {
        return of(stack, world, false);
    }

    static ContainerSnapshot worn(ItemStack stack, World world) {
        return stack == null ? null : of(stack, world, true);
    }

    static String backpackTier(ItemStack stack) {
        NamespaceID id = stack.getItem().namespaceID;
        if (!BAGS.equals(id.namespace()) || !id.value().startsWith(BACKPACK_PREFIX)) {
            return null;
        }
        String tier = id.value().substring(BACKPACK_PREFIX.length());
        return BACKPACK_ROWS.containsKey(tier) ? tier : null;
    }

    static boolean hasAttachment(ItemStack stack) {
        CompoundTag data = stack.getData();
        return data.containsKey("Attachment") && ItemStack.readItemStackFromNbt(data.getCompound("Attachment")) != null;
    }

    private static ContainerSnapshot of(ItemStack stack, World world, boolean worn) {
        String tier = backpackTier(stack);
        if (tier == null) {
            return null;
        }
        int rows = BACKPACK_ROWS.get(tier);
        CompoundTag data = stack.getData();
        List<InventorySnapshot.Slot> slots = new ArrayList<>();
        ItemStack[] contents = new ItemStack[rows * 9];
        if (data.containsKey("BackpackInventory")) {
            ListTag list = data.getList("BackpackInventory");
            for (int i = 0; i < list.tagCount(); i++) {
                CompoundTag itemTag = (CompoundTag) list.tagAt(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < contents.length) {
                    contents[slot] = ItemStack.readItemStackFromNbt(itemTag);
                }
            }
        }
        for (int i = 0; i < contents.length; i++) {
            slots.add(new InventorySnapshot.Slot(SLOTS_LEFT + i % 9 * SLOT_SPACING, SLOTS_TOP + i / 9 * SLOT_SPACING,
                contents[i] == null ? null : ItemSnapshot.contained(contents[i], world), null));
        }
        boolean attachment = BACKPACKS_WITH_ATTACHMENT.contains(tier);
        if (attachment && data.containsKey("Attachment")) {
            ItemStack bedroll = ItemStack.readItemStackFromNbt(data.getCompound("Attachment"));
            if (bedroll != null) {
                slots.add(new InventorySnapshot.Slot(ATTACHMENT_X, SLOTS_TOP, ItemSnapshot.contained(bedroll, world), null));
            }
        }
        String texture = "assets/" + BAGS + "/textures/gui/" + (worn ? "backpack_extension_" : "backpack_") + tier + ".png";
        return new ContainerSnapshot(texture, rows, attachment, worn,
            TextFormatting.removeAllFormatting(stack.getDisplayName()), List.copyOf(slots));
    }
}
