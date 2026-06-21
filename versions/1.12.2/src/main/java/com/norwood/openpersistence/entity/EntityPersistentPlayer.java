package com.norwood.openpersistence.entity;

import com.google.common.base.Optional;
import com.modularwarfare.common.capability.extraslots.CapabilityExtra;
import com.modularwarfare.common.capability.extraslots.ExtraContainer;
import com.modularwarfare.common.capability.extraslots.IExtraItemHandler;
import com.norwood.openpersistence.Config;
import com.norwood.openpersistence.Logger;
import com.norwood.openpersistence.compat.CompatManager;
import com.norwood.openpersistence.compat.modular_warfare.MWCompat;
import com.norwood.openpersistence.proxy.CommonProxy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

import java.util.Arrays;
import java.util.UUID;

import static com.norwood.openpersistence.compat.CompatManager.ID_MW;

public class EntityPersistentPlayer extends EntityCreature {

    private static final DataParameter<Optional<UUID>> PLAYER_UUID = EntityDataManager.createKey(EntityPersistentPlayer.class, DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final DataParameter<String> NAME = EntityDataManager.createKey(EntityPersistentPlayer.class, DataSerializers.STRING);
    private static final DataParameter<Byte> PLAYER_MODEL = EntityDataManager.createKey(EntityPersistentPlayer.class, DataSerializers.BYTE);

    public EntityPersistentPlayer(World world) {
        super(world);
        Arrays.fill(inventoryArmorDropChances, 0F);
        Arrays.fill(inventoryHandsDropChances, 0F);

        if (Config.offlinePlayersSleep) {
            width = 0.25F;
            height = 0.25F;
        }
    }

    public static EntityPersistentPlayer fromPlayer(EntityPlayer player) {
        EntityPersistentPlayer persistentPlayer = new EntityPersistentPlayer(player.world);
        persistentPlayer.setPlayerName(player.getName());
        persistentPlayer.setPlayerUUID(player.getUniqueID());
        for (EntityEquipmentSlot equipmentSlot : EntityEquipmentSlot.values()) {
            persistentPlayer.setItemStackToSlot(equipmentSlot, player.getItemStackFromSlot(equipmentSlot).copy());
        }
        if (CompatManager.isLoaded(ID_MW)) {
            IExtraItemHandler playerHandler = player.getCapability(CapabilityExtra.CAPABILITY, (EnumFacing) null);
            IExtraItemHandler persistentPlayerHandler = persistentPlayer.getCapability(CapabilityExtra.CAPABILITY, (EnumFacing) null);
            if (playerHandler != null && persistentPlayerHandler != null) {
                if (persistentPlayerHandler instanceof ExtraContainer) {
                    ((ExtraContainer) persistentPlayerHandler).setPlayer(player);
                }
                for (int i = 0; i < playerHandler.getSlots(); i++) {
                    persistentPlayerHandler.setStackInSlot(i, playerHandler.getStackInSlot(i));
                }
            } else if (Config.DEBUG) {
                Logger.error("Unable to render extra slot items");
            }
            if (Config.DEBUG) {
                if (persistentPlayer.hasCapability(CapabilityExtra.CAPABILITY, null)) {
                    Logger.info("Modular Warfare extra slots successfully applied to persistent player!");
                } else {
                    Logger.error("Failed to apply Modular Warfare extra slots to persistent player");
                }
            }
        }
        persistentPlayer.setPosition(player.posX, player.posY, player.posZ);
        persistentPlayer.rotationYaw = player.rotationYaw;
        persistentPlayer.prevRotationYaw = player.prevRotationYaw;
        persistentPlayer.rotationPitch = player.rotationPitch;
        persistentPlayer.prevRotationPitch = player.prevRotationPitch;
        persistentPlayer.rotationYawHead = player.rotationYawHead;
        persistentPlayer.prevRotationYawHead = player.prevRotationYawHead;
        persistentPlayer.setHealth(player.getHealth());
        persistentPlayer.setAir(player.getAir());
        persistentPlayer.setFire(persistentPlayer.fire);
        player.getActivePotionEffects().forEach(persistentPlayer::addPotionEffect);
        persistentPlayer.setEntityInvulnerable(player.isCreative());
        persistentPlayer.setPlayerModel(player.getDataManager().get(EntityPlayer.PLAYER_MODEL_FLAG));
        return persistentPlayer;
    }

    public boolean isPlayerSleeping() {
        return Config.offlinePlayersSleep;
    }

    protected boolean canDespawn() {
        return false;
    }

    public void toPlayer(EntityPlayerMP player) {
        player.setHealth(getHealth());
        player.setAir(getAir());
        player.setFire(player.fire);
        getActivePotionEffects().forEach(player::addPotionEffect);
        player.connection.setPlayerLocation(posX, posY, posZ, rotationYaw, rotationPitch);
        player.getServerWorld().getMinecraftServer().addScheduledTask(() -> {
            player.setPositionAndUpdate(posX, posY, posZ);
        });
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);

        CommonProxy.PLAYER_EVENTS.updatePersistentPlayerLocation(this, p -> {
            p.setHealth(0F);
            if (CompatManager.isLoaded(ID_MW)) {
                MWCompat mwCompat = new MWCompat();
                mwCompat.mwDropSlots(p);
            }
            for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
                ItemStack stackInSlot = p.inventory.getStackInSlot(i);
                p.inventory.removeStackFromSlot(i);
                entityDropItem(stackInSlot, 0F);
            }
            if (Config.DEBUG) {
                Logger.info("Persistent player killed");
            }
        });
    }

    @Override
    public Entity changeDimension(int dimensionIn, ITeleporter teleporter) {
        Entity entity = super.changeDimension(dimensionIn, teleporter);
        if (entity instanceof EntityPersistentPlayer) {
            CommonProxy.PLAYER_EVENTS.updatePersistentPlayerLocation((EntityPersistentPlayer) entity, null);
        }
        return entity;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        if (!getIsInvulnerable()) {
            return super.isEntityInvulnerable(source);
        }
        return source != DamageSource.OUT_OF_WORLD;
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        if (!Config.offlinePlayersSleep) {
            tasks.addTask(1, new EntityAILookIdle(this));
        }
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20D);
    }

    @Override
    public ITextComponent getDisplayName() {
        String name = getPlayerName();
        if (name == null || name.trim().isEmpty()) {
            return new TextComponentString("Player");
        } else {
            return new TextComponentString(getPlayerName());
        }
    }

    public Optional<UUID> getPlayerUUID() {
        return dataManager.get(PLAYER_UUID);
    }

    public void setPlayerUUID(UUID uuid) {
        if (uuid == null) {
            dataManager.set(PLAYER_UUID, Optional.absent());
        } else {
            dataManager.set(PLAYER_UUID, Optional.of(uuid));
        }
    }

    public String getPlayerName() {
        return dataManager.get(NAME);
    }

    public void setPlayerName(String name) {
        dataManager.set(NAME, name);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(PLAYER_UUID, Optional.absent());
        dataManager.register(NAME, "");
        dataManager.register(PLAYER_MODEL, (byte) 0);
    }

    public boolean isWearing(EnumPlayerModelParts part) {
        return (getPlayerModel() & part.getPartMask()) == part.getPartMask();
    }

    protected byte getPlayerModel() {
        return dataManager.get(PLAYER_MODEL);
    }

    protected void setPlayerModel(byte b) {
        dataManager.set(PLAYER_MODEL, b);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (getPlayerUUID().isPresent()) {
            compound.setUniqueId("playerUUID", getPlayerUUID().get());
        }

        compound.setString("playerName", getPlayerName());

        compound.setByte("playerModel", getPlayerModel());

        CommonProxy.PLAYER_EVENTS.updatePersistentPlayerLocation(this, null);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);

        if (compound.hasKey("playerUUIDMost")) {
            setPlayerUUID(compound.getUniqueId("playerUUID"));
        }

        setPlayerName(compound.getString("playerName"));

        setPlayerModel(compound.getByte("playerModel"));
    }

}
