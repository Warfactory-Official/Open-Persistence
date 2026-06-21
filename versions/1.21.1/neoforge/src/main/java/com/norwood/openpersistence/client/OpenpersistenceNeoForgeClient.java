package com.norwood.openpersistence.client;

import com.norwood.openpersistence.Openpersistence;
import com.norwood.openpersistence.OpenpersistenceNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Openpersistence.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OpenpersistenceNeoForgeClient {

    private OpenpersistenceNeoForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OpenpersistenceNeoForge.PERSISTENT_PLAYER.get(), PersistentPlayerRenderer::new);
    }
}
