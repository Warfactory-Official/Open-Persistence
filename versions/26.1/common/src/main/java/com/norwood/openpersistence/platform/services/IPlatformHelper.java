package com.norwood.openpersistence.platform.services;

/**
 * Platform abstraction for things that differ between Fabric and Forge.
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface IPlatformHelper {

    /** A human readable name of the current loader ("Fabric" / "Forge"). */
    String getPlatformName();

    /** Whether a mod with the given id is loaded. */
    boolean isModLoaded(String modId);

    /** Whether we are in a development environment. */
    boolean isDevelopmentEnvironment();
}
