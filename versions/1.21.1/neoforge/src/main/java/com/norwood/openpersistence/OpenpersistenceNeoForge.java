package com.norwood.openpersistence;

import com.norwood.openpersistence.entity.ModEntities;
import com.norwood.openpersistence.entity.PersistentPlayerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(Openpersistence.MOD_ID)
public class OpenpersistenceNeoForge {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Openpersistence.MOD_ID);

    public static final Supplier<EntityType<PersistentPlayerEntity>> PERSISTENT_PLAYER =
            ENTITIES.register(ModEntities.PERSISTENT_PLAYER_ID, () -> EntityType.Builder
                    .<PersistentPlayerEntity>of(PersistentPlayerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(ModEntities.PERSISTENT_PLAYER_ID));

    public OpenpersistenceNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Openpersistence.init();

        ENTITIES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::onConfig);

        modContainer.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        ModEntities.PERSISTENT_PLAYER = PERSISTENT_PLAYER.get();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PERSISTENT_PLAYER.get(), PersistentPlayerEntity.createAttributes().build());
    }

    private void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == NeoForgeConfig.SPEC) {
            NeoForgeConfig.sync();
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
