package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordMentions {
    private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}])@([\\p{L}\\p{N}_.]{2,32})");
    private static final int QUERY_LIMIT = 10;
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(5);

    private DiscordMentions() {
    }

    static Resolved resolve(String content, Guild guild) {
        Matcher matcher = MENTION.matcher(content);
        if (guild == null || !matcher.find()) {
            return new Resolved(content, List.of());
        }
        Map<String, Optional<IMentionable>> found = new HashMap<>();
        List<IMentionable> mentions = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        do {
            String name = matcher.group(1);
            Optional<IMentionable> target = found.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> Optional.ofNullable(find(guild, name)));
            if (target.isPresent()) {
                if (!mentions.contains(target.get())) {
                    mentions.add(target.get());
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(target.get().getAsMention()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        } while (matcher.find());
        matcher.appendTail(result);
        return new Resolved(result.toString(), List.copyOf(mentions));
    }

    private static IMentionable find(Guild guild, String name) {
        try {
            List<Member> members = guild.retrieveMembersByPrefix(name, QUERY_LIMIT).setTimeout(QUERY_TIMEOUT).get();
            for (Member member : members) {
                if (member.getEffectiveName().equalsIgnoreCase(name) || member.getUser().getName().equalsIgnoreCase(name)) {
                    return member;
                }
            }
        } catch (RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not look up the Discord member {}", name, e);
        }
        for (Role role : guild.getRolesByName(name, true)) {
            if (role.isMentionable() && !role.isPublicRole()) {
                return role;
            }
        }
        return null;
    }

    record Resolved(String content, List<IMentionable> mentions) {
    }
}
