package com.norwood.openpersistence.compat;

import de.maxhenkel.corpse.corelib.death.Death;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Spawns a Corpse for the offline player. Mirrors Corpse's own {@code CorpseEntity.createFromDeath} on
 * a real death, but without needing a live {@code Player} (we have the offline player's data directly).
 * Uses Corpse's shaded corelib package. Only loaded when the {@code corpse} mod is present.
 */
final class CorpseSupport {

    private CorpseSupport() {
    }

    static boolean deposit(ServerLevel level, double x, double y, double z,
                           UUID playerUUID, String playerName, List<ItemStack> items) {
        GraveDeaths.Split split = GraveDeaths.split(items);
        Death death = new Death.Builder(playerUUID, UUID.randomUUID())
                .playerName(playerName == null ? "" : playerName)
                .mainInventory(split.main)
                .additionalItems(split.additional)
                .posX(x)
                .posY(y)
                .posZ(z)
                .dimension(GraveDeaths.dimension(level))
                .build();

        CorpseEntity corpse = new CorpseEntity(level);
        corpse.setDeath(death);
        corpse.setPlayerUuid(death.getPlayerUUID());
        corpse.setCorpseName(death.getPlayerName());
        corpse.setCorpseModel(death.getModel());
        corpse.setPos(x, Math.max(y, level.getMinY()), z);
        return level.addFreshEntity(corpse);
    }
}
