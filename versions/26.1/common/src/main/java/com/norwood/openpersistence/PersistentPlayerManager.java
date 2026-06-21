package com.norwood.openpersistence;

import com.mojang.authlib.GameProfile;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import com.norwood.openpersistence.platform.Services;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side logic shared by all loaders: spawn a body on logout, restore the player on login,
 * keep the offline save in sync with the body, and drop the player's stuff when the body dies.
 */
public final class PersistentPlayerManager {

    private PersistentPlayerManager() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || server.isSingleplayer()) {
            return;
        }
        Optional<PersistentPlayerEntity> body = findBody(server, player.getUUID());
        if (body.isPresent()) {
            restorePlayer(body.get(), player);
            body.get().discard();
        } else if (OpenPersistenceConfig.debug) {
            Openpersistence.LOGGER.warn("No persistent body found for {}, vanilla spawn", player.getGameProfile().name());
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (!canPersist(player)) {
            return;
        }
        PersistentPlayerEntity body = PersistentPlayerEntity.fromPlayer(player);
        player.stopRiding();
        player.level().addFreshEntity(body);
        syncBodyLocation(body, null);
        if (OpenPersistenceConfig.debug) {
            Openpersistence.LOGGER.info("Spawned persistent body for {}", player.getGameProfile().name());
        }
    }

    public static boolean canPersist(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || server.isSingleplayer() || player.isSpectator()) {
            return false;
        }
        return !player.isCreative() || OpenPersistenceConfig.persistCreativePlayers;
    }

    private static void restorePlayer(PersistentPlayerEntity body, ServerPlayer player) {
        player.setHealth(body.getHealth());
        player.setAirSupply(body.getAirSupply());
        player.setRemainingFireTicks(body.getRemainingFireTicks());
        body.getActiveEffects().forEach(effect ->
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect)));

        ServerLevel level = (ServerLevel) body.level();
        player.teleportTo(level, body.getX(), body.getY(), body.getZ(),
                Set.<Relative>of(), body.getYRot(), body.getXRot(), false);
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

    public static void syncBodyLocation(PersistentPlayerEntity body, Consumer<ServerPlayer> additional) {
        if (!(body.level() instanceof ServerLevel level) || body.getPlayerUUID().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        updateOfflinePlayer(server, level, body.getPlayerUUID().get(), offline -> {
            offline.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            if (additional != null) {
                additional.accept(offline);
            }
        });
    }

    public static void onBodyDeath(PersistentPlayerEntity body) {
        if (!(body.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ItemStack stack : body.getExtraItems()) {
            if (!stack.isEmpty()) {
                body.spawnAtLocation(serverLevel, stack.copy());
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
                    body.spawnAtLocation(serverLevel, stack.copy());
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            if (OpenPersistenceConfig.debug) {
                Openpersistence.LOGGER.info("Persistent body for {} killed; inventory dropped", body.getPlayerName());
            }
        });
    }

    /**
     * Loads the offline player's {@code <uuid>.dat} into a throwaway {@link ServerPlayer}, lets the
     * consumer mutate it, then atomically writes it back. Uses only public {@link NbtIo} +
     * {@link TagValueInput}/{@link TagValueOutput} APIs (mirroring vanilla {@code PlayerDataStorage})
     * so it works identically on every loader and needs no access widening.
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
            ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag);
            offline.load(in);
            consumer.accept(offline);

            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            offline.saveWithoutId(out);
            CompoundTag result = out.buildResult();
            NbtUtils.addCurrentDataVersion(result);

            Path tmp = Files.createTempFile(playerDataDir, uuid + "-", ".dat");
            NbtIo.writeCompressed(result, tmp);
            Path backup = playerDataDir.resolve(uuid + ".dat_old");
            Util.safeReplaceFile(dataFile, tmp, backup);
        } catch (Exception e) {
            Openpersistence.LOGGER.error("Failed to update offline player data for {}", uuid, e);
        }
    }
}
