package com.norwood.openpersistence.compat.modular_warfare;

import com.modularwarfare.common.capability.extraslots.CapabilityExtra;
import com.modularwarfare.common.capability.extraslots.ExtraContainer;
import com.modularwarfare.common.capability.extraslots.ExtraContainerProvider;
import com.modularwarfare.common.capability.extraslots.IExtraItemHandler;
import com.norwood.openpersistence.entity.EntityPersistentPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class MWCompat {
    public void mwDropSlots(EntityPlayerMP p) {
        IExtraItemHandler slots = (IExtraItemHandler) p.getCapability((Capability) CapabilityExtra.CAPABILITY, (EnumFacing) null);
        for (int i = 0; i < slots.getSlots(); i++) {
            ItemStack stackInSlot = slots.getStackInSlot(i);
            EntityItem dropped = p.entityDropItem(stackInSlot.copy(), 0F);
            if (dropped != null) {
                dropped.setPickupDelay(10);
            }
            stackInSlot.setCount(0);
        }
    }
    @SubscribeEvent
    public void mwAddCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPersistentPlayer) {
            ExtraContainer container = new ExtraContainer();
            event.addCapability(
                    new ResourceLocation("modularwarfare", "extraslots"),
                    new ExtraContainerProvider(container)
            );
        }
    }
    @SubscribeEvent
    public void onTracking(PlayerEvent.StartTracking event){
        if (!(event.getTarget() instanceof EntityPersistentPlayer)) {
            return;
        }
        sync((EntityPersistentPlayer) event.getTarget());
    }
    public void sync(EntityPersistentPlayer persistentPlayer) {
        IExtraItemHandler extra = persistentPlayer.getCapability(CapabilityExtra.CAPABILITY, null);
        if (extra == null) {
            return;
        }
        for (int slot = 0; slot < extra.getSlots(); slot++) {
            MWNetworkHandler.INSTANCE.sendToAllTracking(new MWSyncExtraSlots(persistentPlayer, slot, ((IExtraItemHandler) persistentPlayer.getCapability((Capability) CapabilityExtra.CAPABILITY, (EnumFacing) null)).getStackInSlot(slot)), persistentPlayer);
        }
    }
}
