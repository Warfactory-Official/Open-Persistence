package com.norwood.openpersistence.events;

import com.mojang.authlib.GameProfile;
import com.norwood.openpersistence.Config;
import com.norwood.openpersistence.Logger;
import com.norwood.openpersistence.entity.EntityPersistentPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (Objects.requireNonNull(player.getServerWorld().getMinecraftServer()).isSinglePlayer()) {
            return;
        }

        boolean foundPlayer = false;
        for (WorldServer world : player.getServerWorld().getMinecraftServer().worlds) {
            Optional<EntityPersistentPlayer> persistentPlayer = findPersistentPlayer(world, player.getUniqueID());

            if (persistentPlayer.isPresent()) {
                EntityPersistentPlayer p = persistentPlayer.get();
                p.toPlayer(player);
                p.setDead();
                foundPlayer = true;
                break;
            }
        }
        if (!foundPlayer) {
            Logger.error("Failed to find player, falling back to vanilla spawning");
        }

    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            if (Config.DEBUG) {
                Logger.error("Entity is not EntityPlayerMP.");
            }
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!canPersist(player)) {
            if (Config.DEBUG) {
                Logger.error("Player is not Persistent player");
            }
            return;
        }
        EntityPersistentPlayer persistentPlayer = EntityPersistentPlayer.fromPlayer(player);
        player.dismountRidingEntity();
        player.world.spawnEntity(persistentPlayer);
        if (Config.DEBUG){
            WorldServer world = player.getServerWorld().getMinecraftServer().getWorld(0);
            Optional<EntityPersistentPlayer> persistentPlayerCreated = findPersistentPlayer(world, player.getUniqueID());
            if (persistentPlayerCreated.isPresent()){
                Logger.info("Overworld: Persistent player successfully created!");
            }else{
                Logger.error("Overworld: Failed to create persistent player!");
            }
        }
    }
    public boolean canPersist(EntityPlayerMP player) {
        if (Objects.requireNonNull(player.getServerWorld().getMinecraftServer()).isSinglePlayer() || player.isSpectator() || (player.isCreative() && !Config.persistCreativePlayers)) {
            return false;
        }
        return true;
    }

    public void updatePersistentPlayerLocation(EntityPersistentPlayer persistentPlayer, Consumer<EntityPlayerMP> additionalUpdateConsumer) {
        if (!(persistentPlayer.world instanceof WorldServer)) {
            return;
        }
        if (!persistentPlayer.getPlayerUUID().isPresent()) {
            return;
        }

        WorldServer world = (WorldServer) persistentPlayer.world;
        updateOfflinePlayer(world, persistentPlayer.getPlayerUUID().get(), serverPlayerEntity -> {
            Logger.info("Updating offline player location " + persistentPlayer.getPlayerName() + " x: " + persistentPlayer.posX + " y: " + persistentPlayer.posY + " z: " + persistentPlayer.posZ);
            serverPlayerEntity.setPositionAndRotation(persistentPlayer.posX, persistentPlayer.posY, persistentPlayer.posZ, persistentPlayer.rotationYaw, persistentPlayer.rotationPitch);
            serverPlayerEntity.dimension = world.provider.getDimension();
            if (additionalUpdateConsumer != null) {
                additionalUpdateConsumer.accept(serverPlayerEntity);
            }
        });
    }

    public Optional<EntityPersistentPlayer> findPersistentPlayer(WorldServer world, UUID playerUUID) {
        return world.getEntities(EntityPersistentPlayer.class, p -> p.getPlayerUUID().isPresent() && p.getPlayerUUID().get().equals(playerUUID)).stream().findAny();
    }

    public static void updateOfflinePlayer(WorldServer world, UUID playerUUID, Consumer<EntityPlayerMP> playerConsumer) {
        if (world.getMinecraftServer().isSinglePlayer()) {
            return;
        }

        ISaveHandler playerData = world.getSaveHandler();
        EntityPlayerMP serverPlayerEntity = new EntityPlayerMP(world.getMinecraftServer(), world, new GameProfile(playerUUID, ""), new PlayerInteractionManager(world));

        if (playerData instanceof SaveHandler) {
            SaveHandler saveHandler = (SaveHandler) playerData;
            saveHandler.readPlayerData(serverPlayerEntity);
            playerConsumer.accept(serverPlayerEntity);
            saveHandler.writePlayerData(serverPlayerEntity);
        } else {
            Logger.error("Failed to write player data (Wrong SaveHandler)");
        }
    }

}
