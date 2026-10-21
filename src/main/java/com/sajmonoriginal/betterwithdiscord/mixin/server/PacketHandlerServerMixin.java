package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.net.handler.PacketHandlerServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PacketHandlerServer.class, remap = false)
public class PacketHandlerServerMixin {
    @Shadow private PlayerServer playerEntity;

    @WrapOperation(method = "handleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/net/ChatEmotes;process(Ljava/lang/String;)Ljava/lang/String;"))
    private String betterWithDiscord$relayChat(String message, Operation<String> original) {
        return DiscordChatRelay.relayChat(playerEntity, original.call(message));
    }
}
