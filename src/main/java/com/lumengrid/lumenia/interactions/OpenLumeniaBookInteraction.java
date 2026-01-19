package com.lumengrid.lumenia.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.gui.JEIGui;

import javax.annotation.Nonnull;

public class OpenLumeniaBookInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<OpenLumeniaBookInteraction> CODEC =
            BuilderCodec.builder(
                    OpenLumeniaBookInteraction.class,
                    OpenLumeniaBookInteraction::new,
                    SimpleInstantInteraction.CODEC
            )
            .build();

    protected OpenLumeniaBookInteraction() {
        super();
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> entityRef = context.getEntity();

        Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack heldItem = context.getHeldItem();

        if (heldItem == null || !heldItem.getItemId().equals("Lumenia_Book")) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        player.getWorld().execute(() -> {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }

            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent = 
                    ref.getStore().getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            PlayerRef playerRef = Universe.get().getPlayer(uuidComponent.getUuid());
            if (playerRef == null) {
                return;
            }

            player.getPageManager().openCustomPage(ref, ref.getStore(), new JEIGui(playerRef, CustomPageLifetime.CanDismiss));
        });

        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateFirstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
    }

    @Nonnull
    @Override
    public String toString() {
        return "OpenLumeniaBookInteraction{} " + super.toString();
    }
}
