package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = World.class, remap = false)
public class WorldMixin {
    @Inject(method = "wakeUpAllPlayers", at = @At("RETURN"))
    private void betterWithDiscord$announceSkippedNight(CallbackInfo ci) {
        DiscordChatRelay.sendServerSleepMessage();
    }
}
