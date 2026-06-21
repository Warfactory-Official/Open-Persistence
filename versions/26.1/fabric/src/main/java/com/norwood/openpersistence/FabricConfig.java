package com.norwood.openpersistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal JSON config for Fabric, backing the shared {@link OpenPersistenceConfig} fields. */
public final class FabricConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FabricConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Openpersistence.MOD_ID + ".json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                OpenPersistenceConfig.persistCreativePlayers =
                        getBool(json, "persist_creative_players", OpenPersistenceConfig.persistCreativePlayers);
                OpenPersistenceConfig.offlinePlayersSleep =
                        getBool(json, "offline_players_sleep", OpenPersistenceConfig.offlinePlayersSleep);
                OpenPersistenceConfig.debug =
                        getBool(json, "better_logging", OpenPersistenceConfig.debug);
            } catch (Exception e) {
                Openpersistence.LOGGER.warn("Failed to read config, using defaults", e);
            }
        }
        save(path);
    }

    private static boolean getBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static void save(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("persist_creative_players", OpenPersistenceConfig.persistCreativePlayers);
        json.addProperty("offline_players_sleep", OpenPersistenceConfig.offlinePlayersSleep);
        json.addProperty("better_logging", OpenPersistenceConfig.debug);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            Openpersistence.LOGGER.warn("Failed to write config", e);
        }
    }
}
