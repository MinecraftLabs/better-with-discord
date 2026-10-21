package com.sajmonoriginal.betterwithdiscord;

import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import com.sajmonoriginal.betterwithdiscord.server.DiscordClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterWithDiscordMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "better_with_discord";
    public static final Logger LOGGER = LoggerFactory.getLogger("Better With Discord");

    @Override
    public void onInitializeServer() {
        BetterWithDiscordConfig.load();
        new Thread(() -> {
            LOGGER.info("Starting Discord client...");
            if (DiscordClient.init()) {
                LOGGER.info("Discord client started successfully!");
                DiscordChatRelay.sendServerStartMessage();
            } else {
                LOGGER.warn("Discord client failed to start or is disabled.");
            }
        }).start();
        LOGGER.info("Better With Discord initialized!");
    }

    public static void info(String s) {
        LOGGER.info(s);
    }
}
