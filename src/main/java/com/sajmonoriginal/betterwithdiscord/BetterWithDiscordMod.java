package com.sajmonoriginal.betterwithdiscord;

import com.sajmonoriginal.betterwithdiscord.config.BetterWithDiscordConfig;
import com.sajmonoriginal.betterwithdiscord.image.GameImages;
import com.sajmonoriginal.betterwithdiscord.preview.PreviewCommand;
import com.sajmonoriginal.betterwithdiscord.server.DiscordClient;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.net.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.event.defs.ServerEvents;
import turniplabs.halplibe.util.dependency.Key;

import java.nio.file.Path;

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

        ServerEvents.AFTER_SERVER_START.listen(Key.of(MOD_ID), () -> {
            loadImages();
            DiscordClient.onServerStarted();
        });
        CommandManager.registerServerCommand(new PreviewCommand());
        Thread startup = new Thread(DiscordClient::start, "Better With Discord startup");
        startup.setDaemon(true);
        startup.start();
    }

    private static void loadImages() {
        if (!BetterWithDiscordConfig.itemImages) {
            return;
        }
        Path cache = FabricLoader.getInstance().getGameDir().resolve("cache").resolve(MOD_ID);
        Thread images = new Thread(() -> GameImages.load(cache, BetterWithDiscordConfig.tooltipStyle), "Better With Discord assets");
        images.setDaemon(true);
        images.start();
    }
}
