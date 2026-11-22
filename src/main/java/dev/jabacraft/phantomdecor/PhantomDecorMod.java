package dev.jabacraft.phantomdecor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Phantom Decor (server only).
 *
 * Crafted signs and banners place as REAL Minecraft blocks. There is no client
 * AIR spoofing, no text_display and no item_display. Sign text therefore uses
 * the native sign editor and native renderer. The companion resource pack
 * hides the texture of the reserved pale oak sign variant.
 *
 * Custom block IDs are intentionally not registered: vanilla clients cannot
 * understand new block IDs without a client-side mod.
 */
public final class PhantomDecorMod implements ModInitializer {
    public static final String MOD_ID = "phantomdecor";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final List<FramePlacement> PENDING_FRAMES = new ArrayList<>();

    private record FramePlacement(String dimension, BlockPos pos, int remainingTicks) { }

    @Override
    public void onInitialize() {
        // Existing frames can still be turned invisible with a phantom membrane.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(world instanceof ServerLevel level)
                    || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()
                    || !(entity instanceof ItemFrame frame)) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.PHANTOM_MEMBRANE)) return InteractionResult.PASS;
            boolean invisible = !frame.isInvisible();
            frame.setInvisible(invisible);
            if (invisible && !serverPlayer.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, frame.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.6f, 1.4f);
            return InteractionResult.SUCCESS;
        });

        // A crafted frame is still the vanilla frame entity; make it invisible
        // immediately after vanilla has spawned it. Signs and banners need NO
        // placement hooks, conversion, display entities or fake block packets.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(world instanceof ServerLevel level)
                    || player.isSpectator()) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(Items.ITEM_FRAME) && !held.is(Items.GLOW_ITEM_FRAME))
                return InteractionResult.PASS;
            CustomData data = held.get(DataComponents.CUSTOM_DATA);
            if (data == null || !data.copyTag().contains("phantomdecor")) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
            PENDING_FRAMES.add(new FramePlacement(level.dimension().identifier().toString(), pos, 40));
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<FramePlacement> next = new ArrayList<>();
            for (Iterator<FramePlacement> iter = PENDING_FRAMES.iterator(); iter.hasNext();) {
                FramePlacement p = iter.next();
                if (p.remainingTicks() <= 0) { iter.remove(); continue; }
                ServerLevel level = null;
                for (ServerLevel candidate : server.getAllLevels()) {
                    if (candidate.dimension().identifier().toString().equals(p.dimension())) {
                        level = candidate;
                        break;
                    }
                }
                if (level == null || !level.hasChunkAt(p.pos())) {
                    iter.remove();
                    continue;
                }
                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                    new AABB(p.pos()).inflate(0.8));
                if (!frames.isEmpty()) {
                    // Placement callback is invoked before the frame is spawned.
                    frames.get(0).setInvisible(true);
                    iter.remove();
                } else {
                    iter.remove();
                    next.add(new FramePlacement(p.dimension(), p.pos(), p.remainingTicks() - 1));
                }
            }
            PENDING_FRAMES.addAll(next);
        });

        LOGGER.info("Phantom Decor native-block edition loaded (Fabric 1.21.11, server-only)");
    }
}
