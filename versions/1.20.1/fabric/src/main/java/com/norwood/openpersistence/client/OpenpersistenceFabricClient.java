package com.norwood.openpersistence.client;

import com.norwood.openpersistence.OpenpersistenceFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class OpenpersistenceFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(OpenpersistenceFabric.PERSISTENT_PLAYER, PersistentPlayerRenderer::new);
    }
}
