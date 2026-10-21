package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.net.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PlayerList.class, remap = false)
public abstract class PlayerListMixin {
    @Shadow public List<PlayerServer> playerEntities;

    @Inject(method = "playerLoggedIn", at = @At("TAIL"))
    private void betterWithDiscord$announceJoin(PlayerServer player, CallbackInfo ci) {
        DiscordChatRelay.sendJoinLeaveMessage(player, true);
    }

    @Inject(method = "playerLoggedOut", at = @At("HEAD"))
    private void betterWithDiscord$announceLeave(PlayerServer player, CallbackInfo ci) {
        if (playerEntities.contains(player)) {
            DiscordChatRelay.sendJoinLeaveMessage(player, false);
        }
    }
}
