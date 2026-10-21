package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.image.GameImages;
import com.sajmonoriginal.betterwithdiscord.image.InventorySnapshot;
import com.sajmonoriginal.betterwithdiscord.image.ItemSnapshot;
import com.sajmonoriginal.betterwithdiscord.preview.DiscordImages;
import com.sajmonoriginal.betterwithdiscord.preview.MapPreviews;
import com.sajmonoriginal.betterwithdiscord.preview.PreviewCommand;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String INVENTORY_FILE = "inventory.png";
    private static final int INVENTORY_COLOR = 0x5ed2ff;
    private static final Pattern INVENTORY_PLACEHOLDER = Pattern.compile("(?i)\\[(inv|inventory)]");
    private static final ExecutorService CHAT = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord chat");
        thread.setDaemon(true);
        return thread;
    });
    private static final int MAX_NAME_LENGTH = 80;
    private static final List<String> RESERVED_NAMES = List.of("clyde", "discord");

    public static void sendToMinecraft(String author, String message, List<DiscordImages.Entry> images) {
        MinecraftServer server = MinecraftServer.getInstance();
        String prefix = "[" + TextFormatting.PURPLE + "DISCORD" + TextFormatting.RESET + "] <" + clean(author) + "> ";
        List<String> lines = new ArrayList<>();
        for (String line : message.split("\n")) {
            String text = ChatEmotes.process(clean(line)).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > MAX_LINE_LENGTH) {
                text = text.substring(0, MAX_LINE_LENGTH);
            }
            lines.add(text);
            if (lines.size() == MAX_LINES) {
                break;
            }
        }
        if (!images.isEmpty()) {
            StringBuilder links = new StringBuilder();
            for (DiscordImages.Entry image : images) {
                if (!links.isEmpty()) {
                    links.append(' ');
                }
                links.append(imageLink(image));
            }
            if (lines.isEmpty()) {
                lines.add(links.toString());
            } else {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + links);
            }
        }
        for (String line : lines) {
            String chat = prefix + line;
            BetterWithDiscordMod.LOGGER.info(chat);
            server.playerList.sendEncryptedChatToAllPlayers(chat);
        }
    }

    private static String imageLink(DiscordImages.Entry image) {
        String link = "[" + TextFormatting.LIGHT_BLUE + image.name() + TextFormatting.RESET + "]";
        if (!MapPreviews.enabled()) {
            return link;
        }
        return link + " " + TextFormatting.LIGHT_GRAY + "/" + PreviewCommand.NAME + " " + image.id() + TextFormatting.RESET;
    }

    static String clean(String text) {
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
        String inGame = message;
        String toDiscord = message;
        ItemSnapshot item = null;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && ITEM_PLACEHOLDER.matcher(message).find()) {
            item = ItemSnapshot.of(held, player.world);
            String count = item.count() > 1 ? " x" + item.count() : "";
            inGame = replace(ITEM_PLACEHOLDER, inGame,
                "[" + TextFormatting.get(item.colorIndex()) + item.name() + count + TextFormatting.RESET + TextFormatting.WHITE + "]");
            toDiscord = replace(ITEM_PLACEHOLDER, toDiscord, "[" + item.name() + count + "]");
        }
        InventorySnapshot inventory = null;
        if (INVENTORY_PLACEHOLDER.matcher(message).find()) {
            inventory = InventorySnapshot.of(player);
            String title = inventory.playerName() + "'s Inventory";
            inGame = replace(INVENTORY_PLACEHOLDER, inGame, "[" + TextFormatting.LIGHT_BLUE + title + TextFormatting.RESET + TextFormatting.WHITE + "]");
            toDiscord = replace(INVENTORY_PLACEHOLDER, toDiscord, "[" + title + "]");
        }
        sendToDiscord(player, toDiscord, item, inventory);
        return inGame;
    }

    private static String replace(Pattern placeholder, String text, String replacement) {
        return placeholder.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static void sendToDiscord(Player player, String message, ItemSnapshot item, InventorySnapshot inventory) {
        String content = MarkdownSanitizer.escape(TextFormatting.removeAllFormatting(message));
        String name = webhookName(TextFormatting.removeAllFormatting(player.getDisplayName()));
        String avatar = "https://www.mc-heads.net/head/" + player.username + "/150";
        CHAT.execute(() -> {
            List<MessageEmbed> embeds = new ArrayList<>();
            Map<String, byte[]> files = new LinkedHashMap<>();
            GameImages images = GameImages.get();
            if (images != null && item != null) {
                try {
                    byte[] icon = images.icon(item);
                    files.put(TOOLTIP_FILE, images.tooltip(item));
                    if (icon != null) {
                        files.put(ICON_FILE, icon);
                    }
                    embeds.add(itemEmbed(name, avatar, item, images.color(item.colorIndex()), icon != null));
                } catch (RuntimeException e) {
                    BetterWithDiscordMod.LOGGER.warn("Could not draw the picture of {}", item.name(), e);
                }
            }
            if (images != null && inventory != null) {
                try {
                    files.put(INVENTORY_FILE, images.inventory(inventory));
                    embeds.add(new EmbedBuilder()
                            .setColor(INVENTORY_COLOR)
                            .setAuthor(inventory.playerName() + "'s Inventory", null, avatar)
                            .setImage("attachment://" + INVENTORY_FILE)
                            .build());
                } catch (RuntimeException e) {
                    BetterWithDiscordMod.LOGGER.warn("Could not draw the inventory of {}", inventory.playerName(), e);
                }
            }
            send(webhook -> {
                WebhookMessageCreateAction<Message> action = webhook.sendMessage(content)
                        .setUsername(name)
                        .setAvatarUrl(avatar)
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
                if (!embeds.isEmpty()) {
                    action.addEmbeds(embeds);
                    files.forEach((file, data) -> action.addFiles(FileUpload.fromData(data, file)));
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
