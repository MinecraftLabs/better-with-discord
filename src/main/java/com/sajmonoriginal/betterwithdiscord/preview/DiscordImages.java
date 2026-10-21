package com.sajmonoriginal.betterwithdiscord.preview;

import net.dv8tion.jda.api.utils.AttachmentProxy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DiscordImages {
    private static final int LIMIT = 100;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Map<Integer, Entry> ENTRIES = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Entry> eldest) {
            return size() > LIMIT;
        }
    };
    private static int nextId = 1;

    private DiscordImages() {
    }

    public static boolean supported(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static synchronized Entry add(String name, AttachmentProxy file, int size) {
        Entry entry = new Entry(nextId++, name, file, size);
        ENTRIES.put(entry.id(), entry);
        return entry;
    }

    public static synchronized Entry get(int id) {
        return ENTRIES.get(id);
    }

    public static synchronized Entry latest() {
        Entry latest = null;
        for (Entry entry : ENTRIES.values()) {
            latest = entry;
        }
        return latest;
    }

    public record Entry(int id, String name, AttachmentProxy file, int size) {
    }
}
