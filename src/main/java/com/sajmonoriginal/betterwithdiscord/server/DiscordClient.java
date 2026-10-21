package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordClient {
    public static volatile JDA jda;
    public static volatile Webhook webhook;
    private static StandardGuildMessageChannel channel;
    private static final String WEBHOOK_NAME = "BTA Chat Link";
    private static CompletableFuture<Webhook> replacement;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord status");
        thread.setDaemon(true);
        return thread;
    });
    private static int shownPlayerCount = -1;
    private static boolean frozen;
    private static boolean ready;
    private static boolean serverStarted;

    public static void start() {
        try {
            JDA client = JDABuilder.createLight(
                            BetterWithDiscordConfig.token,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .addEventListeners(new Listener(), new DiscordConsoleListener(), new DiscordCommands())
                    .build();
            jda = client.awaitReady();
            webhook = findWebhook();
            StandardGuildMessageChannel chat = getChannel();
            if (chat != null) {
                DiscordCommands.register(chat.getGuild());
            }

            if (BetterWithDiscordConfig.consoleEnabled) {
                ConsoleAppender.register();
                BetterWithDiscordMod.LOGGER.info("Console forwarding enabled to channel: {}", BetterWithDiscordConfig.consoleChannel);
            }
            if (BetterWithDiscordConfig.showPlayerCount) {
                SCHEDULER.scheduleWithFixedDelay(() -> ServerThread.execute(DiscordClient::updatePlayerCount), 0, 30, TimeUnit.SECONDS);
            }
            if (BetterWithDiscordConfig.watchdogSeconds > 0) {
                SCHEDULER.scheduleWithFixedDelay(DiscordClient::checkTicking, 5, 5, TimeUnit.SECONDS);
            }
            BetterWithDiscordMod.LOGGER.info("Connected to Discord as {}", client.getSelfUser().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException e) {
            BetterWithDiscordMod.LOGGER.error("Unable to start the Discord bot", e);
            return;
        }

        synchronized (DiscordClient.class) {
            ready = true;
            if (serverStarted) {
                DiscordChatRelay.sendServerStartMessage();
            }
        }
    }

    public static void onServerStarted() {
        synchronized (DiscordClient.class) {
            serverStarted = true;
            if (ready) {
                DiscordChatRelay.sendServerStartMessage();
            }
        }
    }

    public static void stop() {
        JDA client = jda;
        if (client == null) {
            return;
        }

        SCHEDULER.shutdownNow();
        DiscordChatRelay.sendServerStoppedMessage();
        ConsoleAppender.unregister();
        webhook = null;
        jda = null;
        client.shutdown();
        try {
            if (!client.awaitShutdown(Duration.ofSeconds(5))) {
                client.shutdownNow();
            }
        } catch (InterruptedException e) {
            client.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void updatePlayerCount() {
        JDA client = jda;
        MinecraftServer server = MinecraftServer.getInstance();
        if (client == null || server == null) {
            return;
        }
        int count = server.playerList.playerEntities.size();
        if (count != shownPlayerCount) {
            shownPlayerCount = count;
            client.getPresence().setActivity(Activity.playing(count + (count == 1 ? " player" : " players") + " online"));
        }
    }

    private static void checkTicking() {
        synchronized (DiscordClient.class) {
            if (!serverStarted) {
                return;
            }
        }
        boolean stalled = ServerThread.nanosSinceLastTick() > TimeUnit.SECONDS.toNanos(BetterWithDiscordConfig.watchdogSeconds);
        if (stalled && !frozen) {
            frozen = true;
            DiscordChatRelay.sendServerFrozenMessage(BetterWithDiscordConfig.watchdogSeconds);
        } else if (!stalled && frozen) {
            frozen = false;
            DiscordChatRelay.sendServerRecoveredMessage();
        }
    }

    private static @Nullable StandardGuildMessageChannel getChannel() {
        if (jda == null) {
            return null;
        }

        if (channel == null) {
            channel = jda.getChannelById(StandardGuildMessageChannel.class, BetterWithDiscordConfig.chatChannel);
        }

        return channel;
    }

    private static @Nullable Webhook findWebhook() {
        StandardGuildMessageChannel channel = getChannel();
        if (channel == null) {
            BetterWithDiscordMod.LOGGER.warn("Chat channel {} was not found", BetterWithDiscordConfig.chatChannel);
            return null;
        }

        String selfId = jda.getSelfUser().getId();
        Optional<Webhook> owned = channel.retrieveWebhooks().complete().stream()
                .filter(it -> it.getToken() != null && it.getOwnerAsUser() != null && it.getOwnerAsUser().getId().equals(selfId))
                .findFirst();
        return owned.orElseGet(() -> channel.createWebhook(WEBHOOK_NAME).complete());
    }

    public static synchronized CompletableFuture<Webhook> replaceWebhook(Webhook stale) {
        Webhook current = webhook;
        if (current != null && current != stale) {
            return CompletableFuture.completedFuture(current);
        }
        if (replacement == null || replacement.isCompletedExceptionally() || replacement.getNow(null) == stale) {
            StandardGuildMessageChannel channel = getChannel();
            if (channel == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Chat channel is not available"));
            }
            BetterWithDiscordMod.LOGGER.info("The chat webhook was deleted, creating a new one");
            replacement = channel.createWebhook(WEBHOOK_NAME).submit().thenApply(created -> {
                webhook = created;
                return created;
            });
        }
        return replacement;
    }

    public static class Listener extends ListenerAdapter {

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (event.isWebhookMessage() || event.getAuthor().isBot() || event.getAuthor().isSystem() || !event.isFromGuild()) {
                return;
            }

            if (!event.getChannel().getId().equals(BetterWithDiscordConfig.chatChannel)) {
                return;
            }

            Message message = event.getMessage();
            StringBuilder content = new StringBuilder(message.getContentStripped());
            Message referenced = message.getReferencedMessage();
            if (referenced != null) {
                content.insert(0, "(reply to " + displayName(referenced) + ") ");
            }
            for (Message.Attachment attachment : message.getAttachments()) {
                content.append(" [").append(attachment.getFileName()).append(']');
            }
            for (StickerItem sticker : message.getStickers()) {
                content.append(" [").append(sticker.getName()).append(']');
            }

            String author = displayName(message);
            String text = content.toString();
            ServerThread.execute(() -> DiscordChatRelay.sendToMinecraft(author, text));
        }

        private static String displayName(Message message) {
            Member member = message.getMember();
            return member != null ? member.getEffectiveName() : message.getAuthor().getEffectiveName();
        }
    }
}
