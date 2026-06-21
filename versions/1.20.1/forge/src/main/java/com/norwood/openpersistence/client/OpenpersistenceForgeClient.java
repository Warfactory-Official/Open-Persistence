package com.norwood.openpersistence.client;

import com.norwood.openpersistence.Openpersistence;
import com.norwood.openpersistence.OpenpersistenceForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Openpersistence.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OpenpersistenceForgeClient {

    private OpenpersistenceForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OpenpersistenceForge.PERSISTENT_PLAYER.get(), PersistentPlayerRenderer::new);
    }
}
