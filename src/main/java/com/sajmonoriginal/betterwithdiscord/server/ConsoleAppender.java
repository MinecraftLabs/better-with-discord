package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.minecraft.core.net.command.TextFormatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsoleAppender extends AbstractAppender {
    private static final int MAX_MESSAGE_LENGTH = 1900;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final List<String> IGNORED_LOGGERS = List.of(
            "net.dv8tion", "JDA", "WebSocketClient", "Requester", "RestAction", "RestRateLimiter",
            "Decompressor", "FabricLoader/Mixin", "halplibe"
    );

    private static ConsoleAppender instance;

    private final Queue<String> lines = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord console");
        thread.setDaemon(true);
        return thread;
    });

    private ConsoleAppender() {
        super("DiscordConsole", null, null, true, Property.EMPTY_ARRAY);
    }

    public static synchronized void register() {
        if (instance != null) {
            return;
        }
        instance = new ConsoleAppender();
        instance.start();
        instance.scheduler.scheduleWithFixedDelay(instance::flush, 2, 2, TimeUnit.SECONDS);
        rootLogger().addAppender(instance);
    }

    public static synchronized void unregister() {
        if (instance == null) {
            return;
        }
        rootLogger().removeAppender(instance);
        instance.scheduler.shutdown();
        instance.flush();
        instance.stop();
        instance = null;
    }

    private static Logger rootLogger() {
        return (Logger) LogManager.getRootLogger();
    }

    private synchronized void flush() {
        MessageChannel channel = consoleChannel();
        if (channel == null) {
            lines.clear();
            return;
        }

        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = lines.poll()) != null) {
            if (batch.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
                send(channel, batch);
                batch.setLength(0);
            }
            if (!batch.isEmpty()) {
                batch.append('\n');
            }
            batch.append(line);
        }
        if (!batch.isEmpty()) {
            send(channel, batch);
        }
    }

    private static void send(MessageChannel channel, CharSequence content) {
        channel.sendMessage("```\n" + content + "\n```").queue();
    }

    private static MessageChannel consoleChannel() {
        JDA jda = DiscordClient.jda;
        if (jda == null || BetterWithDiscordConfig.consoleChannel.isEmpty()) {
            return null;
        }
        return jda.getTextChannelById(BetterWithDiscordConfig.consoleChannel);
    }

    @Override
    public void append(LogEvent event) {
        if (event.getLevel().intLevel() > Level.INFO.intLevel()) {
            return;
        }

        String logger = event.getLoggerName();
        if (logger != null) {
            for (String ignored : IGNORED_LOGGERS) {
                if (logger.contains(ignored)) {
                    return;
                }
            }
            if (logger.length() > 20) {
                logger = logger.substring(logger.lastIndexOf('.') + 1);
            }
        }

        String message = TextFormatting.removeAllFormatting(event.getMessage().getFormattedMessage()).replace("```", "``\u200b`");
        String line = String.format("[%s %s] [%s] %s", TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeMillis())), event.getLevel().name(), logger, message);
        if (line.length() > MAX_MESSAGE_LENGTH) {
            line = line.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        lines.offer(line);
    }
}
