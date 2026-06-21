package com.norwood.openpersistence.compat;

import net.minecraftforge.fml.common.Loader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompatManager {

    public static final Map<String, Boolean> MOD_CACHE = new ConcurrentHashMap<>();
    public static final String ID_MW = "modularwarfare";

   public static boolean isLoaded(String modId) {
       return MOD_CACHE.computeIfAbsent(modId, Loader::isModLoaded);
   }


}
