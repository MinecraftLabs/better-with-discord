package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Plugin(name = "DiscordConsole", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class ConsoleAppender extends AbstractAppender {

    private static final int MAX_MESSAGE_LENGTH = 1900;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    
    private static final Set<String> IGNORED_LOGGERS = new HashSet<>(Arrays.asList(
            "JDA", "WebSocketClient", "Requester", "RestAction", "RestRateLimiter", 
            "Decompressor", "FabricLoader/Mixin", "halplibe"
    ));

    private static ConsoleAppender instance;
    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    protected ConsoleAppender(String name, Filter filter) {
        super(name, filter, null, true, null);
        instance = this;
        startQueueProcessor();
    }

    @PluginFactory
    public static ConsoleAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new ConsoleAppender(name != null ? name : "DiscordConsole", filter);
    }

    public static ConsoleAppender getInstance() {
        return instance;
    }

    public static void register() {
        if (instance == null) {
            instance = new ConsoleAppender("DiscordConsole", null);
            org.apache.logging.log4j.core.Logger rootLogger = 
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            instance.start();
            rootLogger.addAppender(instance);
        }
    }

    public static void unregister() {
        if (instance != null) {
            org.apache.logging.log4j.core.Logger rootLogger = 
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.removeAppender(instance);
            instance.stop();
            instance.scheduler.shutdown();
            instance = null;
        }
    }

    private void startQueueProcessor() {
        scheduler.scheduleAtFixedRate(this::processQueue, 2, 2, TimeUnit.SECONDS);
    }

    private void processQueue() {
        if (!BetterWithDiscordConfig.consoleEnabled || DiscordClient.jda == null) {
            return;
        }

        MessageChannel channel = getConsoleChannel();
        if (channel == null) {
            return;
        }

        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = messageQueue.poll()) != null) {
            if (batch.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
                sendBatch(channel, batch.toString());
                batch = new StringBuilder();
            }
            if (batch.length() > 0) {
                batch.append("\n");
            }
            batch.append(line);
        }

        if (batch.length() > 0) {
            sendBatch(channel, batch.toString());
        }
    }

    private void sendBatch(MessageChannel channel, String content) {
        try {
            channel.sendMessage("```\n" + content + "\n```").queue();
        } catch (Exception ignored) {
        }
    }

    private MessageChannel getConsoleChannel() {
        if (DiscordClient.jda == null || BetterWithDiscordConfig.consoleChannel.isEmpty()) {
            return null;
        }
        return DiscordClient.jda.getTextChannelById(BetterWithDiscordConfig.consoleChannel);
    }

    @Override
    public void append(LogEvent event) {
        if (!BetterWithDiscordConfig.consoleEnabled) {
            return;
        }

        Level level = event.getLevel();
        if (level.intLevel() > Level.INFO.intLevel()) {
            return;
        }

        String loggerName = event.getLoggerName();
        if (loggerName != null) {
            for (String ignored : IGNORED_LOGGERS) {
                if (loggerName.contains(ignored)) {
                    return;
                }
            }
        }

        String time = TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeMillis()));
        String levelName = level.name();
        String logger = loggerName;
        String message = event.getMessage().getFormattedMessage();

        if (logger != null && logger.length() > 20) {
            logger = logger.substring(logger.lastIndexOf('.') + 1);
        }

        String formatted = String.format("[%s %s] [%s] %s", time, levelName, logger, message);
        
        if (formatted.length() > MAX_MESSAGE_LENGTH) {
            formatted = formatted.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }

        messageQueue.offer(formatted);
    }
}
