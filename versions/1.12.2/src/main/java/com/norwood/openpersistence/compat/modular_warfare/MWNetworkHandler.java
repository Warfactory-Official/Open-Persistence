package com.norwood.openpersistence.compat.modular_warfare;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class MWNetworkHandler {
    public static final String CHANNEL_NAME = "PersistentPlayers";
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
    private static int packetId = 0;

    public static void registerPackets() {
        INSTANCE.registerMessage(MWSyncExtraSlots.Handler.class, MWSyncExtraSlots.class, packetId++, Side.CLIENT);
    }
}
