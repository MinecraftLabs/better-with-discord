package com.sajmonoriginal.betterwithdiscord.preview;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.server.entity.player.PlayerServer;

public final class PreviewCommand implements CommandManager.CommandRegistry {
    public static final String NAME = "preview";

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(ArgumentBuilderLiteral.<CommandSource>literal(NAME)
            .executes(c -> preview(c.getSource(), DiscordImages.latest()))
            .then(ArgumentBuilderRequired.<CommandSource, Integer>argument("image", ArgumentTypeInteger.integer(1))
                .executes(c -> preview(c.getSource(), DiscordImages.get(c.getArgument("image", Integer.class))))));
    }

    private static int preview(CommandSource source, DiscordImages.Entry entry) {
        if (!(source.getSender() instanceof PlayerServer player)) {
            source.sendMessage("Only players can preview images");
            return 0;
        }
        if (!MapPreviews.enabled()) {
            source.sendMessage(TextFormatting.RED + "Image previews are turned off");
            return 0;
        }
        if (entry == null) {
            source.sendMessage(TextFormatting.RED + "That image is no longer available");
            return 0;
        }
        source.sendMessage(TextFormatting.LIGHT_GRAY + "Loading " + entry.name() + "...");
        MapPreviews.request(player, entry);
        return 1;
    }
}
