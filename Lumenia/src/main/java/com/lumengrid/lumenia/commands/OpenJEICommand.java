package com.lumengrid.lumenia.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.Main;
import com.lumengrid.lumenia.gui.JEIGui;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

/**
 * Command to open the JEI-like item browser GUI
 * If an item parameter is provided, it will search for that item and auto-select the first match
 */
public class OpenJEICommand extends AbstractCommand {

    private final OptionalArg<String> argument;

    public OpenJEICommand() {
        super("jei", "Opens the JEI-like item browser", false);
        this.addAliases("recipes", "lumen", "lumenia", "recipe");
        var arg = new SingleArgumentType<String>("Item", "Item to search for and display", "iron_ingot", "wood_planks") {
            @NullableDecl
            @Override
            public String parse(String s, ParseResult parseResult) {
                return s;
            }
        };
        this.argument = this.withOptionalArg("item", "Item ID or name to search for and display", arg);
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandSender sender = context.sender();
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRefComponent == null) {
                        return;
                    }

                    String itemInput = context.get(this.argument);
                    String defaultSearch = "";
                    String selectedItemId = null;

                    // If an item parameter is provided, try to find it and auto-select it
                    if (itemInput != null && !itemInput.isEmpty()) {
                        String language = playerRefComponent.getLanguage();
                        String foundItemId = findItemId(itemInput, language);
                        
                        if (foundItemId != null) {
                            // Found the item - use it as search query and auto-select it
                            defaultSearch = itemInput;
                            selectedItemId = foundItemId;
                        } else {
                            // Item not found, but still use it as search query
                            defaultSearch = itemInput;
                            // selectedItemId remains null - will auto-select first result
                        }
                    }

                    player.getPageManager().openCustomPage(ref, store, 
                        new JEIGui(playerRefComponent, CustomPageLifetime.CanDismiss, defaultSearch, selectedItemId));
                }, world);
            } else {
                context.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Finds an item ID by searching through item IDs and translated names
     */
    private String findItemId(String search, String language) {
        // First try exact match
        if (Main.ITEMS.containsKey(search)) {
            return search;
        }

        // Search by translated name
        search = search.toLowerCase();
        for (Map.Entry<String, Item> entry : Main.ITEMS.entrySet()) {
            String translationKey = entry.getValue().getTranslationKey();
            String translatedName = I18nModule.get().getMessage(language, translationKey);
            if (translatedName != null && translatedName.toLowerCase().contains(search)) {
                return entry.getKey();
            }
        }

        // Search by partial ID match
        for (String itemId : Main.ITEMS.keySet()) {
            if (itemId.toLowerCase().contains(search)) {
                return itemId;
            }
        }

        return null;
    }
}
