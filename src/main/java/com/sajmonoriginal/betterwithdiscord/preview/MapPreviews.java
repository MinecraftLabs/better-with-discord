package com.sajmonoriginal.betterwithdiscord.preview;

import com.mojang.nbt.tags.CompoundTag;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.image.MapSnapshot;
import com.sajmonoriginal.betterwithdiscord.server.ServerThread;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.net.packet.PacketContainerSetSlot;
import net.minecraft.core.net.packet.PacketMapData;
import net.minecraft.core.player.inventory.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.player.PlayerServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MapPreviews {
    private static final short PREVIEW_MAP_ID = Short.MAX_VALUE;
    private static final byte SCALE_WITHOUT_TERRAIN = 30;
    private static final int CACHED_IMAGES = 8;
    private static final int TICK_MS = 50;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord previews");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, CompletableFuture<List<PreviewFrames.Frame>>> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CompletableFuture<List<PreviewFrames.Frame>>> eldest) {
            return size() > CACHED_IMAGES;
        }
    };
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static MapPalette palette;
    private static long ticks;

    private MapPreviews() {
    }

    public static boolean enabled() {
        return BetterWithDiscordConfig.imagePreviewSeconds > 0;
    }

    public static void request(PlayerServer player, DiscordImages.Entry entry) {
        UUID uuid = player.uuid;
        frames(entry).whenComplete((frames, error) -> ServerThread.execute(() -> {
            PlayerServer online = online(uuid);
            if (online == null) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                BetterWithDiscordMod.LOGGER.debug("Could not load the image {}", entry.name(), cause);
                online.sendMessage(TextFormatting.RED + "Could not load " + entry.name() + ": " + cause.getMessage());
                return;
            }
            show(online, entry, frames);
        }));
    }

    private static synchronized CompletableFuture<List<PreviewFrames.Frame>> frames(DiscordImages.Entry entry) {
        CompletableFuture<List<PreviewFrames.Frame>> cached = CACHE.get(entry.id());
        if (cached != null && !cached.isCompletedExceptionally()) {
            return cached;
        }
        CompletableFuture<List<PreviewFrames.Frame>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return PreviewFrames.load(entry, palette());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, LOADER);
        CACHE.put(entry.id(), future);
        return future;
    }

    private static synchronized MapPalette palette() {
        if (palette == null) {
            palette = MapPalette.create();
        }
        return palette;
    }

    private static void show(PlayerServer player, DiscordImages.Entry entry, List<PreviewFrames.Frame> frames) {
        Session old = SESSIONS.remove(player.uuid);
        if (old != null) {
            old.restore();
        }
        int hotbar = player.inventory.getCurrentSlot();
        Slot slot = player.inventoryMenu.getSlotFor(player.inventory, hotbar);
        if (slot == null) {
            return;
        }
        Session session = new Session(player, hotbar, slot.index, player.inventory.getItem(hotbar), frames,
            ticks + BetterWithDiscordConfig.imagePreviewSeconds * 1000L / TICK_MS);
        session.sendFrame(null);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("initialized", true);
        tag.putByte("scale", SCALE_WITHOUT_TERRAIN);
        ItemStack map = new ItemStack(Items.MAP.id, 1, PREVIEW_MAP_ID, tag);
        map.setCustomName(entry.name());
        player.playerNetServerHandler.sendPacket(new PacketContainerSetSlot(0, player.inventoryMenu.getStateId(), slot.index, map));
        SESSIONS.put(player.uuid, session);
    }

    public static void tick() {
        ticks++;
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            PlayerServer player = session.player;
            if (online(player.uuid) != player) {
                iterator.remove();
            } else if (player.inventory.getCurrentSlot() != session.hotbar
                || !ItemStack.areItemStacksEqual(player.inventory.getItem(session.hotbar), session.real)
                || ticks >= session.expires) {
                session.restore();
                iterator.remove();
            } else {
                session.animate();
            }
        }
    }

    private static PlayerServer online(UUID uuid) {
        for (PlayerServer player : MinecraftServer.getInstance().playerList.playerEntities) {
            if (player.uuid.equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static final class Session {
        private final PlayerServer player;
        private final int hotbar;
        private final int menuSlot;
        private final ItemStack real;
        private final List<PreviewFrames.Frame> frames;
        private final long expires;
        private int frame;
        private long nextFrame;

        private Session(PlayerServer player, int hotbar, int menuSlot, ItemStack real, List<PreviewFrames.Frame> frames, long expires) {
            this.player = player;
            this.hotbar = hotbar;
            this.menuSlot = menuSlot;
            this.real = real == null ? null : real.copy();
            this.frames = frames;
            this.expires = expires;
        }

        private void animate() {
            if (frames.size() < 2 || ticks < nextFrame) {
                return;
            }
            byte[] previous = frames.get(frame).columns();
            frame = (frame + 1) % frames.size();
            sendFrame(previous);
        }

        private void sendFrame(byte[] previous) {
            PreviewFrames.Frame current = frames.get(frame);
            byte[] columns = current.columns();
            for (int x = 0; x < MapSnapshot.SIZE; x++) {
                int from = x * MapSnapshot.SIZE;
                if (previous != null && Arrays.equals(previous, from, from + MapSnapshot.SIZE, columns, from, from + MapSnapshot.SIZE)) {
                    continue;
                }
                byte[] data = new byte[MapSnapshot.SIZE + 3];
                data[1] = (byte) x;
                System.arraycopy(columns, from, data, 3, MapSnapshot.SIZE);
                player.playerNetServerHandler.sendPacket(new PacketMapData((short) Items.MAP.id, PREVIEW_MAP_ID, SCALE_WITHOUT_TERRAIN, data, List.of()));
            }
            nextFrame = ticks + Math.max(1, Math.round(current.delayMs() / (float) TICK_MS));
        }

        private void restore() {
            ItemStack stack = player.inventory.getItem(hotbar);
            player.playerNetServerHandler.sendPacket(new PacketContainerSetSlot(0, player.inventoryMenu.getStateId(), menuSlot,
                stack == null ? null : stack.copy()));
        }
    }
}
