package com.lumengrid.lumenia.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.gui.JEIGui;

import java.util.concurrent.CompletableFuture;

/**
 * Helper class for opening JEI GUI
 */
public class JEIHelper {
    
    /**
     * Opens the JEI GUI for a player
     * @param player The player to open JEI for
     * @param defaultSearch Optional search query
     * @param selectedItemId Optional item ID to auto-select
     */
    public static void openJEI(Player player, String defaultSearch, String selectedItemId) {
        if (player == null) {
            return;
        }
        
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        
        CompletableFuture.runAsync(() -> {
            PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComponent == null) {
                return;
            }
            
            player.getPageManager().openCustomPage(ref, store, 
                new JEIGui(playerRefComponent, CustomPageLifetime.CanDismiss, 
                    defaultSearch != null ? defaultSearch : "", selectedItemId));
        }, world);
    }
    
    /**
     * Opens the JEI GUI for a player with no search or selection
     */
    public static void openJEI(Player player) {
        openJEI(player, null, null);
    }
}
