package com.norwood.openpersistence.compat.modular_warfare;

import com.modularwarfare.common.capability.extraslots.CapabilityExtra;
import com.modularwarfare.common.capability.extraslots.IExtraItemHandler;
import com.norwood.openpersistence.entity.EntityPersistentPlayer;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MWSyncExtraSlots implements IMessage {
    private int entityId;
    private int slot;
    private ItemStack itemStack;

    public MWSyncExtraSlots() {
    }

    public MWSyncExtraSlots(EntityPersistentPlayer entity, int slot, ItemStack stack) {
        this.entityId = entity.getEntityId();
        this.slot = slot;
        this.itemStack = stack;
    }
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(slot);
        ByteBufUtils.writeItemStack(buf, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        slot = buf.readInt();
        itemStack = ByteBufUtils.readItemStack(buf);
    }
    public static class Handler implements IMessageHandler<MWSyncExtraSlots, IMessage> {
        @Override
        public IMessage onMessage(MWSyncExtraSlots message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    Entity entity = Minecraft.getMinecraft().world.getEntityByID(message.entityId);
                    if (entity instanceof EntityPersistentPlayer) {
                        ((IExtraItemHandler) entity.getCapability((Capability) CapabilityExtra.CAPABILITY, (EnumFacing) null)).setStackInSlot(message.slot, message.itemStack);
                    }
                });
            }
            return null;
        }
    }
}
