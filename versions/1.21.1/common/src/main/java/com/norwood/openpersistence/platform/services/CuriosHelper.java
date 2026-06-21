package com.norwood.openpersistence.platform.services;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Platform abstraction over an "extra equipment" system (Curios on Forge/NeoForge).
 *
 * <p>The persistent player stores a flat snapshot of the player's curio stacks at logout so
 * they can be dropped on death. On loaders without Curios (e.g. plain Fabric) every method is
 * a no-op and {@link #isAvailable()} returns {@code false}, so the rest of the mod degrades
 * gracefully.</p>
 */
public interface CuriosHelper {

    /** Whether an extra-slot system (Curios) is present on this platform and loaded. */
    default boolean isAvailable() {
        return false;
    }

    /** Snapshot (copies of) the curio stacks currently equipped by the living entity. */
    default List<ItemStack> snapshot(LivingEntity entity) {
        return Collections.emptyList();
    }

    /** Empty every curio slot of the entity (used on the offline player when the body dies). */
    default void clear(LivingEntity entity) {
    }
}
