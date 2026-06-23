package com.norwood.openpersistence.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompatManager {

    public static final Map<String, Boolean> MOD_CACHE = new ConcurrentHashMap<>();
    public static final String ID_MW = "modularwarfare";
    public static final String ID_GRAVESTONE = "gravestone";
    public static final String ID_CORPSE = "corpse";

   public static boolean isLoaded(String modId) {
       return MOD_CACHE.computeIfAbsent(modId, Loader::isModLoaded);
   }

    /**
     * Seam for grave/corpse mods
     * <p><strong>Status: seam only — not yet wired.</strong> Returns {@code false}, so the caller
     * scatters the items as before. Complete it by guarding on {@link #isLoaded} for
     * {@link #ID_GRAVESTONE}/{@link #ID_CORPSE} and calling the loaded mod's API.</p>
     *
     * @return {@code true} if a grave/corpse took the items (caller must not also drop them).
     */
    public static boolean depositGrave(World world, double x, double y, double z,
                                       UUID playerUUID, String playerName, List<ItemStack> items) {
        return false;
    }


}
