package com.norwood.openpersistence.compat;

import de.maxhenkel.gravestone.GraveUtils;
import de.maxhenkel.gravestone.GravestoneMod;
import de.maxhenkel.gravestone.blocks.GraveStoneBlock;
import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

/**
 * Places a GraveStone for the offline player. Mirrors GraveStone's own death handler: find a free spot
 * near the death position, set the grave block, then store the {@code Death} on its block entity. Uses
 * GraveStone's shaded corelib package. Only loaded when the {@code gravestone} mod is present.
 */
final class GravestoneSupport {

    private GravestoneSupport() {
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

        BlockPos pos = GraveUtils.getGraveStoneLocation(level, death.getBlockPos());
        if (pos == null) {
            return false;
        }
        level.setBlockAndUpdate(pos, GravestoneMod.GRAVESTONE.get().defaultBlockState()
                .setValue(GraveStoneBlock.FACING, Direction.NORTH));
        if (GraveUtils.isReplaceable(level, pos.below())) {
            level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof GraveStoneTileEntity grave) {
            grave.setDeath(death);
            return true;
        }
        return false;
    }
}
