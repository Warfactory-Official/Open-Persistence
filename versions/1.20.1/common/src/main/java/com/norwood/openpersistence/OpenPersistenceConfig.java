package com.norwood.openpersistence;

/**
 * Shared config state. Loader modules populate these fields from their own config systems
 * (ForgeConfigSpec on Forge, a small JSON file on Fabric) during mod init.
 */
public final class OpenPersistenceConfig {

    /** If players in creative mode should leave a persistent body behind. Off by default. */
    public static boolean persistCreativePlayers = false;

    /** If offline persistent bodies should lie down (sleeping pose, smaller hitbox). */
    public static boolean offlinePlayersSleep = false;

    /** Verbose logging for debugging. */
    public static boolean debug = false;

    private OpenPersistenceConfig() {
    }
}
