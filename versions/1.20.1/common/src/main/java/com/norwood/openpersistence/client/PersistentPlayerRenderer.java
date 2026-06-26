package com.norwood.openpersistence.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.UUID;

public class PersistentPlayerRenderer extends LivingEntityRenderer<PersistentPlayerEntity, PlayerModel<PersistentPlayerEntity>> {

    private final PlayerModel<PersistentPlayerEntity> wideModel;
    private final PlayerModel<PersistentPlayerEntity> slimModel;

    public PersistentPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public void render(PersistentPlayerEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = isSlim(entity) ? this.slimModel : this.wideModel;
        applyModelVisibility(entity, this.model);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void applyModelVisibility(PersistentPlayerEntity entity, PlayerModel<PersistentPlayerEntity> model) {
        model.setAllVisible(true);
        model.hat.visible = entity.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = entity.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = entity.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = entity.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = entity.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = entity.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
    }

    @Override
    protected void scale(PersistentPlayerEntity entity, PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected void setupRotations(PersistentPlayerEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        if (entity.isAlive() && entity.isOfflineSleeping()) {
            poseStack.translate(1.0F, 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(this.getFlipDegrees(entity)));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
        } else {
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PersistentPlayerEntity entity) {
        UUID uuid = entity.getPlayerUUID().orElse(Util.NIL_UUID);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return minecraft.getSkinManager().getInsecureSkinLocation(profileWithSkin(entity, uuid));
    }

    private static boolean isSlim(PersistentPlayerEntity entity) {
        UUID uuid = entity.getPlayerUUID().orElse(Util.NIL_UUID);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return "slim".equals(info.getModelName());
            }
        }
        MinecraftProfileTexture skin = minecraft.getSkinManager()
                .getInsecureSkinInformation(profileWithSkin(entity, uuid))
                .get(MinecraftProfileTexture.Type.SKIN);
        if (skin != null) {
            return "slim".equals(skin.getMetadata("model"));
        }
        return "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid));
    }

    /** Rebuilds the player's {@link GameProfile}, re-attaching the {@code textures} property captured
     *  at logout. Without the property the insecure-skin lookup only sees a bare uuid/name and returns
     *  the default skin — which is why a logged-out body otherwise loses its skin. */
    private static GameProfile profileWithSkin(PersistentPlayerEntity entity, UUID uuid) {
        GameProfile profile = new GameProfile(uuid, entity.getPlayerName());
        String value = entity.getSkinTexture();
        if (!value.isEmpty()) {
            String signature = entity.getSkinSignature();
            profile.getProperties().put("textures",
                    new Property("textures", value, signature.isEmpty() ? null : signature));
        }
        return profile;
    }
}
