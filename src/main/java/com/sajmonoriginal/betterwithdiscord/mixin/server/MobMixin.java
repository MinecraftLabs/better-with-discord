package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Mob.class, remap = false)
public abstract class MobMixin {
    @WrapOperation(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/world/World;sendGlobalMessageTranslated(Lnet/minecraft/core/net/command/TextFormatting$Base;Ljava/lang/String;[Ljava/lang/String;)V"))
    private void betterWithDiscord$relayDeath(World world, TextFormatting.Base format, String key, String[] args, Operation<Void> original) {
        original.call(world, format, key, args);
        if ((Object) this instanceof Player) {
            DiscordChatRelay.sendDeathMessage(key, args);
        }
    }
}
