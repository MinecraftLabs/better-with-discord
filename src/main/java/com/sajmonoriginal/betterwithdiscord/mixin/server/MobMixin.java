package com.sajmonoriginal.betterwithdiscord.mixin.server;

import com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mob.class, remap = false)
public abstract class MobMixin {
    @Shadow public abstract String getDeathMessageKey(Entity entity);

    @Inject(
            method = "onDeath",
            at = @At("RETURN")
    )
    void processDeathMessage(Entity entity, CallbackInfo ci) {
        if ((Mob)((Object)this) instanceof Player) {
            String message = getDeathMessageKey(entity).replaceAll("\u00a7.", "");
            DiscordChatRelay.sendDeathMessage(message);
        }
    }
}
