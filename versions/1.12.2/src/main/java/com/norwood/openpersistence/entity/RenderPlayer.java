package com.norwood.openpersistence.entity;

import com.modularwarfare.client.model.FakeLayerBipedArmor;
import com.modularwarfare.client.model.layers.RenderLayerHeldGun;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.norwood.openpersistence.compat.CompatManager;
import com.norwood.openpersistence.compat.modular_warfare.MWFakeBackpackLayer;
import com.norwood.openpersistence.compat.modular_warfare.MWFakeResetLayers;
import com.norwood.openpersistence.compat.modular_warfare.MWFakeVestLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.norwood.openpersistence.compat.CompatManager.ID_MW;

public class RenderPlayer extends RenderLivingBase<EntityPersistentPlayer> {

    private static final Map<UUID, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING_FETCHES = ConcurrentHashMap.newKeySet();
    private final ModelPlayer playerModel;
    private final ModelPlayer playerModelSmallArms;

    public RenderPlayer(RenderManager renderManager) {
        super(renderManager, null, 0.5F);

        playerModel = new ModelPlayer(0F, false);
        playerModelSmallArms = new ModelPlayer(0F, true);
        mainModel = playerModel;
    }

    public static ResourceLocation getSkin(GameProfile gameProfile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        UUID uuid = gameProfile.getId();

        GameProfile cachedProfile = PROFILE_CACHE.get(uuid);
        if (cachedProfile != null) {
            return getSkinFromProfile(minecraft, cachedProfile);
        }

        if (gameProfile.getProperties().containsKey("textures")) {
            PROFILE_CACHE.put(uuid, gameProfile);
            return getSkinFromProfile(minecraft, gameProfile);
        }

        if (!PENDING_FETCHES.contains(uuid)) {
            PENDING_FETCHES.add(uuid);
            CompletableFuture.runAsync(() -> {
                try {
                    GameProfile filledProfile = minecraft.getSessionService().fillProfileProperties(gameProfile, true);
                    PROFILE_CACHE.put(uuid, filledProfile);
                } catch (Exception e) {
                } finally {
                    PENDING_FETCHES.remove(uuid);
                }
            });
        }

        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private static ResourceLocation getSkinFromProfile(Minecraft minecraft, GameProfile profile) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.getSkinManager().loadSkinFromCache(profile);
        if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            return minecraft.getSkinManager().loadSkin(map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN);
        } else {
            return DefaultPlayerSkin.getDefaultSkin(profile.getId());
        }
    }

    public static boolean isSlim(UUID uuid) {
        NetworkPlayerInfo networkplayerinfo = Minecraft.getMinecraft().getConnection().getPlayerInfo(uuid);
        return networkplayerinfo == null ? (uuid.hashCode() & 1) == 1 : networkplayerinfo.getSkinType().equals("slim");
    }

    @Override
    protected void preRenderCallback(EntityPersistentPlayer entitylivingbaseIn, float partialTickTime) {
        float scale = 0.9375F;
        GlStateManager.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getEntityTexture(EntityPersistentPlayer entity) {
        return getSkin(new GameProfile(entity.getPlayerUUID().or(new UUID(0L, 0L)), entity.getPlayerName()));
    }

    @Override
    public void doRender(EntityPersistentPlayer entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        if (isSlim(entity.getPlayerUUID().or(new UUID(0L, 0L)))) {
            mainModel = playerModelSmallArms;
            setModelVisibilities(entity, playerModelSmallArms);
            initLayers(playerModelSmallArms);
        } else {
            mainModel = playerModel;
            setModelVisibilities(entity, playerModel);
            initLayers(playerModel);
        }

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
        GlStateManager.popMatrix();
    }

    private void setModelVisibilities(EntityPersistentPlayer playerEntity, ModelPlayer modelPlayer) {
        modelPlayer.bipedHeadwear.showModel = playerEntity.isWearing(EnumPlayerModelParts.HAT);
        modelPlayer.bipedBodyWear.showModel = playerEntity.isWearing(EnumPlayerModelParts.JACKET);
        modelPlayer.bipedLeftLegwear.showModel = playerEntity.isWearing(EnumPlayerModelParts.LEFT_PANTS_LEG);
        modelPlayer.bipedRightLegwear.showModel = playerEntity.isWearing(EnumPlayerModelParts.RIGHT_PANTS_LEG);
        modelPlayer.bipedLeftArmwear.showModel = playerEntity.isWearing(EnumPlayerModelParts.LEFT_SLEEVE);
        modelPlayer.bipedRightArmwear.showModel = playerEntity.isWearing(EnumPlayerModelParts.RIGHT_SLEEVE);
    }

    private void initLayers(ModelPlayer modelPlayer) {
        layerRenderers.clear();
        addLayer(new LayerBipedArmor(this));
        addLayer(new LayerHeldItem(this));
        addLayer(new LayerElytra(this));
        addLayer(new LayerCustomHead(modelPlayer.bipedHead));

        //MW layers
        if (CompatManager.isLoaded(ID_MW)) {
            addLayer(new MWFakeResetLayers(this));
            addLayer(new FakeLayerBipedArmor(this));
            addLayer(new MWFakeBackpackLayer(this, modelPlayer.bipedBodyWear));
            addLayer(new MWFakeVestLayer(this, modelPlayer.bipedBodyWear));
            addLayer(new RenderLayerHeldGun(this));
        }
    }

    @Override
    protected void applyRotations(EntityPersistentPlayer entityLiving, float f, float rotationYaw, float partialTicks) {
        if (entityLiving.isEntityAlive() && entityLiving.isPlayerSleeping()) {
            GlStateManager.translate(1.8F, 0F, 0F);
            GlStateManager.rotate(this.getDeathMaxRotation(entityLiving), 0F, 0F, 1F);
            GlStateManager.rotate(270F, 0F, 1F, 0F);
        } else {
            super.applyRotations(entityLiving, f, rotationYaw, partialTicks);
        }
    }
}
