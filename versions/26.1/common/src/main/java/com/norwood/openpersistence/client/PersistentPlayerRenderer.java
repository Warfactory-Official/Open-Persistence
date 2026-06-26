package com.norwood.openpersistence.client;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.norwood.openpersistence.OpenPersistenceConfig;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the persistent body as a player (skin, model-part customisation, worn armor & held items)
 * using the 26.x render-state pipeline. The body extends a mob, so we build on
 * {@link HumanoidMobRenderer} (which already supplies head/wings/held-item layers) and reuse the
 * vanilla {@link AvatarRenderState}/{@link PlayerModel} so the player skin renders correctly.
 */
public class PersistentPlayerRenderer
        extends HumanoidMobRenderer<PersistentPlayerEntity, AvatarRenderState, PlayerModel> {

    private static final Map<UUID, PlayerSkin> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    public PersistentPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
                context.getEquipmentRenderer()));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(PersistentPlayerEntity entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = resolveSkin(entity);
        state.showHat = entity.isModelPartShown(PlayerModelPart.HAT);
        state.showJacket = entity.isModelPartShown(PlayerModelPart.JACKET);
        state.showLeftPants = entity.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        state.showRightPants = entity.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        state.showLeftSleeve = entity.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        state.showRightSleeve = entity.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        state.showCape = false;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }

    @Override
    protected void scale(AvatarRenderState state, PoseStack poseStack) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        if (OpenPersistenceConfig.offlinePlayersSleep) {
            poseStack.translate(1.0F, 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
        } else {
            super.setupRotations(state, poseStack, bodyRot, entityScale);
        }
    }

    private static PlayerSkin resolveSkin(PersistentPlayerEntity entity) {
        UUID uuid = entity.getPlayerUUID().orElse(Util.NIL_UUID);
        PlayerSkin cached = SKIN_CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }
        // Only fetch once the captured textures property has synced in; a bare uuid/name profile
        // resolves to the default skin, and caching that would pin the body to Steve. If the data
        // hasn't arrived yet we leave PENDING unset so a later frame retries.
        String value = entity.getSkinTexture();
        if (!value.isEmpty() && PENDING.add(uuid)) {
            GameProfile profile = profileWithSkin(uuid, entity.getPlayerName(), value, entity.getSkinSignature());
            Minecraft.getInstance().getSkinManager().get(profile)
                    .thenAccept(opt -> opt.ifPresent(skin -> SKIN_CACHE.put(uuid, skin)));
        }
        return DefaultPlayerSkin.get(uuid);
    }

    /** Rebuilds the player's {@link GameProfile} with the {@code textures} property captured at logout,
     *  so the skin lookup has something to unpack instead of falling back to the default skin. */
    private static GameProfile profileWithSkin(UUID uuid, String name, String value, String signature) {
        PropertyMap properties = new PropertyMap(LinkedHashMultimap.create());
        properties.put("textures", new Property("textures", value, signature.isEmpty() ? null : signature));
        return new GameProfile(uuid, name, properties);
    }
}
