package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.sajmonoriginal.betterwithdiscord.server.DiscordClient;
import com.sajmonoriginal.betterwithdiscord.server.ServerThread;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, remap = false)
public class MinecraftServerMixin {
    @Inject(method = "doTick", at = @At("HEAD"))
    private void betterWithDiscord$runDiscordTasks(CallbackInfo ci) {
        ServerThread.runTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void betterWithDiscord$disconnect(CallbackInfo ci) {
        DiscordClient.stop();
    }
}
