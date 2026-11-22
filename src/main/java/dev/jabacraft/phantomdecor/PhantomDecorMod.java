package dev.jabacraft.phantomdecor;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-only entry point. Vanilla entities and block entities own all persistent state. */
public final class PhantomDecorMod implements ModInitializer {
    public static final String ID = "phantomdecor";
    private static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Phantom Decor 1.21.11 loaded. Install its resource pack for invisible pale oak sign boards.");
    }

    public static boolean isPhantomFrame(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getString("phantomdecor").orElse("").equals("frame");
    }
}
