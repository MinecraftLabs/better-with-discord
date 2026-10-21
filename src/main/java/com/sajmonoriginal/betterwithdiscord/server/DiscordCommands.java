package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.GameImages;
import com.sajmonoriginal.betterwithdiscord.image.PlayerListRenderer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.player.PlayerServer;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordCommands extends ListenerAdapter {
    private static final String PLAYERLIST = "playerlist";
    private static final String PLAYERLIST_FILE = "playerlist.png";
    private static final int EMBED_COLOR = 0x4ae485;
    private static final ExecutorService RENDER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Better With Discord player list");
        thread.setDaemon(true);
        return thread;
    });

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
            List<PlayerServer> online = server.playerList.playerEntities;
            GameImages images = GameImages.get();
            if (images == null || online.isEmpty()) {
                replyWithText(event, online, server.maxPlayers);
                return;
            }
            List<PlayerListRenderer.Entry> players = online.stream()
                    .map(player -> new PlayerListRenderer.Entry(player.getDisplayName(), String.valueOf(player.getScore()), player.uuid))
                    .toList();
            int maxPlayers = server.maxPlayers;
            RENDER.execute(() -> {
                try {
                    MessageEmbed embed = new EmbedBuilder().setImage("attachment://" + PLAYERLIST_FILE).setColor(EMBED_COLOR).build();
                    event.getHook().sendMessageEmbeds(embed)
                            .addFiles(FileUpload.fromData(images.playerList(players, maxPlayers), PLAYERLIST_FILE))
                            .queue();
                } catch (RuntimeException e) {
                    BetterWithDiscordMod.LOGGER.warn("Could not draw the player list", e);
                    event.getHook().sendMessage("Could not draw the player list.").queue();
                }
            });
        });
    }

    private static void replyWithText(SlashCommandInteractionEvent event, List<PlayerServer> online, int maxPlayers) {
        List<String> names = online.stream()
                .map(PlayerServer::getDisplayName)
                .map(TextFormatting::removeAllFormatting)
                .map(MarkdownSanitizer::escape)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String text = names.isEmpty()
                ? "Nobody is online."
                : "**" + names.size() + "/" + maxPlayers + "** players online\n" + String.join(", ", names);
        String reply = text.length() > Message.MAX_CONTENT_LENGTH
                ? text.substring(0, Message.MAX_CONTENT_LENGTH - 3) + "..."
                : text;
        event.getHook().sendMessage(reply)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }
}
