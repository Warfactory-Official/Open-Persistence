package com.norwood.openpersistence;

import com.mojang.authlib.GameProfile;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import com.norwood.openpersistence.platform.Services;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Server-side logic shared by all loaders: spawn a body on logout, restore the player on login,
 * keep the offline save in sync with the body, and drop the player's stuff when the body dies.
 */
public final class PersistentPlayerManager {

    /** Loaded bodies by owner; maintained by loader level join/leave events. */
    private static final ConcurrentHashMap<UUID, PersistentPlayerEntity> BODIES = new ConcurrentHashMap<>();
    /** Owners online; written on the main thread, read from body ticks (PlayerList is main-thread only). */
    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();

    private PersistentPlayerManager() {
    }

    public static void bodyLoaded(PersistentPlayerEntity body) {
        body.getPlayerUUID().ifPresent(uuid -> BODIES.put(uuid, body));
    }

    public static void bodyUnloaded(PersistentPlayerEntity body) {
        body.getPlayerUUID().ifPresent(uuid -> BODIES.remove(uuid, body));
    }

    /** Main thread inline; from a region worker, queued to the main thread. */
    private static void onMain(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayer()) {
            return;
        }
        ONLINE.add(player.getUUID());
        PersistentPlayerEntity body = BODIES.get(player.getUUID());
        if (body != null) {
            if (body.claim()) {
                restorePlayer(body, player);
            }
        } else if (OpenPersistenceConfig.debug) {
            Openpersistence.LOGGER.warn("No persistent body found for {}, vanilla spawn", player.getGameProfile().getName());
        }
    }

    /** Driven by the body's own server tick; owner-online check closes the rejoin-before-chunk-load window. */
    public static void onBodyTick(PersistentPlayerEntity body) {
        if (body.isClaimed()) {
            body.discard();
            return;
        }
        if (!(body.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || server.isSingleplayer()) {
            return;
        }
        UUID uuid = body.getPlayerUUID().orElse(null);
        if (uuid == null || !ONLINE.contains(uuid)) {
            return;
        }
        if (body.claim()) {
            PersistentPlayerEntity.Restore state = body.restoreState(level);
            onMain(server, () -> {
                ServerPlayer owner = server.getPlayerList().getPlayer(uuid);
                if (owner != null) {
                    restorePlayer(state, owner);
                }
            });
        }
        body.discard();
    }

    public static void onPlayerLogout(ServerPlayer player) {
        ONLINE.remove(player.getUUID());
        if (!canPersist(player)) {
            return;
        }
        PersistentPlayerEntity body = PersistentPlayerEntity.fromPlayer(player);
        player.stopRiding();
        player.level().addFreshEntity(body);
        syncBodyLocation(body, null);
        if (OpenPersistenceConfig.debug) {
            Openpersistence.LOGGER.info("Spawned persistent body for {}", player.getGameProfile().getName());
        }
    }

    public static boolean canPersist(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayer() || player.isSpectator()) {
            return false;
        }
        return !player.isCreative() || OpenPersistenceConfig.persistCreativePlayers;
    }

    private static void restorePlayer(PersistentPlayerEntity body, ServerPlayer player) {
        restorePlayer(body.restoreState((ServerLevel) body.level()), player);
    }

    private static void restorePlayer(PersistentPlayerEntity.Restore state, ServerPlayer player) {
        player.setHealth(state.health());
        player.setAirSupply(state.air());
        player.setRemainingFireTicks(state.fire());
        for (MobEffectInstance effect : state.effects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.teleportTo(state.level(), state.x(), state.y(), state.z(), state.yRot(), state.xRot());
    }

    public static void syncBodyLocation(PersistentPlayerEntity body, Consumer<ServerPlayer> additional) {
        if (!(body.level() instanceof ServerLevel level) || body.getPlayerUUID().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        updateOfflinePlayer(server, level, body.getPlayerUUID().get(), offline -> {
            offline.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            if (additional != null) {
                additional.accept(offline);
            }
        });
    }

    public static void onBodyDeath(PersistentPlayerEntity body) {
        if (!(body.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        onMain(serverLevel.getServer(), () -> dropContents(body, serverLevel));
    }

    /** Main thread: offline save IO, {@code ServerPlayer} construction (loads chunks), grave/item placement. */
    private static void dropContents(PersistentPlayerEntity body, ServerLevel serverLevel) {
        // Dupe guard: if the owner is already back online they still hold their inventory, so dropping
        // the body's saved copy would duplicate it. The body removes itself on rejoin (onBodyTick);
        // this covers the race where it is killed in the tick before that happens.
        UUID ownerUUID = body.getPlayerUUID().orElse(null);
        if (body.isClaimed() || ownerUUID != null && ONLINE.contains(ownerUUID)) {
            body.getExtraItems().clear();
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        boolean[] claimed = {false};
        syncBodyLocation(body, offline -> {
            offline.setHealth(0.0F);
            if (Services.GRAVE.claim(body, offline, body.profile())) {
                claimed[0] = true;
                return;
            }
            for (ItemStack stack : body.getExtraItems()) {
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
            Services.CURIOS.clear(offline);
            Inventory inventory = offline.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            if (OpenPersistenceConfig.debug) {
                Openpersistence.LOGGER.info("Persistent body for {} killed; {} stacks recovered", body.getPlayerName(), drops.size());
            }
        });

        body.getExtraItems().clear();
        if (claimed[0]) {
            body.discard();
            return;
        }

        UUID playerUUID = body.getPlayerUUID().orElse(null);
        boolean stored = playerUUID != null && Services.GRAVE.deposit(
                serverLevel, body.getX(), body.getY(), body.getZ(), playerUUID, body.getPlayerName(), drops);
        if (!stored) {
            for (ItemStack stack : drops) {
                body.spawnAtLocation(stack);
            }
        }
    }

    /**
     * Loads the offline player's {@code <uuid>.dat} into a throwaway {@link ServerPlayer}, lets the
     * consumer mutate it, then atomically writes it back. Uses only public {@link NbtIo} + world-path
     * APIs (mirroring vanilla {@code PlayerDataStorage}) so it works identically on every loader and
     * needs no access widening. NeoForge attachments (e.g. Curios) round-trip through the player tag.
     */
    public static void updateOfflinePlayer(MinecraftServer server, ServerLevel level, UUID uuid,
                                           Consumer<ServerPlayer> consumer) {
        if (server.isSingleplayer()) {
            return;
        }
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path dataFile = playerDataDir.resolve(uuid.toString() + ".dat");
        if (!Files.exists(dataFile)) {
            if (OpenPersistenceConfig.debug) {
                Openpersistence.LOGGER.warn("No save data for offline player {}", uuid);
            }
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            ServerPlayer offline = new ServerPlayer(server, level, new GameProfile(uuid, ""),
                    ClientInformation.createDefault());
            offline.load(tag);
            consumer.accept(offline);

            CompoundTag out = offline.saveWithoutId(new CompoundTag());
            NbtUtils.addCurrentDataVersion(out);
            Path tmp = Files.createTempFile(playerDataDir, uuid + "-", ".dat");
            NbtIo.writeCompressed(out, tmp);
            Path backup = playerDataDir.resolve(uuid + ".dat_old");
            Util.safeReplaceFile(dataFile, tmp, backup);
        } catch (Exception e) {
            Openpersistence.LOGGER.error("Failed to update offline player data for {}", uuid, e);
        }
    }
}
