package com.lumengrid.lumenia;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.gui.JEIGui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CheckKeybindSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            MovementStatesComponent statesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
            Player player = archetypeChunk.getComponent(index, Player.getComponentType());

            if (player == null || statesComponent == null) {
                return;
            }

            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }

            PlayerRef playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRefComponent == null) {
                return;
            }

            MovementStates movementStates = statesComponent.getMovementStates();

            if (movementStates.walking) {
                boolean pageOpen = player.getPageManager().getCustomPage() != null;
                boolean keybindEnabled = Main.getInstance().config.get().defaultOpenJeiKeybind;
                if (!pageOpen && keybindEnabled) {
                    try {
                        PageManager pageManager = player.getPageManager();
                        if (pageManager.getCustomPage() == null) {
                            pageManager.openCustomPage(ref, store, new JEIGui(playerRefComponent, CustomPageLifetime.CanDismiss));
                        }
                    } catch (Exception e) {
                        LOGGER.atSevere().log("Lumenia: Failed to open JEI page: " + e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().log("Lumenia: Error in CheckKeybindSystem: " + e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return MovementStatesComponent.getComponentType();
    }
}
