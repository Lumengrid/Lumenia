package com.lumengrid.lumenia.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.MatchResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.Lumenia;
import com.lumengrid.lumenia.LumeniaComponent;
import com.lumengrid.lumenia.MobDropInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

/**
 * JEI-like GUI for browsing items and viewing recipes
 */
public class JEIGui extends InteractiveCustomUIPage<JEIGui.GuiData> {

    private String searchQuery = "";
    private final Map<String, Item> visibleItems = new HashMap<>();
    private String selectedItem = null;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 20; // 5 columns * 4 rows
    private String activeSection = "info";
    private int craftPage = 0;
    private int usagePage = 0;
    private int dropsPage = 0;
    private static final int CRAFT_RECIPES_PER_PAGE = 1;
    private static final int USAGE_RECIPES_PER_PAGE = 1;
    private static final int DROPS_PER_PAGE = 3;
    private String selectedModFilter = ""; // Empty string means "All Mods"


    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String defaultSearchQuery) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = defaultSearchQuery != null ? defaultSearchQuery : "";
    }

    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String defaultSearchQuery, String selectedItemId) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = defaultSearchQuery != null ? defaultSearchQuery : "";
    }

    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        this(playerRef, lifetime, "");
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Lumengrid_Lumenia_Gui.ui");

        // Append the item info panel
        uiCommandBuilder.append("#RecipeSection", "Pages/Lumengrid_Lumenia_ItemInfo.ui");

        // Set the search input value if we have a default search query
        if (this.searchQuery != null && !this.searchQuery.isEmpty()) {
            uiCommandBuilder.set("#SearchInput.Value", this.searchQuery);
        }

        // Event binding for search
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"), false);

        // Build mod filter dropdown
        this.buildModFilterDropdown(ref, uiCommandBuilder, uiEventBuilder, store);

        // Only show checkbox if global config enables the keybind feature
        boolean globalKeybindEnabled = Lumenia.getInstance().config.get().defaultOpenJeiKeybind;
        if (globalKeybindEnabled) {
            // Feature is enabled globally - show checkbox and allow per-player opt-out
            LumeniaComponent component = store.getComponent(ref, LumeniaComponent.getComponentType());
            // If component exists and player has opted out, checkbox is false
            // If component doesn't exist or player hasn't opted out, checkbox is true (uses global default)
            boolean playerKeybindEnabled = true; // Default to enabled (uses global)
            String playerKeybind = "crouching"; // Default keybind
            if (component != null) {
                playerKeybindEnabled = component.openJeiKeybind;
                playerKeybind = component.jeiKeybind != null ? component.jeiKeybind : "crouching";
            }
            uiCommandBuilder.set("#Title #HeaderControls #KeybindSettings.Visible", true);
            uiCommandBuilder.set("#Title #HeaderControls #KeybindSettings #EnableKeybindCheckbox #CheckBox.Value", playerKeybindEnabled);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Title #HeaderControls #KeybindSettings #EnableKeybindCheckbox #CheckBox",
                    EventData.of(GuiData.KEY_TOGGLE_KEYBIND, "#Title #HeaderControls #KeybindSettings #EnableKeybindCheckbox #CheckBox.Value"), false);
            
            // Build keybind dropdown
            this.buildKeybindDropdown(ref, uiCommandBuilder, uiEventBuilder, store, playerKeybind);
        } else {
            // Feature is disabled globally - hide checkbox
            uiCommandBuilder.set("#Title #HeaderControls #KeybindSettings.Visible", false);
        }

        // Build item grid and recipe panel - the search query is already set, so filtering will happen
        this.buildItemGrid(ref, uiCommandBuilder, uiEventBuilder, store);
        if (this.selectedItem != null && !this.selectedItem.isEmpty()) {
            this.buildItemInfoPanel(ref, uiCommandBuilder, uiEventBuilder, store);
        } else {
            // Hide section buttons when no item is selected
            uiCommandBuilder.set("#RecipePanel #SectionButtons.Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull GuiData data) {
        super.handleDataEvent(ref, store, data);

        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            this.currentPage = 0; // Reset to first page when searching
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildItemGrid(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.modFilter != null) {
            this.selectedModFilter = data.modFilter;
            this.currentPage = 0; // Reset to first page when filtering
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildItemGrid(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }


        if (data.selectedItem != null && !data.selectedItem.isEmpty()) {
            // Reset pagination when selecting a new item
            if (!data.selectedItem.equals(this.selectedItem)) {
                this.craftPage = 0;
                this.usagePage = 0;
                this.dropsPage = 0;
            }
            this.selectedItem = data.selectedItem;
            this.activeSection = "info";
            this.craftPage = 0;
            this.usagePage = 0;
            this.dropsPage = 0;

            UICommandBuilder commandBuilder = new UICommandBuilder();
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildItemInfoPanel(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.activeSection != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            this.activeSection = data.activeSection;
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();

            Item item = Lumenia.ITEMS.get(this.selectedItem);

            // Re-register event bindings for section buttons
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #InfoButton",
                    EventData.of(GuiData.KEY_ACTIVE_SECTION, "info"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #HowToCraftButton",
                    EventData.of(GuiData.KEY_ACTIVE_SECTION, "craft"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #UsedInButton",
                    EventData.of(GuiData.KEY_ACTIVE_SECTION, "usage"), false);

            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
            if ("info".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
                commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
                commandBuilder.set("#RecipePanel #InfoSection.Visible", true);
                if (item != null) {
                    this.buildInfoSection(ref, commandBuilder, eventBuilder, store, item);
                }
            } else if ("craft".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
                commandBuilder.set("#RecipePanel #InfoSection.Visible", false);
                commandBuilder.set("#RecipePanel #CraftSection.Visible", true);
                List<String> recipeIds = Lumenia.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
            } else if ("usage".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
                commandBuilder.set("#RecipePanel #InfoSection.Visible", false);
                commandBuilder.set("#RecipePanel #UsageSection.Visible", true);
                List<String> usageRecipeIds = this.getUsageRecipeIdsWithResources(this.selectedItem);
                int validUsageCount = usageRecipeIds.size();
                this.buildUsageSection(ref, commandBuilder, eventBuilder, store, usageRecipeIds, validUsageCount);
            }

            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.craftPageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            List<String> recipeIds = Lumenia.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            List<String> validRecipeIds = this.getValidCraftRecipes(recipeIds);

            int totalCraftPages = (int) Math.ceil((double) validRecipeIds.size() / CRAFT_RECIPES_PER_PAGE);
            if ("prev".equals(data.craftPageChange) && this.craftPage > 0) {
                this.craftPage--;
            } else if ("next".equals(data.craftPageChange) && this.craftPage < totalCraftPages - 1) {
                this.craftPage++;
            }

            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.usagePageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            List<String> usageRecipeIds = this.getUsageRecipeIdsWithResources(this.selectedItem);

            int totalUsagePages = (int) Math.ceil((double) usageRecipeIds.size() / USAGE_RECIPES_PER_PAGE);
            if ("prev".equals(data.usagePageChange) && this.usagePage > 0) {
                this.usagePage--;
            } else if ("next".equals(data.usagePageChange) && this.usagePage < totalUsagePages - 1) {
                this.usagePage++;
            }

            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            int validUsageCount = usageRecipeIds.size();
            this.buildUsageSection(ref, commandBuilder, eventBuilder, store, usageRecipeIds, validUsageCount);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.dropsPageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            Map<String, MobDropInfo> itemDrops = Lumenia.MOB_LOOT.get(this.selectedItem);
            if (itemDrops != null && !itemDrops.isEmpty()) {
                List<Map.Entry<String, MobDropInfo>> dropsList = new ArrayList<>(itemDrops.entrySet());
                int totalDropsPages = (int) Math.ceil((double) dropsList.size() / DROPS_PER_PAGE);
                if ("prev".equals(data.dropsPageChange) && this.dropsPage > 0) {
                    this.dropsPage--;
                } else if ("next".equals(data.dropsPageChange) && this.dropsPage < totalDropsPages - 1) {
                    this.dropsPage++;
                }

                UICommandBuilder commandBuilder = new UICommandBuilder();
                UIEventBuilder eventBuilder = new UIEventBuilder();
                this.buildDropsSection(ref, commandBuilder, eventBuilder, store, this.selectedItem);
                this.sendUpdate(commandBuilder, eventBuilder, false);
            }
        }

        if (data.giveItem != null && !data.giveItem.isEmpty()) {
            this.sendUpdate(new UICommandBuilder(), new UIEventBuilder(), false);
            if (!ref.isValid()) {
                return;
            }

            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            GameMode gameMode = player.getGameMode();
            if (gameMode != GameMode.Creative) {
                return;
            }

            Item item = (Item) Item.getAssetMap().getAsset(data.giveItem);
            if (item == null) {
                return;
            }

            try {
                ItemStack stack = new ItemStack(data.giveItem);
                ItemContainer itemContainer = player.getInventory().getCombinedHotbarFirst();
                itemContainer.addItemStack(stack);
            } catch (Exception _) {
            }
        }

        if (data.pageChange != null) {
            if ("prev".equals(data.pageChange) && this.currentPage > 0) {
                this.currentPage--;
            } else if ("next".equals(data.pageChange)) {
                int totalPages = (int) Math.ceil((double) this.visibleItems.size() / ITEMS_PER_PAGE);
                if (this.currentPage < totalPages - 1) {
                    this.currentPage++;
                }
            }
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildItemGrid(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.copyItemId != null && !data.copyItemId.isEmpty()) {
            if (!ref.isValid()) {
                return;
            }

            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            // Send item ID to chat as clickable link
            String itemId = data.copyItemId;
            player.sendMessage(Message.raw("Click to copy: " + itemId).link("https://dontpad.com/itemId=" + itemId).color(java.awt.Color.GREEN));
        }

        if (data.toggleKeybind != null) {
            if (!ref.isValid()) {
                return;
            }

            // Update the player's component with the new keybind state
            LumeniaComponent component = store.getComponent(ref, LumeniaComponent.getComponentType());
            if (component != null) {
                component.openJeiKeybind = data.toggleKeybind;
            } else {
                // Create component if it doesn't exist
                component = new LumeniaComponent(data.toggleKeybind, "crouching");
                store.addComponent(ref, LumeniaComponent.getComponentType(), component);
            }
            
            // Update dropdown visibility
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            String currentKeybind = component != null && component.jeiKeybind != null ? component.jeiKeybind : "walking";
            this.buildKeybindDropdown(ref, commandBuilder, eventBuilder, store, currentKeybind);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.keybindSelection != null && !data.keybindSelection.isEmpty()) {
            if (!ref.isValid()) {
                return;
            }

            // Update the player's component with the new keybind selection
            LumeniaComponent component = store.getComponent(ref, LumeniaComponent.getComponentType());
            if (component != null) {
                component.jeiKeybind = data.keybindSelection;
            } else {
                // Create component if it doesn't exist
                boolean keybindEnabled = Lumenia.getInstance().config.get().defaultOpenJeiKeybind;
                component = new LumeniaComponent(keybindEnabled, data.keybindSelection != null ? data.keybindSelection : "crouching");
                store.addComponent(ref, LumeniaComponent.getComponentType(), component);
            }
        }

    }

    private void buildKeybindDropdown(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                     @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                     String currentKeybind) {
        List<DropdownEntryInfo> keybindEntries = new ArrayList<>();
        
        // Add available keybind options
        keybindEntries.add(new DropdownEntryInfo(LocalizableString.fromString("Walk"), "walking"));
        keybindEntries.add(new DropdownEntryInfo(LocalizableString.fromString("Crouch"), "crouching"));
        
        commandBuilder.set("#Title #HeaderControls #KeybindSettings #KeybindDropdown.Entries", keybindEntries);
        // Ensure current keybind is valid, default to "crouching" if not
        String validKeybind = currentKeybind != null && (currentKeybind.equals("walking") || currentKeybind.equals("crouching")) 
                ? currentKeybind : "crouching";
        commandBuilder.set("#Title #HeaderControls #KeybindSettings #KeybindDropdown.Value", validKeybind);
        
        // Show dropdown only if keybind is enabled
        LumeniaComponent component = store.getComponent(ref, LumeniaComponent.getComponentType());
        boolean keybindEnabled = true;
        if (component != null) {
            keybindEnabled = component.openJeiKeybind;
        }
        commandBuilder.set("#Title #HeaderControls #KeybindSettings #KeybindDropdown.Visible", keybindEnabled);
        
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Title #HeaderControls #KeybindSettings #KeybindDropdown",
                EventData.of(GuiData.KEY_KEYBIND_SELECTION, "#Title #HeaderControls #KeybindSettings #KeybindDropdown.Value"), false);
    }

    private void buildModFilterDropdown(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                       @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        List<DropdownEntryInfo> modEntries = new ArrayList<>();
        
        // Add "All Mods" option
        modEntries.add(new DropdownEntryInfo(LocalizableString.fromString("All Mods"), ""));
        
        // Get all unique mods/namespaces from items
        Set<String> modNames = new HashSet<>();
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        
        for (String itemId : Lumenia.ITEMS.keySet()) {
            String modName = this.getModNameForItem(itemId, assetMap);
            if (modName != null && !modName.isEmpty()) {
                modNames.add(modName);
            }
        }
        
        // Sort mod names and add to dropdown
        List<String> sortedModNames = new ArrayList<>(modNames);
        sortedModNames.sort(String.CASE_INSENSITIVE_ORDER);
        
        for (String modName : sortedModNames) {
            modEntries.add(new DropdownEntryInfo(LocalizableString.fromString(modName), modName));
        }
        
        commandBuilder.set("#Title #HeaderControls #ModFilterDropdown.Entries", modEntries);
        if (this.selectedModFilter != null) {
            commandBuilder.set("#Title #HeaderControls #ModFilterDropdown.Value", this.selectedModFilter);
        } else {
            commandBuilder.set("#Title #HeaderControls #ModFilterDropdown.Value", "");
        }
        
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Title #HeaderControls #ModFilterDropdown",
                EventData.of(GuiData.KEY_MOD_FILTER, "#Title #HeaderControls #ModFilterDropdown.Value"), false);
    }

    private String getModNameForItem(String itemId, DefaultAssetMap<String, Item> assetMap) {
        try {
            if (assetMap == null) {
                // Fallback: check namespace in itemId
                int colonIndex = itemId.indexOf(':');
                if (colonIndex > 0) {
                    String namespace = itemId.substring(0, colonIndex);
                    if (namespace.equalsIgnoreCase("core") || namespace.equalsIgnoreCase("hytale")) {
                        return "Vanilla";
                    }
                    return namespace;
                }
                return "Vanilla";
            }

            // Get the pack name that contains this item
            String packName = assetMap.getAssetPack(itemId);
            if (packName == null || packName.isEmpty()) {
                // Fallback: check namespace in itemId
                int colonIndex = itemId.indexOf(':');
                if (colonIndex > 0) {
                    String namespace = itemId.substring(0, colonIndex);
                    if (namespace.equalsIgnoreCase("core") || namespace.equalsIgnoreCase("hytale")) {
                        return "Vanilla";
                    }
                    return namespace;
                }
                return "Vanilla";
            }

            // Get the AssetPack object
            AssetPack pack = AssetModule.get().getAssetPack(packName);
            if (pack == null) {
                // Fallback to pack name
                return packName;
            }

            // Check if it's the base pack (vanilla)
            AssetPack basePack = AssetModule.get().getBaseAssetPack();
            if (pack.equals(basePack)) {
                return "Vanilla";
            }

            // Get the mod name from manifest
            try {
                PluginManifest manifest = pack.getManifest();
                if (manifest != null) {
                    String modName = manifest.getName();
                    if (modName != null && !modName.isEmpty()) {
                        return modName;
                    }
                }
            } catch (Exception e) {
                // Ignore manifest errors
            }

            // Fallback to pack name
            return packName;
        } catch (Exception e) {
            return "Vanilla";
        }
    }

    private void buildItemGrid(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                               @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        // Clear the grid first to remove old items
        commandBuilder.clear("#ItemGrid");

        Map<String, Item> itemList = new HashMap<>(Lumenia.ITEMS);
        ComponentAccessor<EntityStore> componentAccessor = store;
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());

        assert playerComponent != null;

        // Filter items by mod/namespace if a filter is selected
        if (this.selectedModFilter != null && !this.selectedModFilter.isEmpty()) {
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            Map<String, Item> filteredByMod = new HashMap<>();
            for (Map.Entry<String, Item> entry : itemList.entrySet()) {
                String modName = this.getModNameForItem(entry.getKey(), assetMap);
                if (modName != null && modName.equals(this.selectedModFilter)) {
                    filteredByMod.put(entry.getKey(), entry.getValue());
                }
            }
            itemList = filteredByMod;
        }

        // Filter items based on search query
        if (!this.searchQuery.isEmpty()) {
            this.visibleItems.clear();
            ObjectArrayList<SearchResult> results = new ObjectArrayList<>();

            for (Map.Entry<String, Item> entry : itemList.entrySet()) {
                if (entry.getValue() != null) {
                    results.add(new SearchResult(entry.getKey(), MatchResult.EXACT));
                }
            }

            String[] terms = this.searchQuery.split(" ");

            for (String term : terms) {
                term = term.toLowerCase(Locale.ENGLISH);

                for (int cmdIndex = results.size() - 1; cmdIndex >= 0; --cmdIndex) {
                    SearchResult result = results.get(cmdIndex);
                    Item item = itemList.get(result.name);
                    MatchResult match = MatchResult.NONE;
                    if (item != null) {
                        // Search in item name (translation)
                        var message = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
                        if (message != null && message.toLowerCase(Locale.ENGLISH).contains(term)) {
                            match = MatchResult.EXACT;
                        }

                        // Search in item ID if name didn't match
                        if (match == MatchResult.NONE) {
                            String itemId = result.name.toLowerCase(Locale.ENGLISH);
                            if (itemId.toLowerCase().contains(term)) {
                                match = MatchResult.EXACT;
                            }
                        }

                        // Search in item group/type if still no match
                        if (match == MatchResult.NONE) {
                            String itemGroup = getItemGroupString(item).toLowerCase(Locale.ENGLISH);
                            if (itemGroup.toLowerCase().contains(term)) {
                                match = MatchResult.EXACT;
                            }
                        }

                        if (match == MatchResult.NONE && item.getResourceTypes() != null && item.getResourceTypes().length > 0) {
                            try {
                                String finalTerm = term;
                                if (Arrays.stream(item.getResourceTypes())
                                        .filter(Objects::nonNull)
                                        .anyMatch(s -> s.id != null && s.id.toLowerCase().contains(finalTerm))) {
                                    match = MatchResult.EXACT;
                                }
                            } catch (Exception _) {
                            }
                        }
                    }

                    if (match == MatchResult.NONE) {
                        results.remove(cmdIndex);
                    } else {
                        result.match = result.match.min(match);
                    }
                }
            }

            results.sort(SearchResult.COMPARATOR);
            this.visibleItems.clear();

            for (SearchResult result : results) {
                this.visibleItems.put(result.name, itemList.get(result.name));
            }
        } else {
            this.visibleItems.clear();
            this.visibleItems.putAll(itemList);
        }


        // Pagination: Calculate which items to show
        List<Map.Entry<String, Item>> allItems = new ArrayList<>(this.visibleItems.entrySet());
        int totalPages = (int) Math.ceil((double) allItems.size() / ITEMS_PER_PAGE);
        if (this.currentPage >= totalPages && totalPages > 0) {
            this.currentPage = totalPages - 1;
        }
        if (this.currentPage < 0) {
            this.currentPage = 0;
        }

        int startIndex = this.currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allItems.size());
        List<Map.Entry<String, Item>> pageItems = allItems.subList(startIndex, endIndex);

        // Build item grid (5 items per row)
        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (Map.Entry<String, Item> entry : pageItems) {
            if (cardsInCurrentRow == 0) {
                commandBuilder.appendInline("#ItemGrid", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            commandBuilder.append("#ItemGrid[" + rowIndex + "]", "Pages/Lumengrid_Lumenia_ItemIcon.ui");
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", entry.getKey());
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.Visible", true);
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ResourceIcon.AssetPath", "");
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ResourceIcon.Visible", false);
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemName.TextSpans", Message.translation(entry.getValue().getTranslationKey()));
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemId.Text", entry.getKey());

            // Add event binding for clicking on item ID
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemId",
                    EventData.of(GuiData.KEY_COPY_ITEM_ID, entry.getKey()), false);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "]",
                    EventData.of(GuiData.KEY_SELECTED_ITEM, entry.getKey()), false);

            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= 5) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }

        // Add pagination controls - always show buttons if more than 1 page
        if (totalPages > 1) {
            commandBuilder.set("#PaginationControls #PaginationInfo.Visible", true);
            commandBuilder.set("#PaginationControls #PaginationInfo.Text", (this.currentPage + 1) + " / " + totalPages + " (" + allItems.size() + " items)");

            // Always show previous button if more than 1 page
            commandBuilder.set("#PaginationControls #PrevPageButton.Visible", true);
            if (this.currentPage > 0) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PaginationControls #PrevPageButton",
                        EventData.of("PageChange", "prev"), false);
            }

            // Always show next button if more than 1 page
            commandBuilder.set("#PaginationControls #NextPageButton.Visible", true);
            if (this.currentPage < totalPages - 1) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PaginationControls #NextPageButton",
                        EventData.of("PageChange", "next"), false);
            }
        } else {
            commandBuilder.set("#PaginationControls #PaginationInfo.Visible", false);
            commandBuilder.set("#PaginationControls #PrevPageButton.Visible", false);
            commandBuilder.set("#PaginationControls #NextPageButton.Visible", false);
        }
    }

    private String getItemGroupString(Item item) {
        if (item.getTool() != null) return "Tool";
        if (item.getWeapon() != null) return "Weapon";
        if (item.getArmor() != null) return "Armor";
        if (item.getGlider() != null) return "Glider";
        if (item.getUtility() != null) return "Utility";
        if (item.getPortalKey() != null) return "PortalKey";
        if (item.hasBlockType()) return "Block";
        return "Item";
    }

    private void buildItemInfoPanel(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                    @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.set("#RecipePanel.Visible", true);
        commandBuilder.clear("#RecipePanel #CraftSection #CraftList");
        commandBuilder.set("#RecipePanel #NoRecipes.Visible", false);

        if (this.selectedItem == null || this.selectedItem.isEmpty()) {
            commandBuilder.set("#RecipePanel #ItemInfo.Visible", false);
            commandBuilder.set("#RecipePanel #SectionButtons.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
            commandBuilder.set("#RecipePanel #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #NoRecipes.Text", "Select an item to view recipes.");
            return;
        }

        Item item = Lumenia.ITEMS.get(this.selectedItem);
        if (item == null) {
            commandBuilder.set("#RecipePanel #ItemInfo.Visible", false);
            commandBuilder.set("#RecipePanel #SectionButtons.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
            commandBuilder.set("#RecipePanel #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #NoRecipes.Text", "Item not found.");
            return;
        }

        // Display item information (only icon, name, ID, and optional give button)
        commandBuilder.set("#RecipePanel #ItemInfo.Visible", true);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", "");
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", this.selectedItem);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.Visible", true);
        commandBuilder.set("#RecipePanel #ItemInfo #ResourceIcon.AssetPath", "");
        commandBuilder.set("#RecipePanel #ItemInfo #ResourceIcon.Visible", false);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemName.TextSpans", Message.translation(item.getTranslationKey()).bold(true));
        commandBuilder.set("#RecipePanel #ItemInfo #ItemId.Text", this.selectedItem);

        // Add event binding for clicking on item ID in info panel
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#RecipePanel #ItemInfo #ItemId",
                EventData.of(GuiData.KEY_COPY_ITEM_ID, this.selectedItem), false);

        // Hide item properties in the header (they will be shown in Info section)
        commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties.Visible", false);

        this.activeSection = "info";
        this.craftPage = 0;
        this.usagePage = 0;
        this.dropsPage = 0;

        commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
        commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
        commandBuilder.set("#RecipePanel #InfoSection.Visible", true);

        commandBuilder.set("#RecipePanel #SectionButtons.Visible", true);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #InfoButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "info"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #HowToCraftButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "craft"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #UsedInButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "usage"), false);

        // Build info section with item properties
        this.buildInfoSection(ref, commandBuilder, eventBuilder, store, item);

        // Check if player is in creative mode and show/hide give item button
        ComponentAccessor<EntityStore> componentAccessor = store;
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
        boolean isCreative = false;
        if (playerComponent != null) {
            try {
                Object gameModeObj = playerComponent.getGameMode();
                if (gameModeObj != null) {
                    isCreative = gameModeObj == GameMode.Creative;
                }
            } catch (Exception _) {
            }
        }

        if (isCreative) {
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #MaxStackRow #GiveItemButton.Visible", true);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #InfoSection #ItemPropertiesInfo #MaxStackRow #GiveItemButton",
                    EventData.of(GuiData.KEY_GIVE_ITEM, this.selectedItem), false);
        } else {
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #MaxStackRow #GiveItemButton.Visible", false);
        }

        this.buildInfoSection(ref, commandBuilder, eventBuilder, store, item);
    }

    private void buildInfoSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                  @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                  @Nonnull Item item) {
        commandBuilder.clear("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList");

        int propIndex = 0;

        try {
            String originInfo = this.resolveItemOrigin(this.selectedItem);
            if (!originInfo.isEmpty()) {
                commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
                commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Origin: " + originInfo);
                propIndex++;
            }
        } catch (Exception _) {
        }

        String maxStackText = "Max Stack: " + item.getMaxStack();
        commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #MaxStackRow #MaxStackLabel.Text", maxStackText);

        try {
            java.lang.reflect.Method getQualityIndexMethod = Item.class.getMethod("getQualityIndex");
            Object qualityObj = getQualityIndexMethod.invoke(item);
            if (qualityObj != null) {
                ItemQuality itemQuality = ItemQuality.getAssetMap().getAsset((Integer) qualityObj);
                if (itemQuality != null) {
                    commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
                    try {
                        int rgb = ColorParseUtil.colorToARGBInt(itemQuality.getTextColor());
                        java.awt.Color color = new java.awt.Color(rgb);
                        commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].TextSpans",
                                Message.raw("Quality: ").insert(Message.translation(itemQuality.getLocalizationKey()).color(color)));
                    } catch (Exception e) {
                        Lumenia.LOGGER.atSevere().log("Lumenia: buildInfoSection: " + e.getMessage(), e);
                        String qual = I18nModule.get().getMessage(this.playerRef.getLanguage(), itemQuality.getLocalizationKey());
                        if (qual == null || qual.isEmpty()) {
                            qual = itemQuality.getLocalizationKey();
                        }
                        commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Quality: " + qual);
                    }
                    propIndex++;
                }

            }
        } catch (Exception _) {
        }

        if (item.getItemLevel() > 0) {
            commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Item Level: " + item.getItemLevel());
            propIndex++;
        }

        if (item.getMaxDurability() > 0) {
            commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Max Durability: " + item.getMaxDurability());
            propIndex++;
        }

        try {
            java.lang.reflect.Method isConsumableMethod = Item.class.getMethod("isConsumable");
            Object consumableObj = isConsumableMethod.invoke(item);
            if (consumableObj != null) {
                commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
                commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Is Consumable: " + this.formatBoolean((Boolean) consumableObj));
                propIndex++;
            }
        } catch (Exception _) {
        }

        try {
            java.lang.reflect.Method getFuelQualityMethod = Item.class.getMethod("getFuelQuality");
            Object fuelQualityObj = getFuelQualityMethod.invoke(item);
            if (fuelQualityObj != null) {
                int fuelQuality = (Integer) fuelQualityObj;
                if (fuelQuality > 0) {
                    commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
                    commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Fuel Quality: " + fuelQuality);
                    propIndex++;
                }
            }
        } catch (Exception _) {
        }

        // Display resource types
        try {
            ItemResourceType[] resourceTypes = item.getResourceTypes();
            if (resourceTypes != null && resourceTypes.length > 0) {
                List<String> resourceTypeIds = new ArrayList<>();
                for (ItemResourceType resourceType : resourceTypes) {
                    if (resourceType != null && resourceType.id != null && !resourceType.id.isEmpty()) {
                        resourceTypeIds.add(resourceType.id);
                    }
                }
                if (!resourceTypeIds.isEmpty()) {
                    commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
                    String resourceTypesText = "Resource Types: " + String.join(", ", resourceTypeIds);
                    commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", resourceTypesText);
                    propIndex++;
                }
            }
        } catch (Exception _) {
        }

        commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa); Anchor: (Height: 5); }");
        commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "");
        propIndex++;


        if (item.getTool() != null) {
            commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Is Tool: " + this.formatBoolean(item.getTool() != null));
            propIndex++;
        }

        if (item.getWeapon() != null) {
            commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Is Weapon: " + this.formatBoolean(item.getWeapon() != null));
            propIndex++;
        }

        if (item.getArmor() != null) {
            commandBuilder.appendInline("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList", "Label { Style: (FontSize: 14, TextColor: #aaaaaa, Wrap: true); }");
            commandBuilder.set("#RecipePanel #InfoSection #ItemPropertiesInfo #ItemPropertiesList[" + propIndex + "].Text", "Is Armor: " + this.formatBoolean(item.getArmor() != null));
            propIndex++;
        }

        // Build drops section if item has drops
        Map<String, MobDropInfo> itemDrops = Lumenia.MOB_LOOT.get(this.selectedItem);
        if (itemDrops != null && !itemDrops.isEmpty()) {
            this.buildDropsSection(ref, commandBuilder, eventBuilder, store, this.selectedItem);
        } else {
            commandBuilder.set("#RecipePanel #InfoSection #DropsSection.Visible", false);
        }
    }

    private String formatBoolean(boolean value) {
        return value ? "true" : "false";
    }

    private void buildDropsSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                   @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                   String itemId) {
        Map<String, MobDropInfo> itemDrops = Lumenia.MOB_LOOT.get(itemId);
        
        if (itemDrops == null || itemDrops.isEmpty()) {
            commandBuilder.set("#RecipePanel #InfoSection #DropsSection.Visible", false);
            return;
        }

        commandBuilder.set("#RecipePanel #InfoSection #DropsSection.Visible", true);
        commandBuilder.clear("#RecipePanel #InfoSection #DropsSection #DropsList");

        // Convert to list for pagination and sort by translation key
        List<Map.Entry<String, MobDropInfo>> dropsList = new ArrayList<>(itemDrops.entrySet());
        dropsList.sort((a, b) -> {
            try {
                String nameA = I18nModule.get().getMessage(this.playerRef.getLanguage(), a.getValue().roleTranslationKey);
                String nameB = I18nModule.get().getMessage(this.playerRef.getLanguage(), b.getValue().roleTranslationKey);
                if (nameA == null) nameA = a.getValue().roleTranslationKey;
                if (nameB == null) nameB = b.getValue().roleTranslationKey;
                return nameA.compareToIgnoreCase(nameB);
            } catch (Exception e) {
                return a.getValue().roleTranslationKey.compareToIgnoreCase(b.getValue().roleTranslationKey);
            }
        });
        
        int totalDropsPages = (int) Math.ceil((double) dropsList.size() / DROPS_PER_PAGE);
        if (this.dropsPage >= totalDropsPages && totalDropsPages > 0) {
            this.dropsPage = totalDropsPages - 1;
        }
        if (this.dropsPage < 0) {
            this.dropsPage = 0;
        }

        int startIndex = this.dropsPage * DROPS_PER_PAGE;
        int endIndex = Math.min(startIndex + DROPS_PER_PAGE, dropsList.size());
        List<Map.Entry<String, MobDropInfo>> pageDrops = dropsList.subList(startIndex, endIndex);

        // Update pagination controls
        if (totalDropsPages > 1) {
            commandBuilder.set("#RecipePanel #InfoSection #DropsSection #DropsPaginationControls.Visible", true);
            commandBuilder.set("#RecipePanel #InfoSection #DropsSection #DropsPaginationControls #DropsPaginationInfo.Text", 
                    (this.dropsPage + 1) + " / " + totalDropsPages);
            
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, 
                    "#RecipePanel #InfoSection #DropsSection #DropsPaginationControls #DropsPrevPageButton",
                    EventData.of(GuiData.KEY_DROPS_PAGE_CHANGE, "prev"), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, 
                    "#RecipePanel #InfoSection #DropsSection #DropsPaginationControls #DropsNextPageButton",
                    EventData.of(GuiData.KEY_DROPS_PAGE_CHANGE, "next"), false);
        } else {
            commandBuilder.set("#RecipePanel #InfoSection #DropsSection #DropsPaginationControls.Visible", false);
        }

        // Display drops for current page
        int dropIndex = 0;
        for (Map.Entry<String, MobDropInfo> entry : pageDrops) {
            MobDropInfo dropInfo = entry.getValue();
            String roleId = dropInfo.roleId;
            String roleTranslationKey = dropInfo.roleTranslationKey;
            String modelId = dropInfo.modelId;
            Map.Entry<Integer, Integer> quantities = dropInfo.quantities;
            int min = quantities.getKey();
            int max = quantities.getValue();
            
            String quantityText;
            if (min == max) {
                quantityText = "x" + min;
            } else {
                quantityText = "x" + min + " - " + max;
            }

            // Create entry for each drop
            String dropEntrySelector = "#RecipePanel #InfoSection #DropsSection #DropsList[" + dropIndex + "]";
            commandBuilder.appendInline("#RecipePanel #InfoSection #DropsSection #DropsList", 
                    "Group { LayoutMode: Top; Padding: (Full: 8, Bottom: 10); }");
            
            // First row: Mob name and quantity on the same line
            String nameRowSelector = dropEntrySelector + "[0]";
            commandBuilder.appendInline(dropEntrySelector, 
                    "Group { LayoutMode: Left; Anchor: (Height: 24); }");

            // Mob name (using translation key) - takes available space
            commandBuilder.appendInline(nameRowSelector, 
                    "Label { FlexWeight: 1; Style: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center); }");
            commandBuilder.set(nameRowSelector + "[0].TextSpans", 
                    Message.translation(roleTranslationKey));
            
            // Quantity on the same row
            commandBuilder.appendInline(nameRowSelector,
                    "Label { Style: (FontSize: 13, TextColor: #4a9eff, VerticalAlignment: Center); Padding: (Left: 10); }");
            commandBuilder.set(nameRowSelector + "[1].Text", quantityText);

            // Second row: Mob ID (role ID, not translation key)
            String mobIdSelector = dropEntrySelector + "[1]";
            commandBuilder.appendInline(dropEntrySelector,
                    "Label { Style: (FontSize: 12, TextColor: #888888, VerticalAlignment: Center); Padding: (Top: 2); }");
            commandBuilder.set(mobIdSelector + ".Text", roleId);
            
            // Optional: Show model ID if available (third row)
            if (modelId != null && !modelId.isEmpty()) {
                String modelIdSelector = dropEntrySelector + "[2]";
                commandBuilder.appendInline(dropEntrySelector,
                        "Label { Style: (FontSize: 11, TextColor: #666666, VerticalAlignment: Center); Padding: (Top: 2); }");
                commandBuilder.set(modelIdSelector + ".Text", "Model: " + modelId);
            }
            
            dropIndex++;
        }
    }

    private void buildCraftSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                   @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                   List<String> recipeIds) {
        commandBuilder.clear("#RecipePanel #CraftSection #CraftList");
        commandBuilder.set("#RecipePanel #CraftSection.Visible", true);

        List<String> validRecipeIds = this.getValidCraftRecipes(recipeIds);

        if (validRecipeIds.isEmpty()) {
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Text", "No recipes available for this item.");
            commandBuilder.set("#RecipePanel #CraftSection #CraftList.Visible", false);
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        } else {
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection #CraftList.Visible", true);

            int totalCraftPages = (int) Math.ceil((double) validRecipeIds.size() / CRAFT_RECIPES_PER_PAGE);
            if (this.craftPage >= totalCraftPages && totalCraftPages > 0) {
                this.craftPage = totalCraftPages - 1;
            }
            if (this.craftPage < 0) {
                this.craftPage = 0;
            }

            int startIndex = this.craftPage * CRAFT_RECIPES_PER_PAGE;
            int endIndex = Math.min(startIndex + CRAFT_RECIPES_PER_PAGE, validRecipeIds.size());
            List<String> pageRecipeIds = validRecipeIds.subList(startIndex, endIndex);

            this.updatePaginationControls(commandBuilder, eventBuilder, totalCraftPages, validRecipeIds.size(), this.craftPage, "craft");

            int recipeIndex = 0;
            for (String recipeId : pageRecipeIds) {
                CraftingRecipe recipe = Lumenia.RECIPES.get(recipeId);
                if (recipe == null) {
                    continue;
                }

                try {
                    commandBuilder.append("#RecipePanel #CraftSection #CraftList", "Pages/Lumengrid_Lumenia_RecipeDisplay.ui");
                    this.buildRecipeDisplay(commandBuilder, eventBuilder, recipe, "#RecipePanel #CraftSection #CraftList", recipeIndex);
                    ++recipeIndex;
                } catch (Exception _) {
                }
            }
        }
    }

    private void buildUsageSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                   @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                   List<String> usageRecipeIds, int validUsageCount) {
        commandBuilder.clear("#RecipePanel #UsageSection #UsageList");
        commandBuilder.set("#RecipePanel #UsageSection.Visible", true);

        if (usageRecipeIds.isEmpty()) {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Text", "This item is not used in any recipes.");
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", false);
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        } else {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", true);

            int totalUsagePages = (int) Math.ceil((double) usageRecipeIds.size() / USAGE_RECIPES_PER_PAGE);
            if (this.usagePage >= totalUsagePages && totalUsagePages > 0) {
                this.usagePage = totalUsagePages - 1;
            }
            if (this.usagePage < 0) {
                this.usagePage = 0;
            }

            int startIndex = this.usagePage * USAGE_RECIPES_PER_PAGE;
            int endIndex = Math.min(startIndex + USAGE_RECIPES_PER_PAGE, usageRecipeIds.size());
            List<String> pageRecipeIds = usageRecipeIds.subList(startIndex, endIndex);

            this.updatePaginationControls(commandBuilder, eventBuilder, totalUsagePages, usageRecipeIds.size(), this.usagePage, "usage");

            int recipeIndex = 0;
            for (String recipeId : pageRecipeIds) {
                CraftingRecipe recipe = Lumenia.RECIPES.get(recipeId);
                if (recipe == null) {
                    continue;
                }

                try {
                    commandBuilder.append("#RecipePanel #UsageSection #UsageList", "Pages/Lumengrid_Lumenia_RecipeDisplay.ui");
                    this.buildRecipeDisplay(commandBuilder, eventBuilder, recipe, "#RecipePanel #UsageSection #UsageList", recipeIndex);
                    ++recipeIndex;
                } catch (Exception _) {
                }
            }
        }
    }


    private List<String> getValidCraftRecipes(List<String> recipeIds) {
        List<String> validRecipeIds = new ArrayList<>();
        for (String recipeId : recipeIds) {
            CraftingRecipe recipe = Lumenia.RECIPES.get(recipeId);
            if (recipe == null) {
                continue;
            }

            boolean itemFoundInOutputs = false;
            for (MaterialQuantity output : recipe.getOutputs()) {
                if (output == null) {
                    continue;
                }
                if (output != null && this.selectedItem.equals(output.getItemId())) {
                    itemFoundInOutputs = true;
                    break;
                }
            }
            if (itemFoundInOutputs) {
                validRecipeIds.add(recipeId);
            }
        }
        return validRecipeIds;
    }

    /**
     * Gets all usage recipe IDs for an item, including recipes that use items from the same ResourceType
     * Returns a distinct list of recipe IDs (no duplicates)
     */
    private List<String> getUsageRecipeIdsWithResources(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Collections.emptyList();
        }

        // Use a LinkedHashSet to avoid duplicate recipe IDs while preserving insertion order
        Set<String> allRecipeIds = new LinkedHashSet<>();

        // First, add recipes that directly use this item
        List<String> directRecipes = Lumenia.ITEM_FROM_RECIPES.getOrDefault(itemId, Collections.emptyList());
        allRecipeIds.addAll(directRecipes);

        Item item = Lumenia.ITEMS.get(itemId);
        if (item != null) {
            try {
                // Get item categories
                ItemResourceType[] resources = item.getResourceTypes();
                if (resources != null) {
                    for (ItemResourceType resource : resources) {
                        try {
                            if (resource.id != null && !resource.id.isEmpty()) {
                                List<String> recipes = Lumenia.ITEM_FROM_RECIPES.getOrDefault(resource.id, Collections.emptyList());
                                allRecipeIds.addAll(recipes);
                            }
                        } catch (Exception _) {
                        }
                    }
                }
            } catch (Exception _) {
            }
        }

        // Return as distinct list (LinkedHashSet already ensures no duplicates)
        return new ArrayList<>(allRecipeIds);
    }

    private List<String> getValidUsageRecipes(List<String> usageRecipeIds) {
        List<String> validUsageRecipeIds = new ArrayList<>();
        for (String usageRecipeId : usageRecipeIds) {
            var usageRecipe = Lumenia.RECIPES.get(usageRecipeId);
            if (usageRecipe == null) continue;

            Object inputsObj = null;
            try {
                Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
                inputsObj = getInputMethod.invoke(usageRecipe);
            } catch (NoSuchMethodException e) {
                String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
                for (String methodName : methodNames) {
                    try {
                        Method inputMethod = CraftingRecipe.class.getMethod(methodName);
                        inputsObj = inputMethod.invoke(usageRecipe);
                        if (inputsObj != null) break;
                    } catch (Exception _) {
                    }
                }
            } catch (Exception _) {
            }

            boolean itemFoundInInputs = false;
            if (inputsObj != null) {
                if (inputsObj instanceof MaterialQuantity) {
                    MaterialQuantity input = (MaterialQuantity) inputsObj;
                    if (input != null && this.selectedItem.equals(input.getItemId())) {
                        itemFoundInInputs = true;
                    }
                } else if (inputsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                    if (inputs != null && !inputs.isEmpty()) {
                        for (MaterialQuantity input : inputs) {
                            if (input != null && this.selectedItem.equals(input.getItemId())) {
                                itemFoundInInputs = true;
                                break;
                            }
                        }
                    }
                } else if (inputsObj instanceof MaterialQuantity[]) {
                    MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                    if (inputs != null && inputs.length > 0) {
                        for (MaterialQuantity input : inputs) {
                            if (input != null && this.selectedItem.equals(input.getItemId())) {
                                itemFoundInInputs = true;
                                break;
                            }
                        }
                    }
                } else if (inputsObj instanceof Collection) {
                    @SuppressWarnings("unchecked")
                    Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                    if (inputs != null && !inputs.isEmpty()) {
                        for (MaterialQuantity input : inputs) {
                            if (input != null && this.selectedItem.equals(input.getItemId())) {
                                itemFoundInInputs = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (itemFoundInInputs) {
                validUsageRecipeIds.add(usageRecipeId);
            }
        }
        return validUsageRecipeIds;
    }

    private int countValidUsageRecipes(List<String> usageRecipeIds) {
        return this.getValidUsageRecipes(usageRecipeIds).size();
    }

    private void buildRecipeDisplay(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder,
                                    @Nonnull CraftingRecipe recipe, @Nonnull String listSelector, int recipeIndex) {
        String recipeSelector = listSelector + "[" + recipeIndex + "]";
        String contentSelector = recipeSelector + " #RecipeContentContainer #RecipeContent";

        commandBuilder.set(recipeSelector + " #RecipeTitle.Visible", false);

        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            String benchName = this.formatBench(bench.id);
            commandBuilder.set(contentSelector + " #BenchInfo.Visible", true);
            commandBuilder.set(contentSelector + " #BenchInfo #BenchText.Text", benchName + " Tier " + bench.requiredTierLevel);
            commandBuilder.set(contentSelector + " #BenchInfo #BenchId.Text", bench.id);

            String benchItemId = this.findBenchItemId(bench.id);
            if (benchItemId != null && Lumenia.ITEMS.containsKey(benchItemId)) {
                commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.ItemId", "");
                commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.ItemId", benchItemId);
                commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", true);
            } else {
                commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", false);
            }
        } else {
            commandBuilder.set(contentSelector + " #BenchInfo.Visible", true);
            commandBuilder.set(contentSelector + " #BenchInfo #BenchText.Text", "Crafted in: Hand");
            commandBuilder.set(contentSelector + " #BenchInfo #BenchId.Text", "");
            commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", false);
        }

        Object inputsObj = null;
        try {
            Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (NoSuchMethodException e) {
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
            for (String methodName : methodNames) {
                try {
                    Method inputMethod = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = inputMethod.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (NoSuchMethodException ignored) {
                } catch (Exception ignored) {
                }
            }
        } catch (Exception _) {
        }

        int inputIndex = 0;
        String inputGridSelector = contentSelector + " #InputGrid";

        boolean hasInputs = false;
        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                hasInputs = input != null && input.getItemId() != null;
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                hasInputs = inputs != null && !inputs.isEmpty();
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                hasInputs = inputs != null && inputs.length > 0;
            } else if (inputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                hasInputs = inputs != null && !inputs.isEmpty();
            }
        }
        commandBuilder.set(contentSelector + " #InputLabel.Visible", hasInputs);

        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null) {
                    this.addIngredientItem(commandBuilder, eventBuilder, inputGridSelector, input, inputIndex++);
                }
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            this.addIngredientItem(commandBuilder, eventBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                if (inputs != null && inputs.length > 0) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            this.addIngredientItem(commandBuilder, eventBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            } else if (inputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            this.addIngredientItem(commandBuilder, eventBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            }
        }

        if (inputIndex == 0) {
            commandBuilder.appendInline(inputGridSelector, "Label { Style: (FontSize: 12, TextColor: #888888); }");
            commandBuilder.set(inputGridSelector + "[0].Text", "(Ingredients not available - API may not expose recipe inputs)");
        }

        Object outputsObj = recipe.getOutputs();
        int outputIndex = 0;
        String outputGridSelector = contentSelector + " #OutputGrid";

        commandBuilder.set(contentSelector + " #OutputLabel.Visible", true);

        if (outputsObj != null) {
            if (outputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> outputs = (List<MaterialQuantity>) outputsObj;
                if (outputs != null && !outputs.isEmpty()) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, eventBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            } else if (outputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] outputs = (MaterialQuantity[]) outputsObj;
                if (outputs != null && outputs.length > 0) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, eventBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            } else if (outputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<MaterialQuantity> outputs = (Collection<MaterialQuantity>) outputsObj;
                if (outputs != null && !outputs.isEmpty()) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, eventBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            }
        }

        if (outputIndex == 0) {
            commandBuilder.set(contentSelector + " #OutputLabel.Visible", false);
        }
    }

    private void addIngredientItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder,
                                   @Nonnull String inputGridSelector, @Nonnull MaterialQuantity input, int inputIndex) {
        commandBuilder.append(inputGridSelector, "Pages/Lumengrid_Lumenia_RecipeInputItem.ui");

        String itemId = input.getItemId();
        String resourceType = null;

        if (itemId == null || itemId.isEmpty()) {
            try {
                java.lang.reflect.Method getResourceTypeMethod = MaterialQuantity.class.getMethod("getResourceType");
                Object resourceTypeObj = getResourceTypeMethod.invoke(input);
                if (resourceTypeObj != null) {
                    resourceType = resourceTypeObj.toString();
                }
            } catch (Exception e) {
                try {
                    String[] methodNames = {"getResourceTypeId", "getResourceTypeName", "getType"};
                    for (String methodName : methodNames) {
                        try {
                            java.lang.reflect.Method method = MaterialQuantity.class.getMethod(methodName);
                            Object result = method.invoke(input);
                            if (result != null) {
                                resourceType = result.toString();
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if ((itemId == null || itemId.isEmpty()) && (resourceType == null || resourceType.isEmpty())) {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", false);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.AssetPath", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.Visible", false);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemName.Text", "Unknown Material");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #Quantity.Text", "x" + input.getQuantity());
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", "ID: (unknown)");
            return;
        }

        String displayId = (itemId != null && !itemId.isEmpty()) ? itemId : resourceType;
        String displayType = (itemId != null && !itemId.isEmpty()) ? "" : "Resource Type: ";

        if (itemId != null && !itemId.isEmpty()) {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", itemId);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", true);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.AssetPath", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.Visible", false);
        } else {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", false);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.AssetPath", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.Visible", false);
            ResourceType resource = ResourceType.getAssetMap().getAsset(resourceType);
            if (resource != null && !resource.getIcon().isEmpty()) {
                commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", resourceType);
                commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.AssetPath", "");
                commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.AssetPath", resource.getIcon());
                commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ResourceIcon.Visible", true);
            }
        }

        String itemName = displayId;
        if (itemId != null && !itemId.isEmpty()) {
            Item inputItem = Lumenia.ITEMS.get(itemId);
            if (inputItem != null && inputItem.getTranslationKey() != null) {
                try {
                    String translatedName = I18nModule.get().getMessage(this.playerRef.getLanguage(), inputItem.getTranslationKey());
                    if (translatedName != null && !translatedName.isEmpty()) {
                        itemName = translatedName;
                    }
                } catch (Exception e) {
                    itemName = displayId;
                }
            }
        } else if (resourceType != null && !resourceType.isEmpty()) {
            itemName = resourceType;
        }

        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemName.Text", itemName);
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #Quantity.Text", "x" + input.getQuantity());
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", displayType + " " + displayId);

        // Add event binding for clicking on item ID in recipe input
        if (itemId != null && !itemId.isEmpty()) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    inputGridSelector + "[" + inputIndex + "] #ItemId",
                    EventData.of(GuiData.KEY_COPY_ITEM_ID, itemId), false);
        } else {
            if (resourceType != null && !resourceType.isEmpty()) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        inputGridSelector + "[" + inputIndex + "] #ItemId",
                        EventData.of(GuiData.KEY_COPY_ITEM_ID, resourceType), false);
            }
        }
    }

    private void addOutputItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder,
                               @Nonnull String outputGridSelector, @Nonnull MaterialQuantity output, int outputIndex) {
        commandBuilder.append(outputGridSelector, "Pages/Lumengrid_Lumenia_RecipeInputItem.ui");

        String itemId = output.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", "");
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", itemId);

        String itemName = itemId;
        Item outputItem = Lumenia.ITEMS.get(itemId);
        if (outputItem != null && outputItem.getTranslationKey() != null) {
            try {
                String translatedName = I18nModule.get().getMessage(this.playerRef.getLanguage(), outputItem.getTranslationKey());
                if (translatedName != null && !translatedName.isEmpty()) {
                    itemName = translatedName;
                }
            } catch (Exception e) {
                itemName = itemId;
            }
        }

        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemName.Text", itemName);
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #Quantity.Text", "x" + output.getQuantity());
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemId.Text", itemId);

        // Add event binding for clicking on item ID in recipe output
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                outputGridSelector + "[" + outputIndex + "] #ItemId",
                EventData.of(GuiData.KEY_COPY_ITEM_ID, itemId), false);
    }

    private void updatePaginationControls(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder,
                                          int totalPages, int totalItems, int currentPage, @Nonnull String sectionType) {
        String pageChangeKey = "craft".equals(sectionType) ? GuiData.KEY_CRAFT_PAGE_CHANGE : GuiData.KEY_USAGE_PAGE_CHANGE;

        if (totalItems > 0 && totalPages > 1) {
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", true);
            commandBuilder.set("#RecipePanel #PaginationControls #PaginationInfo.Text",
                    (currentPage + 1) + " / " + totalPages);

            commandBuilder.set("#RecipePanel #PaginationControls #PrevPageButton.Visible", true);
            if (currentPage > 0) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PaginationControls #PrevPageButton",
                        EventData.of(pageChangeKey, "prev"), false);
            }

            commandBuilder.set("#RecipePanel #PaginationControls #NextPageButton.Visible", true);
            if (currentPage < totalPages - 1) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PaginationControls #NextPageButton",
                        EventData.of(pageChangeKey, "next"), false);
            }
        } else {
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        }
    }

    private String formatBench(String name) {
        name = name.replaceAll("_", " ");
        if (!name.contains("Bench")) {
            name += " Bench";
        }
        return name;
    }

    private String findBenchItemId(String benchId) {
        String[] patterns = {
                "Item_" + benchId,
                "Item_" + benchId + "_Bench",
                benchId + "_Item",
                benchId
        };

        for (String pattern : patterns) {
            if (Lumenia.ITEMS.containsKey(pattern)) {
                return pattern;
            }
        }

        String lowerBenchId = benchId.toLowerCase();
        for (String itemId : Lumenia.ITEMS.keySet()) {
            String lowerItemId = itemId.toLowerCase();
            if (lowerItemId.contains(lowerBenchId) || lowerBenchId.contains(lowerItemId)) {
                if (lowerItemId.contains("bench") || lowerItemId.contains("workbench") || lowerItemId.contains("crafting")) {
                    return itemId;
                }
            }
        }

        return null;
    }

    @Nonnull
    private String resolveItemOrigin(@Nonnull String itemId) {
        try {
            // Get the asset map for items
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            if (assetMap == null) {
                return "vanilla";
            }

            // Get the pack name that contains this item
            String packName = assetMap.getAssetPack(itemId);
            if (packName == null || packName.isEmpty()) {
                // Fallback: check namespace in itemId
                int colonIndex = itemId.indexOf(':');
                if (colonIndex > 0) {
                    String namespace = itemId.substring(0, colonIndex);
                    if (namespace.equalsIgnoreCase("core") || namespace.equalsIgnoreCase("hytale")) {
                        return "vanilla";
                    }
                    return "Mod: " + namespace;
                }
                return "vanilla";
            }

            // Get the AssetPack object
            AssetPack pack = AssetModule.get().getAssetPack(packName);
            if (pack == null) {
                // Fallback to pack name
                return "Mod: " + packName;
            }

            // Check if it's the base pack (vanilla)
            AssetPack basePack = AssetModule.get().getBaseAssetPack();
            if (pack.equals(basePack)) {
                return "vanilla";
            }

            // Get the mod name from manifest
            try {
                PluginManifest manifest = pack.getManifest();
                if (manifest != null) {
                    String modName = manifest.getName();
                    if (modName != null && !modName.isEmpty()) {
                        return "Mod: " + modName;
                    }
                }
            } catch (Exception e) {
                Lumenia.LOGGER.atSevere().log("Lumenia: resolveItemOrigin ignoreManifestError: " + e.getMessage(), e);
            }

            // Fallback to pack name
            return "Mod: " + packName;
        } catch (Exception e) {
            Lumenia.LOGGER.atSevere().log("Lumenia: resolveItemOrigin: " + e.getMessage(), e);
            return "vanilla";
        }
    }

    public static class GuiData {
        static final String KEY_SEARCH_QUERY = "@SearchQuery";
        static final String KEY_SELECTED_ITEM = "SelectedItem";
        static final String KEY_PAGE_CHANGE = "PageChange";
        static final String KEY_ACTIVE_SECTION = "ActiveSection";
        static final String KEY_CRAFT_PAGE_CHANGE = "CraftPageChange";
        static final String KEY_USAGE_PAGE_CHANGE = "UsagePageChange";
        static final String KEY_DROPS_PAGE_CHANGE = "DropsPageChange";
        static final String KEY_GIVE_ITEM = "GiveItem";
        static final String KEY_COPY_ITEM_ID = "CopyItemId";
        static final String KEY_TOGGLE_KEYBIND = "@ToggleKeybind";
        static final String KEY_KEYBIND_SELECTION = "@KeybindSelection";
        static final String KEY_MOD_FILTER = "@ModFilter";

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.<GuiData>builder(GuiData.class, GuiData::new)
                .addField(new KeyedCodec<>(KEY_SEARCH_QUERY, Codec.STRING),
                        (data, s) -> data.searchQuery = s, data -> data.searchQuery)
                .addField(new KeyedCodec<>(KEY_SELECTED_ITEM, Codec.STRING),
                        (data, s) -> data.selectedItem = s, data -> data.selectedItem)
                .addField(new KeyedCodec<>(KEY_PAGE_CHANGE, Codec.STRING),
                        (data, s) -> data.pageChange = s, data -> data.pageChange)
                .addField(new KeyedCodec<>(KEY_ACTIVE_SECTION, Codec.STRING),
                        (data, s) -> data.activeSection = s, data -> data.activeSection)
                .addField(new KeyedCodec<>(KEY_CRAFT_PAGE_CHANGE, Codec.STRING),
                        (data, s) -> data.craftPageChange = s, data -> data.craftPageChange)
                .addField(new KeyedCodec<>(KEY_USAGE_PAGE_CHANGE, Codec.STRING),
                        (data, s) -> data.usagePageChange = s, data -> data.usagePageChange)
                .addField(new KeyedCodec<>(KEY_DROPS_PAGE_CHANGE, Codec.STRING),
                        (data, s) -> data.dropsPageChange = s, data -> data.dropsPageChange)
                .addField(new KeyedCodec<>(KEY_GIVE_ITEM, Codec.STRING),
                        (data, s) -> data.giveItem = s, data -> data.giveItem)
                .addField(new KeyedCodec<>(KEY_COPY_ITEM_ID, Codec.STRING),
                        (data, s) -> data.copyItemId = s, data -> data.copyItemId)
                .addField(new KeyedCodec<>(KEY_TOGGLE_KEYBIND, Codec.BOOLEAN),
                        (data, b) -> data.toggleKeybind = b, data -> data.toggleKeybind)
                .addField(new KeyedCodec<>(KEY_KEYBIND_SELECTION, Codec.STRING),
                        (data, s) -> data.keybindSelection = s, data -> data.keybindSelection)
                .addField(new KeyedCodec<>(KEY_MOD_FILTER, Codec.STRING),
                        (data, s) -> data.modFilter = s, data -> data.modFilter)
                .build();

        private String searchQuery;
        private String selectedItem;
        private String pageChange;
        private String activeSection;
        private String craftPageChange;
        private String usagePageChange;
        private String dropsPageChange;
        private String giveItem;
        private String copyItemId;
        private Boolean toggleKeybind;
        private String keybindSelection;
        private String modFilter;
    }

    private static class SearchResult {
        public static final Comparator<SearchResult> COMPARATOR = Comparator.comparing((o) -> o.match);
        private final String name;
        private MatchResult match;

        public SearchResult(String name, MatchResult match) {
            this.name = name;
            this.match = match;
        }
    }
}
