package com.sajmonoriginal.betterwithdiscord.server;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ServerThread {
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    private ServerThread() {
    }

    public static void execute(Runnable task) {
        TASKS.add(task);
    }

    public static void runTasks() {
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                BetterWithDiscordMod.LOGGER.error("Failed to handle a message from Discord", e);
            }
        }
    }
}
