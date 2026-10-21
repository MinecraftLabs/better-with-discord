package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.packet.Packet;
import net.minecraft.core.util.phys.Vec3;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.pos.TilePosc;
import net.minecraft.server.MinecraftServer;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.function.Supplier;

public class DiscordCommandSource implements CommandSource {

    private final MessageChannel channel;
    private final String executorName;

    public DiscordCommandSource(MessageChannel channel, String executorName) {
        this.channel = channel;
        this.executorName = executorName;
    }

    @Override
    public void sendMessage(String message) {
        BetterWithDiscordMod.LOGGER.info("[Discord Console Output] {}", message);
        sendToDiscord(message);
    }

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public String getName() {
        return "Discord:" + executorName;
    }

    @Override
    public Player getSender() {
        return null;
    }

    public Vec3 getPosition() {
        return Vec3.getTempVec3(0, 64, 0);
    }

    @Override
    public World getWorld() {
        MinecraftServer server = MinecraftServer.getInstance();
        if (server != null) {
            return server.getDimensionWorld(0);
        }
        return null;
    }

    @Override
    public World getWorld(int dimension) {
        MinecraftServer server = MinecraftServer.getInstance();
        if (server != null) {
            return server.getDimensionWorld(dimension);
        }
        return null;
    }

    @Override
    public void teleportPlayerToPosAndRot(Player player, double x, double y, double z, float yaw, float pitch) {
    }

    @Override
    public void teleportPlayerToPos(Player player, double x, double y, double z) {
    }

    @Override
    public void movePlayerToDimension(Player player, int dimension) {
    }

    @Override
    public void sendPacketToAllPlayers(Supplier<Packet> packetSupplier) {
        MinecraftServer server = MinecraftServer.getInstance();
        if (server != null && server.playerList != null) {
            server.playerList.sendPacketToAllPlayers(packetSupplier.get());
        }
    }

    @Override
    public void sendMessageToAllPlayers(String message) {
    }

    @Override
    public void sendMessage(Player player, String message) {
    }

    @Override
    public boolean messageMayBeMultiline() {
        return true;
    }

    @Override
    public TilePosc getBlockCoordinates() {
        return new TilePos(0, 64, 0);
    }

    @Override
    public Vector3dc getCoordinates(boolean blockCoordinates) {
        return new Vector3d(0, 64, 0);
    }

    @Override
    public java.util.Collection<String> getPlayerNicknames() {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.Collection<String> getPlayerUsernames() {
        return java.util.Collections.emptyList();
    }

    private void sendToDiscord(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        if (message.length() > 1900) {
            message = message.substring(0, 1900) + "...";
        }

        try {
            channel.sendMessage("```\n" + message + "\n```").queue();
        } catch (Exception ignored) {
        }
    }
}
