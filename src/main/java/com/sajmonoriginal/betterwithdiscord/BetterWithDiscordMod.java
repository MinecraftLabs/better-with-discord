package com.sajmonoriginal.betterwithdiscord;

import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.server.DiscordClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.event.defs.ServerEvents;
import turniplabs.halplibe.util.dependency.Key;

public class BetterWithDiscordMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "better_with_discord";
    public static final Logger LOGGER = LoggerFactory.getLogger("Better With Discord");

    @Override
    public void onInitializeServer() {
        BetterWithDiscordConfig.load();
        if (!BetterWithDiscordConfig.enabled) {
            LOGGER.info("Discord relay is turned off in config/{}.cfg", MOD_ID);
            return;
        }

        ServerEvents.AFTER_SERVER_START.listen(Key.of(MOD_ID), DiscordClient::onServerStarted);
        Thread startup = new Thread(DiscordClient::start, "Better With Discord startup");
        startup.setDaemon(true);
        startup.start();
    }
}
