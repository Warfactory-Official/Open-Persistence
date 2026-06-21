package com.norwood.openpersistence.compat.modular_warfare;

import com.modularwarfare.ModularWarfare;
import com.modularwarfare.client.model.ModelCustomArmor;
import com.modularwarfare.common.armor.ArmorType;
import com.modularwarfare.common.armor.ItemSpecialArmor;
import com.modularwarfare.common.capability.extraslots.CapabilityExtra;
import com.modularwarfare.common.capability.extraslots.IExtraItemHandler;
import com.norwood.openpersistence.entity.EntityPersistentPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MWFakeVestLayer implements LayerRenderer<EntityPersistentPlayer> {
    private final ModelRenderer modelRenderer;
    private RenderLivingBase renderer;

    public MWFakeVestLayer(final RenderLivingBase renderer, final ModelRenderer modelRenderer) {
        this.modelRenderer = modelRenderer;
        this.renderer = renderer;
    }

    public void doRenderLayer(final EntityPersistentPlayer player, final float limbSwing, final float limbSwingAmount, final float partialTicks, final float ageInTicks, final float netHeadYaw, final float headPitch, final float scale) {
        final int[] slots = new int[]{1};
        if (player.hasCapability(CapabilityExtra.CAPABILITY, null)) {
            final IExtraItemHandler extraSlots = player.getCapability(CapabilityExtra.CAPABILITY, null);
            for (final int slot : slots) {
                final ItemStack itemStackSpecialArmor = extraSlots.getStackInSlot(slot);
                if (!itemStackSpecialArmor.isEmpty() && itemStackSpecialArmor.getItem() instanceof ItemSpecialArmor) {
                    this.renderBody(player, ((ItemSpecialArmor) itemStackSpecialArmor.getItem()).type, scale);
                }
            }
        }
    }
    public ModelPlayer getMainModel()
    {
        return (ModelPlayer) renderer.getMainModel();    }
    public void renderBody(final EntityPersistentPlayer player, final ArmorType armorType, final float scale) {
        if (armorType.hasModel()) {
            final ModelCustomArmor armorModel = (ModelCustomArmor) armorType.bipedModel;
            GlStateManager.pushMatrix();
            if (player.isSneaking()) {
                GlStateManager.translate(0.0f, 0.2f, 0.0f);
                GlStateManager.rotate(30.0f, 1.0f, 0.0f, 0.0f);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableRescaleNormal();
            final int skinId = 0;
            String path = skinId > 0 ? "skins/" + armorType.modelSkins[skinId].getSkin() : armorType.modelSkins[0].getSkin();
            Minecraft.getMinecraft().getRenderManager().renderEngine.bindTexture(new ResourceLocation(ModularWarfare.MOD_ID, "skins/armor/" + path + ".png"));
            GL11.glScalef(1.0f, 1.0f, 1.0f);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            armorModel.render("armorModel", getMainModel().bipedBody, 0.0625f, 1f);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.popMatrix();
        }
    }


    public boolean shouldCombineTextures() {
        return true;
    }
}
