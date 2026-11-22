package dev.jabacraft.phantomdecor.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the invisible variant when a frame is broken in survival. */
@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {
    @Inject(method = "getFrameItemStack", at = @At("RETURN"), cancellable = true)
    private void phantomdecor$drop(CallbackInfoReturnable<ItemStack> cir) {
        ItemFrame frame = (ItemFrame) (Object) this;
        if (!frame.isInvisible()) return;
        ItemStack stack = cir.getReturnValue().copy();
        CompoundTag tag = new CompoundTag();
        tag.putString("phantomdecor", "frame");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        boolean glow = frame instanceof GlowItemFrame;
        stack.set(DataComponents.ITEM_NAME, Component.literal(glow ? "Невидимая светящаяся рамка" : "Невидимая рамка"));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("phantomdecor", glow ? "invisible_glow_frame" : "invisible_frame"));
        cir.setReturnValue(stack);
    }
}
