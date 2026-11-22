package dev.jabacraft.phantomdecor.mixin;

import dev.jabacraft.phantomdecor.PhantomDecorMod;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sets the vanilla frame's invisible flag before it is sent to any client. */
@Mixin(net.minecraft.world.item.HangingEntityItem.class)
public abstract class HangingEntityItemMixin {
    @Inject(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private void phantomdecor$beforeSpawn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir, @Local HangingEntity entity) {
        if (entity instanceof ItemFrame frame && PhantomDecorMod.isPhantomFrame(context.getItemInHand())) {
            frame.setInvisible(true);
        }
    }
}
