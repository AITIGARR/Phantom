package dev.jabacraft.phantomdecor.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("setText") void phantomdecor$setText(Component text);
    @Invoker("setBackgroundColor") void phantomdecor$setBackground(int background);
}
