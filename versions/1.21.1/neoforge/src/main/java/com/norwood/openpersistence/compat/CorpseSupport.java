package com.norwood.openpersistence.compat;

import de.maxhenkel.corpse.corelib.death.Death;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Spawns a Corpse for the offline player. Mirrors Corpse's own {@code CorpseEntity.createFromDeath} on
 * a real death, but without needing a live {@code Player} (we have the offline player's data directly).
 * Uses Corpse's shaded corelib package. Only loaded when the {@code corpse} mod is present.
 *
 * <p>{@code CorpseEntity#setDeath} is annotated {@code @OnlyIn(Dist.CLIENT)} in current Corpse builds,
 * so NeoForge's RuntimeDistCleaner strips it out of the class entirely on a dedicated server. The
 * protected {@code death} field has to be assigned directly via reflection instead.</p>
 */
final class CorpseSupport {

    private static final Field DEATH_FIELD;

    static {
        Field field;
        try {
            field = CorpseEntity.class.getDeclaredField("death");
            field.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            field = null;
        }
        DEATH_FIELD = field;
    }

    private CorpseSupport() {
    }

    static boolean deposit(ServerLevel level, double x, double y, double z,
                           UUID playerUUID, String playerName, List<ItemStack> items) {
        if (DEATH_FIELD == null) {
            // Corpse's internal layout changed again; bail out to the normal item-scatter fallback
            // instead of throwing.
            return false;
        }

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
        try {
            DEATH_FIELD.set(corpse, death);
        } catch (ReflectiveOperationException e) {
            return false;
        }
        corpse.setCorpseUUID(death.getPlayerUUID());
        corpse.setCorpseName(death.getPlayerName());
        corpse.setCorpseModel(death.getModel());
        corpse.setPos(x, Math.max(y, level.getMinBuildHeight()), z);
        return level.addFreshEntity(corpse);
    }
}