package com.norwood.openpersistence.platform;

import com.norwood.openpersistence.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        // 26.x made FMLLoader instance-based; getCurrent() returns the active loader.
        return !FMLLoader.getCurrent().isProduction();
    }
}
