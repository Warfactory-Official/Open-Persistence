package com.norwood.openpersistence.platform.services;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Platform abstraction over a "grave" / "corpse" mod (e.g. GraveStone or Corpse on Forge/NeoForge).
 *
 * <p>Open Persistence never produces a real player death — when a logout body
 * ({@link com.norwood.openpersistence.entity.PersistentPlayerEntity}) is killed, the items belong to
 * an <em>offline</em> player, so the player-death hooks those mods rely on never fire. This service is
 * the seam that lets us hand the recovered items to such a mod ourselves instead of scattering them on
 * the floor.</p>
 *
 * <p>On platforms with no grave mod (e.g. plain Fabric), {@link #isAvailable()} returns {@code false}
 * and {@link #deposit} returns {@code false}, so {@code PersistentPlayerManager} falls back to dropping
 * the items as loose entities — exactly the old behaviour.</p>
 */
public interface GraveHelper {

    /** Whether a supported grave/corpse mod is present and loaded on this platform. */
    default boolean isAvailable() {
        return false;
    }

    /**
     * Try to store the given items in a grave/corpse for the (offline) player at the body's position.
     *
     * @param level      the level the body died in
     * @param x          body position X
     * @param y          body position Y
     * @param z          body position Z
     * @param playerUUID the offline player the body belongs to
     * @param playerName the offline player's name (for grave display/ownership)
     * @param items      the stacks recovered from the body and the offline save (already copies)
     * @return {@code true} if a grave/corpse was created and now owns the items (the caller must NOT
     *         drop them); {@code false} if nothing handled it (the caller should scatter them).
     */
    default boolean deposit(ServerLevel level, double x, double y, double z,
                            UUID playerUUID, String playerName, List<ItemStack> items) {
        return false;
    }
}
