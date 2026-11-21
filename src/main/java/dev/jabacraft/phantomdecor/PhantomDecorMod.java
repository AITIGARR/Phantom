package dev.jabacraft.phantomdecor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.math.Transformation;
import dev.jabacraft.phantomdecor.mixin.DisplayAccess;
import dev.jabacraft.phantomdecor.mixin.ItemDisplayAccess;
import dev.jabacraft.phantomdecor.mixin.TextDisplayAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side decoration mod. No client mod or resource pack is required.
 *
 * Signs and banners remain ordinary server-side blocks with their block entity data intact.
 * To vanilla clients, the mod periodically sends a virtual AIR block at that location and
 * shows a persistent vanilla display entity. The original can be restored at any time.
 */
public final class PhantomDecorMod implements ModInitializer {
    public static final String MOD_ID = "phantomdecor";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE = FabricLoader.getInstance().getConfigDir().resolve("phantomdecor-blocks.json");

    private final Map<String, DecoratedBlock> blocks = new HashMap<>();
    private final List<PendingPlacement> pending = new ArrayList<>();
    private int ticks = 0;

    public record DecoratedBlock(String dimension, long position, String kind) { }
    private record PendingPlacement(String dimension, long position, String kind, int expireTick) { }
    private record Database(List<DecoratedBlock> blocks) { }

    @Override
    public void onInitialize() {
        load();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof ItemFrame frame)) return InteractionResult.PASS;
            if (!player.getItemInHand(hand).is(Items.PHANTOM_MEMBRANE)) return InteractionResult.PASS;
            boolean makeInvisible = !frame.isInvisible();
            if (makeInvisible && !player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
            frame.setInvisible(makeInvisible);
            level.playSound(null, frame.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.3f);
            return InteractionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (player.isSpectator()) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            BlockPos clicked = hit.getBlockPos();
            BlockState state = level.getBlockState(clicked);

            // Applying a phantom membrane to an already placed sign/banner works
            // even when that block has dyed text, a custom name or patterned layers.
            if (hand == InteractionHand.MAIN_HAND && held.is(Items.PHANTOM_MEMBRANE)) {
                String kind = typeOf(state);
                if (kind != null && !blocks.containsKey(key(level, clicked))) {
                    decorate(level, clicked, kind);
                    if (!sp.getAbilities().instabuild) held.shrink(1);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }

            // Special crafting results are still vanilla items with custom_data.
            // They place normally, so the vanilla sign editor still works.
            if (isMarked(held)) {
                String kind = itemKind(held);
                if (kind != null) {
                    BlockPos target = state.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
                    pending.add(new PendingPlacement(level.dimension().location().toString(), target.asLong(), kind, ticks + 2400));
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("phantomdecor")
                .then(Commands.literal("restore")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        HitResult hit = player.pick(6.0d, 0.0f, false);
                        if (!(hit instanceof BlockHitResult bh)) {
                            ctx.getSource().sendFailure(Component.literal("Смотри на невидимую табличку или флаг."));
                            return 0;
                        }
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = bh.getBlockPos();
                        if (!restore(level, pos)) {
                            ctx.getSource().sendFailure(Component.literal("Здесь нет скрытого блока Phantom Decor."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Исходный блок восстановлен."), false);
                        return Command.SINGLE_SUCCESS;
                    }))));

        LOG.info("Phantom Decor loaded (server-only, Minecraft 1.21.11)");
    }

    private void tick(MinecraftServer server) {
        ticks++;
        for (Iterator<PendingPlacement> it = pending.iterator(); it.hasNext();) {
            PendingPlacement p = it.next();
            ServerLevel level = dimension(server, p.dimension());
            if (level == null || ticks >= p.expireTick()) { it.remove(); continue; }
            BlockPos pos = BlockPos.of(p.position());
            if (!level.hasChunkAt(pos)) continue;
            if (p.kind().equals("frame")) {
                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos).inflate(0.75));
                if (!frames.isEmpty()) {
                    frames.get(0).setInvisible(true);
                    it.remove();
                }
                continue;
            }
            String actual = typeOf(level.getBlockState(pos));
            if (!p.kind().equals(actual)) continue;
            if (actual.equals("sign")) {
                // Wait for the sign editor to save text; unlike replacing a sign in the
                // use callback, this preserves both sides and vanilla input behavior.
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SignBlockEntity sign && !hasText(sign) && ticks < p.expireTick() - 1200) continue;
            }
            decorate(level, pos, actual);
            it.remove();
        }

        // Frequent small block updates keep the fake AIR after chunk changes / rejoin.
        // Displays are real vanilla entities; refresh only if the chunk loaded them.
        if (ticks % 20 != 0) return;
        boolean changed = false;
        for (Iterator<Map.Entry<String, DecoratedBlock>> it = blocks.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, DecoratedBlock> mapEntry = it.next();
            DecoratedBlock info = mapEntry.getValue();
            ServerLevel level = dimension(server, info.dimension());
            if (level == null) continue;
            BlockPos pos = BlockPos.of(info.position());
            if (!level.hasChunkAt(pos)) continue;
            if (!info.kind().equals(typeOf(level.getBlockState(pos)))) {
                removeDisplays(level, pos);
                it.remove();
                changed = true;
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < 140 * 140) {
                    player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
                }
            }
            ensureDisplay(level, pos, info.kind());
        }
        if (changed) save();
    }

    private void decorate(ServerLevel level, BlockPos pos, String kind) {
        blocks.put(key(level, pos), new DecoratedBlock(level.dimension().location().toString(), pos.asLong(), kind));
        ensureDisplay(level, pos, kind);
        // Send the fake block immediately; automatic refresh also catches players joining later.
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.5f);
        save();
    }

    private boolean restore(ServerLevel level, BlockPos pos) {
        if (blocks.remove(key(level, pos)) == null) return false;
        removeDisplays(level, pos);
        BlockState restored = level.getBlockState(pos);
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundBlockUpdatePacket(pos, restored));
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getUpdatePacket() != null) player.connection.send(be.getUpdatePacket());
        }
        save();
        return true;
    }

    private void ensureDisplay(ServerLevel level, BlockPos pos, String kind) {
        String tag = entityTag(pos);
        List<Display> existing = level.getEntitiesOfClass(Display.class, new AABB(pos).inflate(1.6), e -> e.getTags().contains(tag));
        if (!existing.isEmpty()) return;

        if (kind.equals("sign") && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            float yaw = getFacing(level.getBlockState(pos));
            makeText(level, pos, sign.getFrontText(), yaw, false);
            makeText(level, pos, sign.getBackText(), yaw + 180f, true);
        } else if (kind.equals("banner")) {
            Display.ItemDisplay entity = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            ItemStack item = new ItemStack(level.getBlockState(pos).getBlock().asItem());
            if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
                item.set(DataComponents.BANNER_PATTERNS, banner.getPatterns());
            }
            ((ItemDisplayAccess) entity).phantomdecor$setStack(item);
            ((ItemDisplayAccess) entity).phantomdecor$setContext(ItemDisplayContext.FIXED);
            ((DisplayAccess) entity).phantomdecor$setTransformation(transform(1.65f));
            entity.setPos(pos.getX() + .5, pos.getY() + .58, pos.getZ() + .5);
            entity.setYRot(getFacing(level.getBlockState(pos)));
            entity.addTag(entityTag(pos));
            level.addFreshEntity(entity);
        }
    }

    private void makeText(ServerLevel level, BlockPos pos, SignText text, float yaw, boolean back) {
        if (!hasText(text)) return;
        Component combined = Component.empty();
        int color = text.getColor().getTextColor();
        for (int i = 0; i < 4; i++) {
            if (i > 0) combined = combined.copy().append(Component.literal("\n"));
            final int c = color;
            combined = combined.copy().append(text.getMessage(i, false).copy().withStyle(s -> s.withColor(c)));
        }
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        ((TextDisplayAccess) entity).phantomdecor$setText(combined);
        ((TextDisplayAccess) entity).phantomdecor$setBackground(0);
        ((DisplayAccess) entity).phantomdecor$setTransformation(transform(0.7f));
        // The two faces are separated slightly, so the rear text can be read as well.
        double radians = Math.toRadians(yaw);
        double signOffset = back ? .055 : .07;
        entity.setPos(pos.getX() + .5 - Math.sin(radians) * signOffset,
                      pos.getY() + .78,
                      pos.getZ() + .5 + Math.cos(radians) * signOffset);
        entity.setYRot(yaw);
        entity.addTag(entityTag(pos));
        level.addFreshEntity(entity);
    }

    private static Transformation transform(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private void removeDisplays(ServerLevel level, BlockPos pos) {
        String tag = entityTag(pos);
        for (Display display : level.getEntitiesOfClass(Display.class, new AABB(pos).inflate(1.6), e -> e.getTags().contains(tag))) {
            display.discard();
        }
    }

    private static String entityTag(BlockPos pos) { return "pd_" + pos.asLong(); }
    private static String key(ServerLevel level, BlockPos pos) { return level.dimension().location() + "|" + pos.asLong(); }
    private static boolean hasText(SignBlockEntity sign) { return hasText(sign.getFrontText()) || hasText(sign.getBackText()); }
    private static boolean hasText(SignText text) {
        for (int i = 0; i < 4; i++) if (!text.getMessage(i, false).getString().isBlank()) return true;
        return false;
    }

    private static float getFacing(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.SignBlock sign)
            return sign.getYRotationDegrees(state);
        if (state.hasProperty(WallBannerBlock.FACING)) return state.getValue(WallBannerBlock.FACING).toYRot();
        if (state.hasProperty(BannerBlock.ROTATION)) return state.getValue(BannerBlock.ROTATION) * 22.5f;
        return 0;
    }

    private static String typeOf(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.SignBlock) return "sign";
        if (state.getBlock() instanceof BannerBlock || state.getBlock() instanceof WallBannerBlock) return "banner";
        return null;
    }

    private static String itemKind(ItemStack stack) {
        if (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME)) return "frame";
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.endsWith("_sign") && !id.contains("hanging")) return "sign";
        if (id.endsWith("_banner")) return "banner";
        return null;
    }

    private static boolean isMarked(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().contains("phantomdecor");
    }

    private static ServerLevel dimension(MinecraftServer server, String name) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(name)) return level;
        }
        return null;
    }

    private void load() {
        if (!Files.isRegularFile(SAVE)) return;
        try {
            Database db = GSON.fromJson(Files.readString(SAVE, StandardCharsets.UTF_8), Database.class);
            if (db == null || db.blocks() == null) return;
            for (DecoratedBlock b : db.blocks()) blocks.put(b.dimension() + "|" + b.position(), b);
            LOG.info("Loaded {} invisible signs/banners", blocks.size());
        } catch (Exception ex) { LOG.error("Could not load {}", SAVE, ex); }
    }

    private void save() {
        try {
            Files.createDirectories(SAVE.getParent());
            Files.writeString(SAVE, GSON.toJson(new Database(List.copyOf(blocks.values()))), StandardCharsets.UTF_8);
        } catch (IOException ex) { LOG.error("Could not save {}", SAVE, ex); }
    }
}
