package com.norwood.openpersistence.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.norwood.openpersistence.OpenPersistenceConfig;
import com.norwood.openpersistence.PersistentPlayerManager;
import com.norwood.openpersistence.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final EntityDataAccessor<ItemStack> BACKPACK =
            SynchedEntityData.defineId(PersistentPlayerEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Snapshot of the player's curio stacks at logout (NeoForge only); dropped on death. */
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private final List<ItemStack> extraItems = new ArrayList<>();
    /** Set once the owner got this body's state back; exactly one restorer wins. */
    private final AtomicBoolean claimed = new AtomicBoolean();

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

    public static PersistentPlayerEntity fromPlayer(Player player) {
        PersistentPlayerEntity body = new PersistentPlayerEntity(ModEntities.PERSISTENT_PLAYER, player.level());
        body.setPlayerName(player.getGameProfile().getName());
        body.setPlayerUUID(player.getUUID());
        captureSkin(body, player);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }

        body.extraItems.clear();
        body.extraItems.addAll(Services.CURIOS.snapshot(player));
        body.setBackpack(Services.CURIOS.backpack(player));

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
            body.setSkinTexture(texture.value(), texture.signature());
        }
    }

    private static byte modelCustomisation(Player player) {
        byte mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                mask |= part.getMask();
            }
        }
        return mask;
    }

    public boolean claim() {
        return claimed.compareAndSet(false, true);
    }

    public boolean isClaimed() {
        return claimed.get();
    }

    /** Immutable copy of what the owner gets back on rejoin; safe to hand to another thread. */
    public Restore restoreState(ServerLevel level) {
        List<MobEffectInstance> copies = new ArrayList<>();
        for (MobEffectInstance effect : getActiveEffects()) {
            copies.add(new MobEffectInstance(effect));
        }
        return new Restore(level, getX(), getY(), getZ(), getYRot(), getXRot(), getHealth(), getAirSupply(),
                getRemainingFireTicks(), copies, wornArmor());
    }

    /** Armour as worn now: plate/garment wear and inserts changed on the body, which the offline save never saw. */
    private List<ItemStack> wornArmor() {
        List<ItemStack> worn = new ArrayList<>(ARMOR_SLOTS.length);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            worn.add(getItemBySlot(slot).copy());
        }
        return worn;
    }

    /** {@code player}'s armour slots := the body's. */
    public static void applyArmor(Player player, List<ItemStack> armor) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            player.getInventory().armor.set(ARMOR_SLOTS[i].getIndex(), armor.get(i).copy());
        }
    }

    /** {@code armor}: {@link #ARMOR_SLOTS} order. */
    public record Restore(ServerLevel level, double x, double y, double z, float yRot, float xRot, float health,
                          int air, int fire, List<MobEffectInstance> effects, List<ItemStack> armor) {
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
            PersistentPlayerManager.onBodyTick(this);
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide) {
            PersistentPlayerManager.onBodyDeath(this);
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PLAYER_UUID, Optional.empty());
        builder.define(PLAYER_NAME, "");
        builder.define(PLAYER_MODEL, (byte) 0);
        builder.define(SKIN_TEXTURE, "");
        builder.define(SKIN_SIGNATURE, "");
        builder.define(BACKPACK, ItemStack.EMPTY);
    }

    public ItemStack getBackpack() {
        return this.entityData.get(BACKPACK);
    }

    public void setBackpack(ItemStack stack) {
        this.entityData.set(BACKPACK, stack);
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

    public boolean isModelPartShown(PlayerModelPart part) {
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

    /** Owner's profile with the skin captured at logout. */
    public GameProfile profile() {
        GameProfile profile = new GameProfile(getPlayerUUID().orElse(new UUID(0L, 0L)), getPlayerName());
        String texture = getSkinTexture();
        if (!texture.isEmpty()) {
            String signature = getSkinSignature();
            profile.getProperties().put("textures", new Property("textures", texture, signature.isEmpty() ? null : signature));
        }
        return profile;
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
        if (!getBackpack().isEmpty()) {
            tag.put("Backpack", getBackpack().save(this.registryAccess()));
        }

        ListTag list = new ListTag();
        for (ItemStack stack : extraItems) {
            if (!stack.isEmpty()) {
                list.add(stack.save(this.registryAccess()));
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
        setBackpack(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Backpack")));

        extraItems.clear();
        ListTag list = tag.getList("ExtraItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(this.registryAccess(), list.getCompound(i)).ifPresent(stack -> {
                if (!stack.isEmpty()) {
                    extraItems.add(stack);
                }
            });
        }
    }
}
