package com.norwood.openpersistence;

import com.norwood.openpersistence.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = OpenpersistenceLegacyForge.MODID, name = OpenpersistenceLegacyForge.NAME, version = OpenpersistenceLegacyForge.VERSION, acceptedMinecraftVersions = OpenpersistenceLegacyForge.MC_VERSION)
public class OpenpersistenceLegacyForge {

    public static final String MODID = "openpersistence";
    public static final String NAME = "Open Persistence";
    public static final String VERSION = "1.0.0";
    public static final String MC_VERSION = "[1.12.2]";

    @Mod.Instance
    private static OpenpersistenceLegacyForge INSTANCE;

    @SidedProxy(clientSide = "com.norwood.openpersistence.proxy.ClientProxy", serverSide = "com.norwood.openpersistence.proxy.CommonProxy")
    public static CommonProxy proxy;

    public OpenpersistenceLegacyForge() {
        INSTANCE = this;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    public static OpenpersistenceLegacyForge getInstance() {
        return INSTANCE;
    }

}
