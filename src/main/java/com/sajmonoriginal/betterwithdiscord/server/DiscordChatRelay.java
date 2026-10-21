package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.image.GameImages;
import com.sajmonoriginal.betterwithdiscord.image.ItemSnapshot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.ChatEmotes;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.util.helper.NetCharacters;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordChatRelay {
    private static final int MAX_LINE_LENGTH = 256;
    private static final int MAX_LINES = 10;
    private static final Pattern ITEM_PLACEHOLDER = Pattern.compile("(?i)\\[(item|i)]");
    private static final String TOOLTIP_FILE = "tooltip.png";
    private static final String ICON_FILE = "icon.png";
    private static final ExecutorService CHAT = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord chat");
        thread.setDaemon(true);
        return thread;
    });
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

    public static String relayChat(Player player, String message) {
        Matcher placeholder = ITEM_PLACEHOLDER.matcher(message);
        ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !placeholder.find()) {
            sendToDiscord(player, message, null);
            return message;
        }

        ItemSnapshot item = ItemSnapshot.of(held, player.world);
        String count = item.count() > 1 ? " x" + item.count() : "";
        String inGame = "[" + TextFormatting.get(item.colorIndex()) + item.name() + count + TextFormatting.RESET + TextFormatting.WHITE + "]";
        sendToDiscord(player, placeholder.replaceAll(Matcher.quoteReplacement("[" + item.name() + count + "]")), item);
        return placeholder.replaceAll(Matcher.quoteReplacement(inGame));
    }

    private static void sendToDiscord(Player player, String message, ItemSnapshot item) {
        String content = MarkdownSanitizer.escape(TextFormatting.removeAllFormatting(message));
        String name = webhookName(TextFormatting.removeAllFormatting(player.getDisplayName()));
        String avatar = "https://www.mc-heads.net/head/" + player.username + "/150";
        CHAT.execute(() -> {
            GameImages images = item == null ? null : GameImages.get();
            MessageEmbed embed = null;
            byte[] tooltip = null;
            byte[] icon = null;
            if (images != null) {
                try {
                    tooltip = images.tooltip(item);
                    icon = images.icon(item);
                    embed = itemEmbed(name, avatar, item, images.color(item.colorIndex()), icon != null);
                } catch (RuntimeException e) {
                    BetterWithDiscordMod.LOGGER.warn("Could not draw the picture of {}", item.name(), e);
                }
            }
            MessageEmbed itemEmbed = embed;
            byte[] tooltipPng = tooltip;
            byte[] iconPng = icon;
            send(webhook -> {
                WebhookMessageCreateAction<Message> action = webhook.sendMessage(content)
                        .setUsername(name)
                        .setAvatarUrl(avatar)
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
                if (itemEmbed != null) {
                    action.addEmbeds(itemEmbed).addFiles(FileUpload.fromData(tooltipPng, TOOLTIP_FILE));
                    if (iconPng != null) {
                        action.addFiles(FileUpload.fromData(iconPng, ICON_FILE));
                    }
                }
                return action;
            });
        });
    }

    private static MessageEmbed itemEmbed(String player, String avatar, ItemSnapshot item, int color, boolean withIcon) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(color)
                .setAuthor(player + "'s Item", null, avatar)
                .setTitle(item.name() + (item.count() > 1 ? " x" + item.count() : ""))
                .setImage("attachment://" + TOOLTIP_FILE);
        if (withIcon) {
            embed.setThumbnail("attachment://" + ICON_FILE);
        }
        return embed.build();
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
