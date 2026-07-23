package com.norwood.openpersistence.entity;

import com.mojang.authlib.properties.Property;
import com.norwood.openpersistence.OpenPersistenceConfig;
import com.norwood.openpersistence.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A body left behind by a player who logged out of a multiplayer server. It looks like the
 * player, keeps their gear for display, and can be killed to drop the player's inventory.
 *
 * <p>It must NOT behave like a normal mob with respect to despawning: it survives peaceful
 * difficulty and {@code doMobSpawning=false}. This is achieved by registering it under a
 * non-MONSTER {@link net.minecraft.world.entity.MobCategory}, marking it persistence-required,
 * and refusing every despawn path.</p>
 */
public class PersistentPlayerEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> PLAYER_UUID =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> PLAYER_NAME =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> PLAYER_MODEL =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.BYTE);
    /** Base64 {@code textures} property captured from the player at logout, so the body keeps the
     *  real skin once the player has left and is no longer in the client's tab list. */
    private static final EntityDataAccessor<String> SKIN_TEXTURE =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.STRING);
    /** Mojang signature for {@link #SKIN_TEXTURE} ("" when unsigned, e.g. offline mode). */
    private static final EntityDataAccessor<String> SKIN_SIGNATURE =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.STRING);

    /** Snapshot of the player's curio stacks at logout (Forge/NeoForge only); dropped on death. */
    private final List<ItemStack> extraItems = new ArrayList<>();

    public PersistentPlayerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    /** Builds a persistent body that mirrors the given (logging-out) player. */
    public static PersistentPlayerEntity fromPlayer(Player player) {
        PersistentPlayerEntity body = new PersistentPlayerEntity(ModEntities.PERSISTENT_PLAYER, player.level());
        body.setPlayerName(player.getGameProfile().getName());
        body.setPlayerUUID(player.getUUID());
        captureSkin(body, player);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }

        // Curios / extra slots (no-op on platforms without Curios).
        body.extraItems.clear();
        body.extraItems.addAll(Services.CURIOS.snapshot(player));

        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        body.yHeadRot = player.yHeadRot;
        body.yHeadRotO = player.yHeadRotO;
        body.yBodyRot = player.yBodyRot;
        body.setHealth(player.getHealth());
        body.setAirSupply(player.getAirSupply());
        body.setRemainingFireTicks(player.getRemainingFireTicks());
        for (MobEffectInstance effect : player.getActiveEffects()) {
            body.addEffect(new MobEffectInstance(effect));
        }
        body.setInvulnerable(player.isCreative());
        body.setPlayerModelByte(modelCustomisation(player));
        return body;
    }

    /** Copy the player's signed {@code textures} property (skin/cape) onto the body, if present.
     *  Absent in offline mode, where no authenticated textures exist — the body then falls back to
     *  the default skin, exactly like the player it mirrors. */
    private static void captureSkin(PersistentPlayerEntity body, Player player) {
        Collection<Property> textures = player.getGameProfile().getProperties().get("textures");
        if (!textures.isEmpty()) {
            Property texture = textures.iterator().next();
            body.setSkinTexture(texture.getValue(), texture.getSignature());
        }
    }

    /** Rebuild the worn-model-parts bitmask from the player's public {@code isModelPartShown}. */
    private static byte modelCustomisation(Player player) {
        byte mask = 0;
        for (net.minecraft.world.entity.player.PlayerModelPart part : net.minecraft.world.entity.player.PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                mask |= part.getMask();
            }
        }
        return mask;
    }

    public boolean isOfflineSleeping() {
        return OpenPersistenceConfig.offlinePlayersSleep;
    }

    public List<ItemStack> getExtraItems() {
        return extraItems;
    }

    // --- Despawn immunity (peaceful difficulty / doMobSpawning=false) -------------------------

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Never despawn: not on peaceful, not when far away, not when mob spawning is disabled.
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // --- Behaviour ---------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (!OpenPersistenceConfig.offlinePlayersSleep) {
            this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
        }
    }


    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            com.norwood.openpersistence.PersistentPlayerManager.onBodyTick(this);
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide) {
            com.norwood.openpersistence.PersistentPlayerManager.onBodyDeath(this);
        }
    }

    @Override
    public Component getName() {
        String name = getPlayerName();
        if (name == null || name.trim().isEmpty()) {
            return Component.literal("Player");
        }
        return Component.literal(name);
    }

    // --- Synched data ------------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PLAYER_UUID, Optional.empty());
        this.entityData.define(PLAYER_NAME, "");
        this.entityData.define(PLAYER_MODEL, (byte) 0);
        this.entityData.define(SKIN_TEXTURE, "");
        this.entityData.define(SKIN_SIGNATURE, "");
    }

    public Optional<UUID> getPlayerUUID() {
        return this.entityData.get(PLAYER_UUID);
    }

    public void setPlayerUUID(UUID uuid) {
        this.entityData.set(PLAYER_UUID, Optional.ofNullable(uuid));
    }

    public String getPlayerName() {
        return this.entityData.get(PLAYER_NAME);
    }

    public void setPlayerName(String name) {
        this.entityData.set(PLAYER_NAME, name == null ? "" : name);
    }

    public byte getPlayerModelByte() {
        return this.entityData.get(PLAYER_MODEL);
    }

    public void setPlayerModelByte(byte value) {
        this.entityData.set(PLAYER_MODEL, value);
    }

    public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
        return (getPlayerModelByte() & part.getMask()) == part.getMask();
    }

    public String getSkinTexture() {
        return this.entityData.get(SKIN_TEXTURE);
    }

    public String getSkinSignature() {
        return this.entityData.get(SKIN_SIGNATURE);
    }

    public void setSkinTexture(String value, String signature) {
        this.entityData.set(SKIN_TEXTURE, value == null ? "" : value);
        this.entityData.set(SKIN_SIGNATURE, signature == null ? "" : signature);
    }

    // --- NBT ---------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getPlayerUUID().ifPresent(uuid -> tag.putUUID("PlayerUUID", uuid));
        tag.putString("PlayerName", getPlayerName());
        tag.putByte("PlayerModel", getPlayerModelByte());
        tag.putString("SkinTexture", getSkinTexture());
        tag.putString("SkinSignature", getSkinSignature());

        ListTag list = new ListTag();
        for (ItemStack stack : extraItems) {
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundTag()));
            }
        }
        tag.put("ExtraItems", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PlayerUUID")) {
            setPlayerUUID(tag.getUUID("PlayerUUID"));
        }
        setPlayerName(tag.getString("PlayerName"));
        setPlayerModelByte(tag.getByte("PlayerModel"));
        setSkinTexture(tag.getString("SkinTexture"), tag.getString("SkinSignature"));

        extraItems.clear();
        ListTag list = tag.getList("ExtraItems", 10);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                extraItems.add(stack);
            }
        }
    }
}
