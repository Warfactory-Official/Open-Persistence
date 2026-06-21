package com.norwood.openpersistence.entity;

import net.minecraft.world.entity.EntityType;

/**
 * Holder for the persistent-player {@link EntityType}. Each loader registers the type with
 * its own registry mechanism and assigns it here so common code can reference it.
 */
public final class ModEntities {

    public static final String PERSISTENT_PLAYER_ID = "player";

    public static EntityType<PersistentPlayerEntity> PERSISTENT_PLAYER;

    private ModEntities() {
    }
}
