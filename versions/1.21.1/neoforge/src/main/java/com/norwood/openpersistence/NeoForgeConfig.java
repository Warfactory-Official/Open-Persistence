package com.norwood.openpersistence;

import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge config spec backing the shared {@link OpenPersistenceConfig} static fields. */
public final class NeoForgeConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue PERSIST_CREATIVE_PLAYERS;
    private static final ModConfigSpec.BooleanValue OFFLINE_PLAYERS_SLEEP;
    private static final ModConfigSpec.BooleanValue DEBUG;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("persistent_players");
        PERSIST_CREATIVE_PLAYERS = builder
                .comment("If players in creative mode should leave a persistent body behind")
                .define("persist_creative_players", false);
        OFFLINE_PLAYERS_SLEEP = builder
                .comment("If offline persistent bodies should lie down (sleeping pose)")
                .define("offline_players_sleep", false);
        DEBUG = builder
                .comment("Verbose logging for debugging")
                .define("better_logging", false);
        builder.pop();
        SPEC = builder.build();
    }

    private NeoForgeConfig() {
    }

    public static void sync() {
        OpenPersistenceConfig.persistCreativePlayers = PERSIST_CREATIVE_PLAYERS.get();
        OpenPersistenceConfig.offlinePlayersSleep = OFFLINE_PLAYERS_SLEEP.get();
        OpenPersistenceConfig.debug = DEBUG.get();
    }
}
