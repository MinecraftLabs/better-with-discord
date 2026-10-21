package com.sajmonoriginal.betterwithdiscord.server;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.player.PlayerServer;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

public class DiscordCommands extends ListenerAdapter {
    private static final String PLAYERLIST = "playerlist";

    public static void register(Guild guild) {
        guild.updateCommands()
                .addCommands(Commands.slash(PLAYERLIST, "Show who is playing on the server"))
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals(PLAYERLIST)) {
            return;
        }

        event.deferReply().queue();
        ServerThread.execute(() -> {
            MinecraftServer server = MinecraftServer.getInstance();
            List<String> names = server.playerList.playerEntities.stream()
                    .map(PlayerServer::getDisplayName)
                    .map(TextFormatting::removeAllFormatting)
                    .map(MarkdownSanitizer::escape)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            String text = names.isEmpty()
                    ? "Nobody is online."
                    : "**" + names.size() + "/" + server.maxPlayers + "** players online\n" + String.join(", ", names);
            String reply = text.length() > Message.MAX_CONTENT_LENGTH
                    ? text.substring(0, Message.MAX_CONTENT_LENGTH - 3) + "..."
                    : text;
            event.getHook().sendMessage(reply)
                    .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                    .queue();
        });
    }
}
