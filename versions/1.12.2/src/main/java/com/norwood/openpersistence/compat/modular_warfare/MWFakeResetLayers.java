package com.norwood.openpersistence.compat.modular_warfare;

import com.norwood.openpersistence.entity.EntityPersistentPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;

public class MWFakeResetLayers implements LayerRenderer<EntityPersistentPlayer> {
    RenderLivingBase renderPlayer;
    public MWFakeResetLayers(RenderLivingBase renderPlayer) {
        this.renderPlayer=renderPlayer;
    }

    public ModelPlayer getMainModel()
    {
        return (ModelPlayer) renderPlayer.getMainModel();
    }

    @Override
    public void doRenderLayer(EntityPersistentPlayer entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        getMainModel().bipedHead.isHidden = false;
        getMainModel().bipedBody.isHidden = false;
        getMainModel().bipedLeftArm.isHidden = false;
        getMainModel().bipedRightArm.isHidden = false;
        getMainModel().bipedLeftLeg.isHidden = false;
        getMainModel().bipedRightLeg.isHidden = false;
        getMainModel().bipedHead.showModel = true;
        getMainModel().bipedBody.showModel = true;
        getMainModel().bipedLeftArm.showModel = true;
        getMainModel().bipedRightArm.showModel = true;
        getMainModel().bipedLeftLeg.showModel = true;
        getMainModel().bipedRightLeg.showModel = true;
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

}
