package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
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

        String commandName = message.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }

        if (BetterWithDiscordConfig.consoleCommandBlacklist.contains(commandName)) {
            sendResponse(event.getChannel(), "Command " + commandName + " is blacklisted.");
            return;
        }

        if (!BetterWithDiscordConfig.consoleAllowedCommands.isEmpty() && !BetterWithDiscordConfig.consoleAllowedCommands.contains(commandName)) {
            sendResponse(event.getChannel(), "Command " + commandName + " is not allowed.");
            return;
        }

        if (!logCommand(event.getAuthor(), message)) {
            sendResponse(event.getChannel(), "Command was not run because it could not be logged.");
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

    private boolean logCommand(User author, String command) {
        Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve(BetterWithDiscordMod.MOD_ID + "-commands.log");
        String line = "[" + Instant.now() + "] " + author.getName() + " (" + author.getId() + "): " + command.replaceAll("\\R", " ") + System.lineSeparator();
        try {
            Files.createDirectories(log.getParent());
            Files.writeString(log, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            BetterWithDiscordMod.LOGGER.error("Could not write {}", log, e);
            return false;
        }
    }

    private void sendResponse(MessageChannel channel, String message) {
        channel.sendMessage(MarkdownUtil.codeblock(message)).queue();
    }
}
