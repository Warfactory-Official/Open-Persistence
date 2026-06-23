package com.norwood.openpersistence.compat;

import com.norwood.openpersistence.platform.services.GraveHelper;

/**
 * Fabric has no supported grave/corpse integration: the mods we target — GraveStone and Corpse, both
 * by henkelmax — ship for Forge/NeoForge only. Every method falls back to the interface's no-op
 * defaults, so on Fabric a killed body simply scatters its items on the ground, as it always has.
 *
 * <p>If a Fabric grave mod with a usable API is ever targeted, implement {@code deposit} here.</p>
 */
public class FabricGraveHelper implements GraveHelper {
}
