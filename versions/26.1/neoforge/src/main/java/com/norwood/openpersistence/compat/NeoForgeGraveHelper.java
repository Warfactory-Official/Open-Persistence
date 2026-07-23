package com.norwood.openpersistence.compat;

import com.norwood.openpersistence.Openpersistence;
import com.norwood.openpersistence.platform.services.GraveHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * NeoForge grave/corpse integration for GraveStone and Corpse (both by henkelmax).
 *
 * <p>Those mods only capture a <em>real</em> player death; a logout body is a custom entity, so we
 * deposit the recovered items into their store ourselves on the offline player's behalf. The actual
 * mod calls live in {@link CorpseSupport}/{@link GravestoneSupport}, which are only referenced (and
 * therefore only class-loaded) when the corresponding mod is present — so a server that has neither
 * mod, or only one of them, never hits a missing-class error.</p>
 */
public class NeoForgeGraveHelper implements GraveHelper {

    @Override
    public boolean isAvailable() {
        ModList list = ModList.get();
        return list != null && (list.isLoaded("corpse") || list.isLoaded("gravestone"));
    }

    @Override
    public boolean deposit(ServerLevel level, double x, double y, double z,
                           UUID playerUUID, String playerName, List<ItemStack> items) {
        ModList list = ModList.get();
        if (list == null) {
            return false;
        }
        // Prefer Corpse, then GraveStone, if both happen to be installed.
        if (list.isLoaded("corpse")) {
            Boolean stored = guardedDeposit("corpse",
                    () -> CorpseSupport.deposit(level, x, y, z, playerUUID, playerName, items));
            if (stored != null) {
                return stored;
            }
        }
        if (list.isLoaded("gravestone")) {
            Boolean stored = guardedDeposit("gravestone",
                    () -> GravestoneSupport.deposit(level, x, y, z, playerUUID, playerName, items));
            if (stored != null) {
                return stored;
            }
        }
        return false;
    }


    private static Boolean guardedDeposit(String mod, BooleanSupplier deposit) {
        try {
            return deposit.getAsBoolean();
        } catch (LinkageError | RuntimeException e) {
            Openpersistence.LOGGER.error(
                    "Could not store items in '{}' (incompatible mod version?); dropping them instead", mod, e);
            return null;
        }
    }
}
