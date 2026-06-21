package com.norwood.openpersistence.entity;

import com.norwood.openpersistence.Openpersistence;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

/**
 * Holder for the persistent-player {@link EntityType}. Each loader registers the type with
 * its own registry mechanism and assigns it here so common code can reference it.
 */
public final class ModEntities {

    public static final String PERSISTENT_PLAYER_ID = "player";

    /** 26.x requires the registry key when building the EntityType, so it is shared here. */
    public static final ResourceKey<EntityType<?>> PERSISTENT_PLAYER_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Openpersistence.MOD_ID, PERSISTENT_PLAYER_ID));

    public static EntityType<PersistentPlayerEntity> PERSISTENT_PLAYER;

    private ModEntities() {
    }
}
