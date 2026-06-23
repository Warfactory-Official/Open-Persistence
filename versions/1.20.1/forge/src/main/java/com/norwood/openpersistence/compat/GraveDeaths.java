package com.norwood.openpersistence.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared helpers for turning the flat list of stacks recovered from a persistent body into the
 * inventory lists a corelib {@code Death} expects.
 *
 * <p>Corpse and GraveStone each shade their <em>own</em> relocated copy of henkelmax's corelib
 * ({@code de.maxhenkel.corpse.corelib...} vs {@code de.maxhenkel.gravestone.corelib...}), so the
 * {@code Death} object itself must be built in each per-mod support class against that mod's package.
 * Only the loader-agnostic slot-splitting lives here.</p>
 *
 * <p>Items fill the 36-slot main inventory first and overflow into the unbounded "additional items"
 * store, so an arbitrary number of stacks (e.g. from accessory mods) is preserved. Equipment is
 * intentionally left unset — it only affects how the grave/corpse renders worn armor, and skipping it
 * sidesteps a builder-overload type change between corelib versions; every item is still recoverable
 * from the inventory.</p>
 */
final class GraveDeaths {

    static final int MAIN_SIZE = 36;

    private GraveDeaths() {
    }

    static final class Split {
        final NonNullList<ItemStack> main;
        final NonNullList<ItemStack> additional;

        Split(NonNullList<ItemStack> main, NonNullList<ItemStack> additional) {
            this.main = main;
            this.additional = additional;
        }
    }

    static Split split(List<ItemStack> items) {
        NonNullList<ItemStack> main = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
        NonNullList<ItemStack> additional = NonNullList.create();
        int idx = 0;
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (idx < MAIN_SIZE) {
                main.set(idx, stack);
            } else {
                additional.add(stack);
            }
            idx++;
        }
        return new Split(main, additional);
    }

    static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }
}
