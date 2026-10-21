package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public class DiscordClient {
    public static volatile JDA jda;
    public static volatile Webhook webhook;
    private static StandardGuildMessageChannel channel;
    private static boolean ready;
    private static boolean serverStarted;

    public static void start() {
        try {
            JDA client = JDABuilder.createLight(
                            BetterWithDiscordConfig.token,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .addEventListeners(new Listener(), new DiscordConsoleListener())
                    .build();
            jda = client.awaitReady();
            webhook = findWebhook();

            if (BetterWithDiscordConfig.consoleEnabled) {
                ConsoleAppender.register();
                BetterWithDiscordMod.LOGGER.info("Console forwarding enabled to channel: {}", BetterWithDiscordConfig.consoleChannel);
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
        return owned.orElseGet(() -> channel.createWebhook("BTA Chat Link").complete());
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

            Member member = event.getMember();
            String author = member != null ? member.getEffectiveName() : event.getAuthor().getEffectiveName();
            String content = event.getMessage().getContentStripped();
            ServerThread.execute(() -> DiscordChatRelay.sendToMinecraft(author, content));
        }
    }
}
