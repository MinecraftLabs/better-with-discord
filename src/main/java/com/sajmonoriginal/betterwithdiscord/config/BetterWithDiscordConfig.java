package com.sajmonoriginal.betterwithdiscord.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import net.fabricmc.loader.api.FabricLoader;
import turniplabs.halplibe.util.TomlConfigHandler;
import turniplabs.halplibe.util.toml.Toml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BetterWithDiscordConfig {
    public static boolean enabled;
    public static String token;
    public static String chatChannel;
    public static String serverName;
    public static String serverAvatarUrl;
    public static boolean showPlayerCount;
    public static int watchdogSeconds;
    public static boolean itemImages;
    public static String tooltipStyle;

    public static boolean consoleEnabled;
    public static String consoleChannel;
    public static boolean consoleCommandsEnabled;
    public static String consoleCommandPrefix;
    public static List<String> consoleCommandBlacklist;
    public static List<String> consoleAllowedCommands;

    private BetterWithDiscordConfig() {
    }

    public static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path legacy = configDir.resolve(BetterWithDiscordMod.MOD_ID + ".json");
        JsonObject migrated = null;
        if (!Files.exists(configDir.resolve(BetterWithDiscordMod.MOD_ID + ".cfg")) && Files.exists(legacy)) {
            migrated = readLegacy(legacy);
        }

        Toml defaults = new Toml("Better With Discord");
        defaults.addCategory("Chat relay between the server and a Discord channel", "discord")
                .addEntry("enable", legacyBoolean(migrated, "enable", false))
                .addEntry("token", "Bot token from the Discord Developer Portal", legacyString(migrated, "token", ""))
                .addEntry("channel", "ID of the chat channel", legacyString(migrated, "channel", ""))
                .addEntry("server_name", "Name used for server messages", legacyString(migrated, "servername", "Server"))
                .addEntry("server_avatar_url", "Avatar used for server messages", legacyString(migrated, "serverpfp_url", "https://i.imgur.com/dJUId0O.png"))
                .addEntry("show_player_count", "Show the number of online players as the bot status", true)
                .addEntry("watchdog_seconds", "Post a warning when the server stops ticking for this many seconds, 0 turns it off", 60)
                .addEntry("item_images", "Show [item] in chat as a picture of the held item, downloads the BTA client once for its textures", true)
                .addEntry("tooltip_style", "Tooltip style used for item pictures, as in the BTA options", "default");
        defaults.addCategory("Server console in a Discord channel", "console")
                .addEntry("enable", legacyBoolean(migrated, "console_enable", false))
                .addEntry("channel", "ID of the console channel", legacyString(migrated, "console_channel", ""))
                .addEntry("commands", "Run messages sent to the console channel as commands", legacyBoolean(migrated, "console_commands_enable", true))
                .addEntry("command_prefix", "Only run messages that start with this prefix", legacyString(migrated, "console_command_prefix", ""))
                .addEntry("command_blacklist", "Commands that can't be run from Discord, separated by commas", legacyList(migrated, "console_command_blacklist", "stop, op, deop, ban, unban"))
                .addEntry("allowed_commands", "When set, only these commands can be run from Discord, separated by commas", "");

        TomlConfigHandler config = new TomlConfigHandler(BetterWithDiscordMod.MOD_ID, defaults);
        enabled = config.getBoolean("discord.enable");
        token = config.getString("discord.token");
        chatChannel = config.getString("discord.channel");
        serverName = config.getString("discord.server_name");
        serverAvatarUrl = config.getString("discord.server_avatar_url");
        showPlayerCount = config.getBoolean("discord.show_player_count");
        watchdogSeconds = config.getInt("discord.watchdog_seconds");
        itemImages = config.getBoolean("discord.item_images");
        tooltipStyle = config.getString("discord.tooltip_style");
        consoleEnabled = config.getBoolean("console.enable");
        consoleChannel = config.getString("console.channel");
        consoleCommandsEnabled = config.getBoolean("console.commands");
        consoleCommandPrefix = config.getString("console.command_prefix");
        consoleCommandBlacklist = commandList(config.getString("console.command_blacklist"));
        consoleAllowedCommands = commandList(config.getString("console.allowed_commands"));

        if (migrated != null) {
            try {
                Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
                BetterWithDiscordMod.LOGGER.info("Moved the settings from {} to {}.cfg", legacy.getFileName(), BetterWithDiscordMod.MOD_ID);
            } catch (IOException e) {
                BetterWithDiscordMod.LOGGER.warn("Could not rename {}", legacy, e);
            }
        }
    }

    private static List<String> commandList(String value) {
        return Arrays.stream(value.split(","))
                .map(command -> command.trim().toLowerCase(Locale.ROOT))
                .filter(command -> !command.isEmpty())
                .toList();
    }

    private static JsonObject readLegacy(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            BetterWithDiscordMod.LOGGER.warn("Could not read the old config {}", file, e);
            return null;
        }
    }

    private static String legacyString(JsonObject legacy, String key, String fallback) {
        if (legacy == null || !legacy.has(key) || !legacy.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return legacy.get(key).getAsString();
    }

    private static boolean legacyBoolean(JsonObject legacy, String key, boolean fallback) {
        if (legacy == null || !legacy.has(key) || !legacy.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return legacy.get(key).getAsBoolean();
    }

    private static String legacyList(JsonObject legacy, String key, String fallback) {
        if (legacy == null || !legacy.has(key) || !legacy.get(key).isJsonArray()) {
            return fallback;
        }
        StringBuilder joined = new StringBuilder();
        for (JsonElement element : legacy.getAsJsonArray(key)) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(element.getAsString());
        }
        return joined.toString();
    }
}
