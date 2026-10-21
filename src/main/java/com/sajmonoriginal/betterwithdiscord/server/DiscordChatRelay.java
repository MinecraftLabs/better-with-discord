package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.ChatEmotes;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.util.helper.NetCharacters;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

public class DiscordChatRelay {
    private static final int MAX_LINE_LENGTH = 256;
    private static final int MAX_LINES = 10;
    private static final int MAX_NAME_LENGTH = 80;
    private static final List<String> RESERVED_NAMES = List.of("clyde", "discord");

    public static void sendToMinecraft(String author, String message) {
        MinecraftServer server = MinecraftServer.getInstance();
        String prefix = "[" + TextFormatting.PURPLE + "DISCORD" + TextFormatting.RESET + "] <" + clean(author) + "> ";
        int sent = 0;
        for (String line : message.split("\n")) {
            String text = ChatEmotes.process(clean(line)).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > MAX_LINE_LENGTH) {
                text = text.substring(0, MAX_LINE_LENGTH);
            }
            String chat = prefix + text;
            BetterWithDiscordMod.LOGGER.info(chat);
            server.playerList.sendEncryptedChatToAllPlayers(chat);
            if (++sent == MAX_LINES) {
                break;
            }
        }
    }

    private static String clean(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != TextFormatting.SPECIAL_CHAR && NetCharacters.isAllowed(c)) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    public static void sendToDiscord(Player player, String message) {
        String content = MarkdownSanitizer.escape(TextFormatting.removeAllFormatting(message));
        String name = webhookName(TextFormatting.removeAllFormatting(player.getDisplayName()));
        String avatar = "https://www.mc-heads.net/head/" + player.username + "/150";
        send(webhook -> webhook.sendMessage(content)
                .setUsername(name)
                .setAvatarUrl(avatar)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)));
    }

    public static void sendJoinLeaveMessage(Player player, boolean joined) {
        String name = TextFormatting.removeAllFormatting(player.getDisplayName());
        sendEmbed(new EmbedBuilder()
                .setColor(joined ? 0x00FF00 : 0xFF0000)
                .setAuthor(name + (joined ? " joined" : " left") + " the server", null, "https://www.mc-heads.net/head/" + player.username)
                .build());
    }

    public static void sendDeathMessage(String deathMessage) {
        sendEmbed(new EmbedBuilder()
                .setColor(0xFF0000)
                .setAuthor(deathMessage, null, "https://i.imgur.com/l1EJ6fx.png")
                .build());
    }

    public static void sendServerStartMessage() {
        sendEmbed(new EmbedBuilder()
                .setColor(0x4ae485)
                .setAuthor("Server started!")
                .setTimestamp(Instant.now())
                .build());
    }

    public static void sendServerStoppedMessage() {
        Webhook webhook = DiscordClient.webhook;
        if (webhook == null) return;
        MessageEmbed embed = new EmbedBuilder()
                .setColor(0xf92f60)
                .setAuthor("Server stopped!")
                .setTimestamp(Instant.now())
                .build();
        try {
            webhook.sendMessageEmbeds(embed)
                    .setUsername(webhookName(BetterWithDiscordConfig.serverName))
                    .setAvatarUrl(BetterWithDiscordConfig.serverAvatarUrl)
                    .submit()
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            BetterWithDiscordMod.LOGGER.warn("Could not send the stop message to Discord", e);
        }
    }

    public static void sendServerFrozenMessage(int seconds) {
        sendEmbed(new EmbedBuilder()
                .setColor(0xf9a62f)
                .setAuthor("The server has not responded for " + seconds + " seconds")
                .setTimestamp(Instant.now())
                .build());
    }

    public static void sendServerRecoveredMessage() {
        sendEmbed(new EmbedBuilder()
                .setColor(0x4ae485)
                .setAuthor("The server is responding again")
                .setTimestamp(Instant.now())
                .build());
    }

    public static void sendServerSleepMessage() {
        sendEmbed(new EmbedBuilder()
                .setColor(0x222d5a)
                .setAuthor("The Night was Skipped", null, "https://i.imgur.com/R1e1sJS.png")
                .build());
    }

    private static void sendEmbed(MessageEmbed embed) {
        send(webhook -> webhook.sendMessageEmbeds(embed)
                .setUsername(webhookName(BetterWithDiscordConfig.serverName))
                .setAvatarUrl(BetterWithDiscordConfig.serverAvatarUrl));
    }

    private static String webhookName(String name) {
        StringBuilder allowed = new StringBuilder(name.trim());
        for (String reserved : RESERVED_NAMES) {
            int at = allowed.toString().toLowerCase(Locale.ROOT).indexOf(reserved);
            while (at >= 0) {
                allowed.insert(at + 1, '-');
                at = allowed.toString().toLowerCase(Locale.ROOT).indexOf(reserved, at + 1);
            }
        }
        if (allowed.isEmpty()) {
            return "Player";
        }
        return allowed.length() > MAX_NAME_LENGTH ? allowed.substring(0, MAX_NAME_LENGTH) : allowed.toString();
    }

    private static void send(Function<Webhook, WebhookMessageCreateAction<Message>> message) {
        Webhook webhook = DiscordClient.webhook;
        if (webhook == null) return;
        message.apply(webhook).queue(null, error -> {
            if (error instanceof ErrorResponseException response && response.getErrorResponse() == ErrorResponse.UNKNOWN_WEBHOOK) {
                DiscordClient.replaceWebhook(webhook).thenAccept(replacement -> message.apply(replacement).queue());
            } else {
                BetterWithDiscordMod.LOGGER.warn("Could not send a message to Discord", error);
            }
        });
    }
}
