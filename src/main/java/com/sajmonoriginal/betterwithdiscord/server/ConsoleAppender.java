package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.minecraft.core.net.command.TextFormatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
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
    private static final int MAX_TRACE_LINES = 20;
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

        String batch = "";
        String line;
        while ((line = lines.poll()) != null) {
            String joined = batch.isEmpty() ? line : batch + "\n" + line;
            if (!batch.isEmpty() && MarkdownUtil.codeblock(joined).length() > Message.MAX_CONTENT_LENGTH) {
                send(channel, batch);
                joined = line;
            }
            batch = joined;
        }
        if (!batch.isEmpty()) {
            send(channel, batch);
        }
    }

    private static void send(MessageChannel channel, String content) {
        String block = MarkdownUtil.codeblock(content);
        if (block.length() > Message.MAX_CONTENT_LENGTH) {
            block = MarkdownUtil.codeblock(content.substring(0, Math.max(0, content.length() - (block.length() - Message.MAX_CONTENT_LENGTH) - 3)) + "...");
        }
        channel.sendMessage(block).queue(null, error -> BetterWithDiscordMod.LOGGER.debug("Could not send console lines to Discord", error));
    }

    private static MessageChannel consoleChannel() {
        JDA jda = DiscordClient.jda;
        if (jda == null || BetterWithDiscordConfig.consoleChannel.isEmpty()) {
            return null;
        }
        return jda.getChannelById(StandardGuildMessageChannel.class, BetterWithDiscordConfig.consoleChannel);
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

        String message = TextFormatting.removeAllFormatting(event.getMessage().getFormattedMessage());
        lines.offer(String.format("[%s %s] [%s] %s", TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeMillis())), event.getLevel().name(), logger, message));
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            StringWriter trace = new StringWriter();
            thrown.printStackTrace(new PrintWriter(trace));
            trace.toString().lines().limit(MAX_TRACE_LINES).forEach(lines::offer);
        }
    }
}
