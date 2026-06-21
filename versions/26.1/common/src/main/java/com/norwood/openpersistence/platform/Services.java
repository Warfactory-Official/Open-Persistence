package com.norwood.openpersistence.platform;

import com.norwood.openpersistence.Openpersistence;
import com.norwood.openpersistence.platform.services.CuriosHelper;
import com.norwood.openpersistence.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Loads the loader-specific service implementations via {@link ServiceLoader}.
 * This is the standard MultiLoader-template pattern that Prism's common source layout
 * relies on (common code is compiled directly into each loader, no @ExpectPlatform).
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final CuriosHelper CURIOS = load(CuriosHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Openpersistence.LOGGER.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
