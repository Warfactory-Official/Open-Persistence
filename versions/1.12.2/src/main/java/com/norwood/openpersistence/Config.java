package com.norwood.openpersistence;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean persistCreativePlayers = false;
    public static boolean offlinePlayersSleep = false;
    public static boolean DEBUG = true;

    public static void init(Configuration config) {
        config.load();
        persistCreativePlayers = config.getBoolean("persist_creative_players", "persistent_players", false, "If players in creative mode should persist");
        offlinePlayersSleep = config.getBoolean("offline_players_sleep", "persistent_players", false, "If players that are offline should be sleeping");
        DEBUG = config.getBoolean("better_logging", "persistent_players", false, "For Better logging in case errors start happening");
        config.save();
    }
}
