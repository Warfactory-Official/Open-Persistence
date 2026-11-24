package com.norwood.openpersistence.compat;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.core.api.HitRouter;
import com.wf.wflib.corpse.api.Corpses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** wflib corpse (ragdoll body, every slot incl. attachments). Only loaded when {@code wflib} is present. */
final class WflibCorpseSupport {

    private WflibCorpseSupport() {
    }

    static boolean claim(LivingEntity body, ServerPlayer offline, GameProfile profile) {
        return Corpses.claim(offline, profile, body);
    }

    static void onOwner(Entity body, Runnable work) {
        HitRouter.apply(body.level(), body, work);
    }
}
