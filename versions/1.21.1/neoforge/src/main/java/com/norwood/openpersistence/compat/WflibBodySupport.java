package com.norwood.openpersistence.compat;

import com.wf.wflib.backpack.api.Backpacks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** wflib worn gear that is not in an equipment slot. Only loaded when {@code wflib} is present. */
final class WflibBodySupport {

    private WflibBodySupport() {
    }

    static ItemStack backpack(Player player) {
        return Backpacks.shown(Backpacks.worn(player));
    }
}
