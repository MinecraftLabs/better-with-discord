package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class DiscordConsoleListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
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

        String commandName = message.split(" ")[0].toLowerCase(Locale.ROOT);
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }

        if (BetterWithDiscordConfig.consoleCommandBlacklist.contains(commandName)) {
            sendResponse(event.getChannel(), "Command `" + commandName + "` is blacklisted.");
            return;
        }

        executeCommand(message, event.getAuthor().getName());
    }

    private void executeCommand(String command, String executor) {
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        MinecraftServer server = MinecraftServer.getInstance();
        if (server == null) {
            return;
        }

        BetterWithDiscordMod.LOGGER.info("[Discord Console] {} executed: {}", executor, command);
        server.addCommand(command, server);
    }

    private void sendResponse(MessageChannel channel, String message) {
        channel.sendMessage("```\n" + message + "\n```").queue();
    }
}
