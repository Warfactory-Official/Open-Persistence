package com.norwood.openpersistence;

import com.norwood.openpersistence.entity.ModEntities;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class OpenpersistenceFabric implements ModInitializer {

    public static final EntityType<PersistentPlayerEntity> PERSISTENT_PLAYER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Openpersistence.MOD_ID, ModEntities.PERSISTENT_PLAYER_ID),
            EntityType.Builder.<PersistentPlayerEntity>of(PersistentPlayerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(Openpersistence.MOD_ID + ":" + ModEntities.PERSISTENT_PLAYER_ID));

    @Override
    public void onInitialize() {
        Openpersistence.init();
        ModEntities.PERSISTENT_PLAYER = PERSISTENT_PLAYER;

        FabricConfig.load();
        FabricDefaultAttributeRegistry.register(PERSISTENT_PLAYER, PersistentPlayerEntity.createAttributes());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PersistentPlayerManager.onPlayerLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PersistentPlayerManager.onPlayerLogout(handler.player));
    }
}
