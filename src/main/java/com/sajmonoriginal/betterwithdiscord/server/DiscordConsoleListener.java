package com.sajmonoriginal.betterwithdiscord.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.world.World;
import net.minecraft.server.MinecraftServer;

public class DiscordConsoleListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!BetterWithDiscordConfig.consoleEnabled || !BetterWithDiscordConfig.consoleCommandsEnabled) {
            return;
        }

        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) {
            return;
        }

        if (!event.getChannel().getId().equals(BetterWithDiscordConfig.consoleChannel)) {
            return;
        }

        String message = event.getMessage().getContentRaw().trim();
        if (message.isEmpty()) {
            return;
        }

        String prefix = BetterWithDiscordConfig.consoleCommandPrefix;
        if (!prefix.isEmpty()) {
            if (!message.startsWith(prefix)) {
                return;
            }
            message = message.substring(prefix.length()).trim();
        }

        if (message.isEmpty()) {
            return;
        }

        String commandName = message.split(" ")[0].toLowerCase();
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }
        
        if (BetterWithDiscordConfig.consoleCommandBlacklist.contains(commandName)) {
            sendResponse(event.getChannel(), "Command `" + commandName + "` is blacklisted.");
            return;
        }

        executeCommand(message, event.getChannel(), event.getAuthor().getName());
    }

    private void executeCommand(String command, MessageChannel responseChannel, String executor) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        BetterWithDiscordMod.LOGGER.info("[Discord Console] {} executed: {}", executor, command);

        MinecraftServer server = MinecraftServer.getInstance();
        if (server == null) {
            sendResponse(responseChannel, "Server is not running.");
            return;
        }

        World world = server.getDimensionWorld(0);
        if (world == null) {
            sendResponse(responseChannel, "World not loaded.");
            return;
        }

        CommandManager commandManager = world.getCommandManager();
        if (commandManager == null) {
            sendResponse(responseChannel, "Command system not available.");
            return;
        }

        String finalCommand = command;
        try {
            DiscordCommandSource source = new DiscordCommandSource(responseChannel, executor);
            commandManager.execute(finalCommand, source);
        } catch (CommandSyntaxException e) {
            sendResponse(responseChannel, "Error: " + e.getMessage());
        } catch (Exception e) {
            BetterWithDiscordMod.LOGGER.error("Error executing command from Discord: {}", finalCommand, e);
            sendResponse(responseChannel, "Error: " + e.getMessage());
        }
    }

    private void sendResponse(MessageChannel channel, String message) {
        try {
            channel.sendMessage("```\n" + message + "\n```").queue();
        } catch (Exception ignored) {
        }
    }
}
