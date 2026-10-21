package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.lang.I18n;
import net.minecraft.core.net.command.TextFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Mob.class, remap = false)
public abstract class MobMixin {
    @ModifyExpressionValue(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/entity/Mob;getDeathMessageKey(Lnet/minecraft/core/entity/Entity;)Ljava/lang/String;"))
    private String betterWithDiscord$relayDeath(String key, @Local(argsOnly = true) Entity killer) {
        if ((Object) this instanceof Player player) {
            String message = killer == null
                    ? I18n.getInstance().translateKeyAndFormat(key, Entity.getNameFromEntity(player, true))
                    : I18n.getInstance().translateKeyAndFormat(key, Entity.getNameFromEntity(player, true), Entity.getNameFromEntity(killer, true));
            DiscordChatRelay.sendDeathMessage(TextFormatting.removeAllFormatting(message));
        }
        return key;
    }
}
