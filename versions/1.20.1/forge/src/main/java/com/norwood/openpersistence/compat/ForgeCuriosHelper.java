package com.norwood.openpersistence.compat;

import com.norwood.openpersistence.platform.services.CuriosHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Curios integration. Curios is a soft (compile-only) dependency, so every method first checks
 * {@link #isAvailable()} before touching any {@code top.theillusivec4.curios} class — that keeps
 * the JVM from linking Curios types when the mod is absent.
 */
public class ForgeCuriosHelper implements CuriosHelper {

    @Override
    public boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded("curios");
    }

    @Override
    public List<ItemStack> snapshot(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!isAvailable()) {
            return stacks;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            IItemHandlerModifiable curios = handler.getEquippedCurios();
            for (int i = 0; i < curios.getSlots(); i++) {
                stacks.add(curios.getStackInSlot(i).copy());
            }
        });
        return stacks;
    }

    @Override
    public void clear(LivingEntity entity) {
        if (!isAvailable()) {
            return;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            IItemHandlerModifiable curios = handler.getEquippedCurios();
            for (int i = 0; i < curios.getSlots(); i++) {
                curios.setStackInSlot(i, ItemStack.EMPTY);
            }
        });
    }
}
