package com.norwood.openpersistence;

import com.mojang.authlib.GameProfile;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import com.norwood.openpersistence.platform.Services;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side logic shared by all loaders: spawn a body on logout, restore the player on login,
 * keep the offline save in sync with the body, and drop the player's stuff when the body dies.
 *
 * <p>Loader modules call {@link #onPlayerLogout}/{@link #onPlayerLogin} from their own connection
 * events; the entity calls {@link #onBodyDeath}/{@link #syncBodyLocation} from its own hooks.</p>
 */
public final class PersistentPlayerManager {

    private PersistentPlayerManager() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayer()) {
            return;
        }
        Optional<PersistentPlayerEntity> body = findBody(server, player.getUUID());
        if (body.isPresent()) {
            restorePlayer(body.get(), player);
            body.get().discard();
        } else if (OpenPersistenceConfig.debug) {
            Openpersistence.LOGGER.warn("No persistent body found for {}, vanilla spawn", player.getGameProfile().getName());
        }
    }

    /**
     * Driven by the body's own server tick. The login event removes the body when it is already
     * loaded, but if the owner rejoined before their chunk (and the body in it) finished loading,
     * that event ran too early and missed it — leaving a body that can be killed while the owner is
     * back online, duplicating their items. Removing the body as soon as it ticks with its owner
     * online closes that window on every loader.
     */
    public static void onBodyTick(PersistentPlayerEntity body) {
        if (!(body.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || server.isSingleplayer()) {
            return;
        }
        body.getPlayerUUID().ifPresent(uuid -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(uuid);
            if (owner != null) {
                restorePlayer(body, owner);
                body.discard();
            }
        });
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (!canPersist(player)) {
            return;
        }
        PersistentPlayerEntity body = PersistentPlayerEntity.fromPlayer(player);
        player.stopRiding();
        player.level().addFreshEntity(body);
        // The body never moves, so sync the offline save to its location exactly once now.
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

    /** Hands the body's stored state (health, air, effects, position) back to the player. */
    private static void restorePlayer(PersistentPlayerEntity body, ServerPlayer player) {
        player.setHealth(body.getHealth());
        player.setAirSupply(body.getAirSupply());
        player.setRemainingFireTicks(body.getRemainingFireTicks());
        body.getActiveEffects().forEach(effect ->
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect)));

        ServerLevel level = (ServerLevel) body.level();
        player.teleportTo(level, body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
    }

    public static Optional<PersistentPlayerEntity> findBody(MinecraftServer server, UUID playerUUID) {
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof PersistentPlayerEntity body
                        && body.getPlayerUUID().map(playerUUID::equals).orElse(false)) {
                    return Optional.of(body);
                }
            }
        }
        return Optional.empty();
    }

    /** Keeps the offline player's saved position in sync with the body (called on save & death). */
    public static void syncBodyLocation(PersistentPlayerEntity body, Consumer<ServerPlayer> additional) {
        if (!(body.level() instanceof ServerLevel level) || body.getPlayerUUID().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        updateOfflinePlayer(server, level, body.getPlayerUUID().get(), offline -> {
            // The throwaway player is constructed in the body's level, so writing its position
            // also pins the offline save to the right dimension.
            offline.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            if (additional != null) {
                additional.accept(offline);
            }
        });
    }

    /** Called by the entity when its body is killed: drop the player's gear and zero their save. */
    public static void onBodyDeath(PersistentPlayerEntity body) {
        if (!(body.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Dupe guard: if the owner is already back online they still hold their inventory, so dropping
        // the body's saved copy would duplicate it. The body removes itself on rejoin (onBodyTick);
        // this covers the race where it is killed in the tick before that happens.
        UUID ownerUUID = body.getPlayerUUID().orElse(null);
        if (ownerUUID != null && serverLevel.getServer().getPlayerList().getPlayer(ownerUUID) != null) {
            body.getExtraItems().clear();
            return;
        }
        // Gather everything the body should drop into one list: the curio snapshot taken at logout,
        // plus the offline player's saved inventory (which we also empty + write back so the loss sticks).
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack stack : body.getExtraItems()) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        body.getExtraItems().clear();

        syncBodyLocation(body, offline -> {
            offline.setHealth(0.0F);
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
     * needs no access widening. Forge capabilities (e.g. Curios) round-trip through the player tag.
     */
    public static void updateOfflinePlayer(MinecraftServer server, ServerLevel level, UUID uuid,
                                           Consumer<ServerPlayer> consumer) {
        if (server.isSingleplayer()) {
            return;
        }
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        File dataFile = playerDataDir.resolve(uuid.toString() + ".dat").toFile();
        if (!dataFile.exists()) {
            if (OpenPersistenceConfig.debug) {
                Openpersistence.LOGGER.warn("No save data for offline player {}", uuid);
            }
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(dataFile);
            ServerPlayer offline = new ServerPlayer(server, level, new GameProfile(uuid, ""));
            offline.load(tag);
            consumer.accept(offline);

            CompoundTag out = offline.saveWithoutId(new CompoundTag());
            NbtUtils.addCurrentDataVersion(out);
            File tmp = File.createTempFile(uuid + "-", ".dat", playerDataDir.toFile());
            NbtIo.writeCompressed(out, tmp);
            File backup = playerDataDir.resolve(uuid + ".dat_old").toFile();
            Util.safeReplaceFile(dataFile, tmp, backup);
        } catch (Exception e) {
            Openpersistence.LOGGER.error("Failed to update offline player data for {}", uuid, e);
        }
    }
}
