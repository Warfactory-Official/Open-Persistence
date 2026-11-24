package com.norwood.openpersistence.client;

import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import com.wf.wflib.backpack.api.client.BackpackLayer;
import com.wf.wflib.core.api.WFSystems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Only loaded when {@code wflib} is present. */
final class WflibBodyLayers {

    private WflibBodyLayers() {
    }

    static void add(EntityRenderersEvent.AddLayers event, EntityType<PersistentPlayerEntity> type) {
        if (WFSystems.enabled("backpack")) {
            LivingEntityRenderer<PersistentPlayerEntity, PlayerModel<PersistentPlayerEntity>> renderer = event.getRenderer(type);
            renderer.addLayer(new BackpackLayer<>(renderer, PersistentPlayerEntity::getBackpack, null));
        }
    }
}
