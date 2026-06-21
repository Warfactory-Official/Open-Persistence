package com.norwood.openpersistence;

import com.norwood.openpersistence.entity.ModEntities;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Openpersistence.MOD_ID)
public class OpenpersistenceForge {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Openpersistence.MOD_ID);

    public static final RegistryObject<EntityType<PersistentPlayerEntity>> PERSISTENT_PLAYER =
            ENTITIES.register(ModEntities.PERSISTENT_PLAYER_ID, () -> EntityType.Builder
                    .<PersistentPlayerEntity>of(PersistentPlayerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(ModEntities.PERSISTENT_PLAYER_ID));

    public OpenpersistenceForge() {
        Openpersistence.init();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::onConfig);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ModEntities.PERSISTENT_PLAYER = PERSISTENT_PLAYER.get();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PERSISTENT_PLAYER.get(), PersistentPlayerEntity.createAttributes().build());
    }

    private void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ForgeConfig.SPEC) {
            ForgeConfig.sync();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersistentPlayerManager.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersistentPlayerManager.onPlayerLogout(player);
        }
    }
}
