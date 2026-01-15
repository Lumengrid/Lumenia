package com.lumengrid.lumenia.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.MatchResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.Main;
import com.lumengrid.lumenia.util.MessageHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;

/**
 * JEI-like GUI for browsing items and viewing recipes
 */
public class JEIGui extends InteractiveCustomUIPage<JEIGui.GuiData> {

    private String searchQuery = "";
    private final Map<String, Item> visibleItems = new HashMap<>();
    private String selectedItem = null; // Currently selected item for recipe display
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 20; // 5 columns * 4 rows
    private String selectedUsageTab = null; // Currently selected usage tab (bench key)
    private int usagePage = 0; // Current page for usage recipes
    private static final int USAGE_RECIPES_PER_PAGE = 1; // Recipes per page in usage section
    private String activeSection = "craft"; // "craft" or "usage" - which section is currently shown
    private int craftPage = 0; // Current page for craft recipes
    private static final int CRAFT_RECIPES_PER_PAGE = 1; // Recipes per page in craft section

    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String defaultSearchQuery) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = defaultSearchQuery != null ? defaultSearchQuery : "";
    }

    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String defaultSearchQuery, String selectedItemId) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.searchQuery = defaultSearchQuery != null ? defaultSearchQuery : "";
        this.selectedItem = selectedItemId != null && !selectedItemId.isEmpty() ? selectedItemId : null;
    }

    public JEIGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        this(playerRef, lifetime, "");
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                     @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Lumengrid_Lumenia_Gui.ui");
        
        // Set the search input value if we have a default search query
        if (this.searchQuery != null && !this.searchQuery.isEmpty()) {
            uiCommandBuilder.set("#SearchInput.Value", this.searchQuery);
        }

        // Event binding for search
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"), false);

        // Event binding for pagination (will be set up in buildItemGrid)

        // Build item grid and recipe panel - the search query is already set, so filtering will happen
        this.buildItemGrid(ref, uiCommandBuilder, uiEventBuilder, store);
        this.buildRecipePanel(ref, uiCommandBuilder, uiEventBuilder, store);
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

        if (data.selectedItem != null) {
            this.selectedItem = data.selectedItem;
            // Auto-select "How to Craft" section when an item is selected
            this.activeSection = "craft";
            this.craftPage = 0; // Reset to first page
            
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            // Don't update grid at all - just update recipe panel to avoid X icons
            // The grid will keep its current state and icons
            this.buildRecipePanel(ref, commandBuilder, eventBuilder, store);
            
            // Build and show the "How to Craft" section automatically
            List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
            
            this.sendUpdate(commandBuilder, eventBuilder, false);
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

        if (data.activeSection != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            this.activeSection = data.activeSection;
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            
            // Hide the section that's not active
            if ("craft".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
                // Build and show craft section
                List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
            } else if ("usage".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
                // Build and show usage section
                List<String> usageRecipeIds = Main.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                // Calculate valid usage count
                int validUsageCount = 0;
                for (String usageRecipeId : usageRecipeIds) {
                    var usageRecipe = Main.RECIPES.get(usageRecipeId);
                    if (usageRecipe == null) continue;
                    // Verify the selected item is actually in this recipe's inputs
                    Object inputsObj = null;
                    try {
                        Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
                        inputsObj = getInputMethod.invoke(usageRecipe);
                    } catch (Exception e) {
                        // Skip if can't get inputs
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
                            for (MaterialQuantity input : inputs) {
                                if (input != null && this.selectedItem.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        } else if (inputsObj instanceof MaterialQuantity[]) {
                            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                            for (MaterialQuantity input : inputs) {
                                if (input != null && this.selectedItem.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        } else if (inputsObj instanceof Collection) {
                            @SuppressWarnings("unchecked")
                            Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                            for (MaterialQuantity input : inputs) {
                                if (input != null && this.selectedItem.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (itemFoundInInputs) {
                        validUsageCount++;
                    }
                }
                this.buildUsageSection(ref, commandBuilder, eventBuilder, store, usageRecipeIds, validUsageCount);
            }
            
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        // Handle craft section pagination
        if (data.craftPageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            List<String> validRecipeIds = new ArrayList<>();
            for (String recipeId : recipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) continue;
                boolean itemFoundInOutputs = false;
                for (MaterialQuantity output : recipe.getOutputs()) {
                    if (output != null && this.selectedItem.equals(output.getItemId())) {
                        itemFoundInOutputs = true;
                        break;
                    }
                }
                if (itemFoundInOutputs) {
                    validRecipeIds.add(recipeId);
                }
            }
            
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

        // Handle usage section pagination
        if (data.usagePageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            List<String> usageRecipeIds = Main.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            List<String> validUsageRecipeIds = new ArrayList<>();
            for (String usageRecipeId : usageRecipeIds) {
                var usageRecipe = Main.RECIPES.get(usageRecipeId);
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
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
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
            
            int totalUsagePages = (int) Math.ceil((double) validUsageRecipeIds.size() / USAGE_RECIPES_PER_PAGE);
            if ("prev".equals(data.usagePageChange) && this.usagePage > 0) {
                this.usagePage--;
            } else if ("next".equals(data.usagePageChange) && this.usagePage < totalUsagePages - 1) {
                this.usagePage++;
            }
            
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            int validUsageCount = validUsageRecipeIds.size();
            this.buildUsageSection(ref, commandBuilder, eventBuilder, store, usageRecipeIds, validUsageCount);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
    }

    private void buildItemGrid(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                               @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        // Clear the grid first to remove old items
        commandBuilder.clear("#ItemGrid");

        Map<String, Item> itemList = new HashMap<>(Main.ITEMS);
        ComponentAccessor<EntityStore> componentAccessor = store;
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());

        assert playerComponent != null;

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
                            if (itemId.contains(term)) {
                                match = MatchResult.EXACT;
                            }
                        }
                        
                        // Search in item group/type if still no match
                        if (match == MatchResult.NONE) {
                            String itemGroup = getItemGroup(item).toLowerCase(Locale.ENGLISH);
                            if (itemGroup.contains(term)) {
                                match = MatchResult.EXACT;
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

        // Always ensure selected item is visible if it's set
        if (this.selectedItem != null && !this.selectedItem.isEmpty() && Main.ITEMS.containsKey(this.selectedItem)) {
            this.visibleItems.put(this.selectedItem, Main.ITEMS.get(this.selectedItem));
        }

        // If no item is explicitly selected but we have a search query, auto-select the first item
        if (this.selectedItem == null && !this.searchQuery.isEmpty() && !this.visibleItems.isEmpty()) {
            // Auto-select the first item in the filtered results
            List<Map.Entry<String, Item>> sortedItems = new ArrayList<>(this.visibleItems.entrySet());
            sortedItems.sort((e1, e2) -> {
                String name1 = I18nModule.get().getMessage(this.playerRef.getLanguage(), e1.getValue().getTranslationKey());
                String name2 = I18nModule.get().getMessage(this.playerRef.getLanguage(), e2.getValue().getTranslationKey());
                return name1.compareToIgnoreCase(name2);
            });
            if (!sortedItems.isEmpty()) {
                this.selectedItem = sortedItems.get(0).getKey();
            }
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
            // Set ItemIcon properties - ensure it's visible and has the correct ItemId
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", entry.getKey());
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.Visible", true);
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemName.TextSpans", Message.translation(entry.getValue().getTranslationKey()));
            commandBuilder.set("#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemId.Text", "ID: " + entry.getKey());

            // Don't highlight selected item in grid to avoid X icon issues
            // Selection is indicated by the recipe panel showing item info

            // No tooltip - information is shown in the detail panel when item is selected

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#ItemGrid[" + rowIndex + "][" + cardsInCurrentRow + "]",
                EventData.of(GuiData.KEY_SELECTED_ITEM, entry.getKey()), false);

            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= 5) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }

        // Add pagination controls
        if (totalPages > 1 || allItems.size() > 0) {
            commandBuilder.set("#PaginationControls #PaginationInfo.Visible", true);
            commandBuilder.set("#PaginationControls #PaginationInfo.Text", (this.currentPage + 1) + " / " + totalPages + " (" + allItems.size() + " items)");
            
            // Previous page button
            if (this.currentPage > 0) {
                commandBuilder.set("#PaginationControls #PrevPageButton.Visible", true);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PaginationControls #PrevPageButton",
                    EventData.of("PageChange", "prev"), false);
            } else {
                commandBuilder.set("#PaginationControls #PrevPageButton.Visible", false);
            }
            
            // Next page button
            if (this.currentPage < totalPages - 1) {
                commandBuilder.set("#PaginationControls #NextPageButton.Visible", true);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PaginationControls #NextPageButton",
                    EventData.of("PageChange", "next"), false);
            } else {
                commandBuilder.set("#PaginationControls #NextPageButton.Visible", false);
            }
        } else {
            commandBuilder.set("#PaginationControls #PaginationInfo.Visible", false);
            commandBuilder.set("#PaginationControls #PrevPageButton.Visible", false);
            commandBuilder.set("#PaginationControls #NextPageButton.Visible", false);
        }
    }

    private void buildRecipePanel(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                  @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        // Always show the recipe panel
        commandBuilder.set("#RecipePanel.Visible", true);
        commandBuilder.clear("#RecipePanel #CraftSection #CraftList");
        commandBuilder.set("#RecipePanel #NoRecipes.Visible", false);

        if (this.selectedItem == null || this.selectedItem.isEmpty()) {
            // No item selected - hide all item-related information
            commandBuilder.set("#RecipePanel #ItemInfo.Visible", false);
            commandBuilder.set("#RecipePanel #SectionButtons.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
            commandBuilder.set("#RecipePanel #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #NoRecipes.Text", "Select an item to view recipes.");
            return;
        }

        // Show item info
        Item item = Main.ITEMS.get(this.selectedItem);
        if (item == null) {
            // Item not found - hide all item-related information
            commandBuilder.set("#RecipePanel #ItemInfo.Visible", false);
            commandBuilder.set("#RecipePanel #SectionButtons.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
            commandBuilder.set("#RecipePanel #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #NoRecipes.Text", "Item not found.");
            return;
        }

        // Display item information
        commandBuilder.set("#RecipePanel #ItemInfo.Visible", true);
        // Clear ItemId first to avoid showing X from previous selection, then set it
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", "");
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", this.selectedItem);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.Visible", true);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemName.TextSpans", Message.translation(item.getTranslationKey()).bold(true));
        commandBuilder.set("#RecipePanel #ItemInfo #ItemId.Text", "ID: " + this.selectedItem);
        
        // Initialize usage section as hidden
        commandBuilder.set("#RecipePanel #UsageSection.Visible", false);

        // Get recipe IDs for building the craft section
        List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
        
        // Clear and build item properties
        commandBuilder.clear("#RecipePanel #ItemInfo #ItemProperties");
        int propIndex = 0;
        
        commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
        commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Max Stack: " + item.getMaxStack());
        propIndex++;
        
        if (item.getMaxDurability() > 0) {
            commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
            commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Durability: " + item.getMaxDurability());
            propIndex++;
        }
        
        if (item.getItemLevel() > 0) {
            commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
            commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Item Level: " + item.getItemLevel());
            propIndex++;
        }
        
        // Add item type info
        if (item.getTool() != null) {
            commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
            commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Type: Tool");
            propIndex++;
        } else if (item.getWeapon() != null) {
            commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
            commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Type: Weapon");
            propIndex++;
        } else if (item.getArmor() != null) {
            commandBuilder.appendInline("#RecipePanel #ItemInfo #ItemProperties", "Label { Style: (FontSize: 12, TextColor: #aaaaaa); }");
            commandBuilder.set("#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "].Text", "Type: Armor");
            propIndex++;
        }

        // Auto-show "How to Craft" section when item is selected
        this.activeSection = "craft";
        this.craftPage = 0; // Reset to first page
        
        // Hide usage section, show craft section
        commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
        commandBuilder.set("#RecipePanel #CraftSection.Visible", true);

        // Show section buttons (only visible when item is selected)
        commandBuilder.set("#RecipePanel #SectionButtons.Visible", true);

        // Bind section button events
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #HowToCraftButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "craft"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #UsedInButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "usage"), false);

        // Build and show the "How to Craft" section automatically
        this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
    }

    private void buildCraftSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                  @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                  List<String> recipeIds) {
        // Show recipes section - always show the section, even if empty
        commandBuilder.clear("#RecipePanel #CraftSection #CraftList");
        commandBuilder.set("#RecipePanel #CraftSection.Visible", true);
        
        // Filter recipes to only include those that actually produce the selected item
        List<String> validRecipeIds = new ArrayList<>();
        for (String recipeId : recipeIds) {
            CraftingRecipe recipe = Main.RECIPES.get(recipeId);
            if (recipe == null) continue;
            
            // Verify the selected item is actually in this recipe's outputs
            boolean itemFoundInOutputs = false;
            for (MaterialQuantity output : recipe.getOutputs()) {
                if (output != null && this.selectedItem.equals(output.getItemId())) {
                    itemFoundInOutputs = true;
                    break;
                }
            }
            
            if (itemFoundInOutputs) {
                validRecipeIds.add(recipeId);
            }
        }
        
        if (validRecipeIds.isEmpty()) {
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Text", "No recipes available for this item.");
            commandBuilder.set("#RecipePanel #CraftSection #CraftList.Visible", false);
            // Hide pagination when no recipes
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        } else {
            commandBuilder.set("#RecipePanel #CraftSection #NoRecipes.Visible", false);
            commandBuilder.set("#RecipePanel #CraftSection #CraftList.Visible", true);

            // Calculate pagination for craft section
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

            // Build pagination controls (shared, after buttons) - always visible when recipes exist
            this.updatePaginationControls(commandBuilder, eventBuilder, totalCraftPages, validRecipeIds.size(), this.craftPage, "craft");

            // Display recipes - one per page item, each showing bench, inputs, and outputs
            int recipeIndex = 0;
            for (String recipeId : pageRecipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) {
                    continue;
                }

                try {
                    commandBuilder.append("#RecipePanel #CraftSection #CraftList", "Pages/Lumengrid_Lumenia_RecipeDisplay.ui");
                    this.buildRecipeDisplay(commandBuilder, recipe, "#RecipePanel #CraftSection #CraftList", recipeIndex);
                    ++recipeIndex;
                } catch (Exception e) {
                    // If there's an error building this recipe, skip it and continue
                    continue;
                }
            }
        }
    }

    private void buildUsageSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                   @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                   List<String> usageRecipeIds, int validUsageCount) {
        // Display usage section (recipes that use this item as ingredient)
        // This shows what items can be crafted USING the selected item as an ingredient
        // We need to verify the item is actually in the recipe inputs
        
        commandBuilder.clear("#RecipePanel #UsageSection #UsageList");
        commandBuilder.set("#RecipePanel #UsageSection.Visible", true);
        
        // Filter and verify recipes that actually use this item
        List<String> validUsageRecipeIds = new ArrayList<>();
        for (String usageRecipeId : usageRecipeIds) {
            var usageRecipe = Main.RECIPES.get(usageRecipeId);
            if (usageRecipe == null) continue;

            // Verify the selected item is actually in this recipe's inputs
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
                    } catch (NoSuchMethodException ignored) {
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
            }
            
            // Verify the selected item is actually in the inputs
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

        if (validUsageRecipeIds.isEmpty()) {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Text", "This item is not used in any recipes.");
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", false);
            // Hide pagination when no recipes
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        } else {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", true);

            // Calculate pagination for usage section
            int totalUsagePages = (int) Math.ceil((double) validUsageRecipeIds.size() / USAGE_RECIPES_PER_PAGE);
            if (this.usagePage >= totalUsagePages && totalUsagePages > 0) {
                this.usagePage = totalUsagePages - 1;
            }
            if (this.usagePage < 0) {
                this.usagePage = 0;
            }

            int startIndex = this.usagePage * USAGE_RECIPES_PER_PAGE;
            int endIndex = Math.min(startIndex + USAGE_RECIPES_PER_PAGE, validUsageRecipeIds.size());
            List<String> pageRecipeIds = validUsageRecipeIds.subList(startIndex, endIndex);

            // Build pagination controls (shared, after buttons) - always visible when recipes exist
            this.updatePaginationControls(commandBuilder, eventBuilder, totalUsagePages, validUsageRecipeIds.size(), this.usagePage, "usage");

            // Display recipes - one per page item, each showing bench, inputs, and outputs
            int recipeIndex = 0;
            for (String recipeId : pageRecipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) {
                    continue;
                }

                try {
                    commandBuilder.append("#RecipePanel #UsageSection #UsageList", "Pages/Lumengrid_Lumenia_RecipeDisplay.ui");
                    this.buildRecipeDisplay(commandBuilder, recipe, "#RecipePanel #UsageSection #UsageList", recipeIndex);
                    ++recipeIndex;
                } catch (Exception e) {
                    // If there's an error building this recipe, skip it and continue
                    continue;
                }
            }
        }
    }

    // Helper methods
    private String getItemGroup(Item item) {
        if (item.getTool() != null) return "Tool";
        if (item.getWeapon() != null) return "Weapon";
        if (item.getArmor() != null) return "Armor";
        if (item.getGlider() != null) return "Glider";
        if (item.getUtility() != null) return "Utility";
        if (item.getPortalKey() != null) return "PortalKey";
        if (item.hasBlockType()) return "Block";
        return "Item";
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, int value) {
        return this.addTooltipLine(tooltip, key, value + "");
    }

    private MessageHelper.ML addTooltipLine(MessageHelper.ML tooltip, String key, String value) {
        return tooltip.append(Message.raw(key).color("#93844c").bold(true))
                     .append(Message.raw(value)).nl();
    }

    private String formatBench(String name) {
        name = name.replaceAll("_", " ");
        if (!name.contains("Bench")) {
            name += " Bench";
        }
        return name;
    }

    private String findBenchItemId(String benchId) {
        // Try common patterns for bench item IDs
        String[] patterns = {
            "Item_" + benchId,
            "Item_" + benchId + "_Bench",
            benchId + "_Item",
            benchId
        };
        
        for (String pattern : patterns) {
            if (Main.ITEMS.containsKey(pattern)) {
                return pattern;
            }
        }
        
        // Try searching by partial match (case-insensitive)
        String lowerBenchId = benchId.toLowerCase();
        for (String itemId : Main.ITEMS.keySet()) {
            String lowerItemId = itemId.toLowerCase();
            if (lowerItemId.contains(lowerBenchId) || lowerBenchId.contains(lowerItemId)) {
                // Check if it's likely a bench item
                if (lowerItemId.contains("bench") || lowerItemId.contains("workbench") || lowerItemId.contains("crafting")) {
                    return itemId;
                }
            }
        }
        
        return null;
    }

    /**
     * Unified method to build a complete recipe display with bench, inputs, and outputs
     * @param commandBuilder UI command builder
     * @param recipe The recipe to display
     * @param listSelector The selector for the recipe list (e.g., "#CraftSection #CraftList" or "#UsageSection #UsageList")
     * @param recipeIndex The index of the recipe in the list
     */
    private void buildRecipeDisplay(@Nonnull UICommandBuilder commandBuilder, @Nonnull CraftingRecipe recipe,
                                   @Nonnull String listSelector, int recipeIndex) {
        String recipeSelector = listSelector + "[" + recipeIndex + "]";
        String contentSelector = recipeSelector + " #RecipeContentContainer #RecipeContent";
        
        // Hide recipe title (not needed)
        commandBuilder.set(recipeSelector + " #RecipeTitle.Visible", false);
        
        // Build bench info (icon + name + id + tier)
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            
            // Check if bench requirement is "Fieldcraft" - show as "Pocket crafting"
            if ("Fieldcraft".equals(bench.id)) {
                commandBuilder.set(contentSelector + " #BenchInfo.Visible", true);
                commandBuilder.set(contentSelector + " #BenchInfo #BenchText.Text", "Pocket crafting");
                commandBuilder.set(contentSelector + " #BenchInfo #BenchId.Text", "");
                commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", false);
            } else {
                String benchName = formatBench(bench.id);
                commandBuilder.set(contentSelector + " #BenchInfo.Visible", true);
                commandBuilder.set(contentSelector + " #BenchInfo #BenchText.Text", benchName + " Tier " + bench.requiredTierLevel);
                commandBuilder.set(contentSelector + " #BenchInfo #BenchId.Text", "ID: " + bench.id);

                // Set bench icon
                String benchItemId = findBenchItemId(bench.id);
                if (benchItemId != null && Main.ITEMS.containsKey(benchItemId)) {
                    commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.ItemId", "");
                    commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.ItemId", benchItemId);
                    commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", true);
                } else {
                    commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", false);
                }
            }
        } else {
            commandBuilder.set(contentSelector + " #BenchInfo.Visible", true);
            commandBuilder.set(contentSelector + " #BenchInfo #BenchText.Text", "Pocket crafting");
            commandBuilder.set(contentSelector + " #BenchInfo #BenchId.Text", "");
            commandBuilder.set(contentSelector + " #BenchInfo #BenchIcon.Visible", false);
        }

        // Build inputs
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
        } catch (Exception e) {
            // Will handle below
        }

        int inputIndex = 0;
        String inputGridSelector = contentSelector + " #InputGrid";
        
        // Show input label if there are inputs
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
                    // Always add, even if itemId is null (might be resource type)
                    this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                }
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            // Always add, even if itemId is null (might be resource type)
                            this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                if (inputs != null && inputs.length > 0) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            // Always add, even if itemId is null (might be resource type)
                            this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            } else if (inputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            // Always add, even if itemId is null (might be resource type)
                            this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            }
        }

        if (inputIndex == 0) {
            // No inputs found - show placeholder
            commandBuilder.appendInline(inputGridSelector, "Label { Style: (FontSize: 12, TextColor: #888888); }");
            commandBuilder.set(inputGridSelector + "[0].Text", "(Ingredients not available - API may not expose recipe inputs)");
        }

        // Build outputs
        Object outputsObj = recipe.getOutputs();
        int outputIndex = 0;
        String outputGridSelector = contentSelector + " #OutputGrid";
        
        // Show output label
        commandBuilder.set(contentSelector + " #OutputLabel.Visible", true);
        
        if (outputsObj != null) {
            if (outputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> outputs = (List<MaterialQuantity>) outputsObj;
                if (outputs != null && !outputs.isEmpty()) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            } else if (outputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] outputs = (MaterialQuantity[]) outputsObj;
                if (outputs != null && outputs.length > 0) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            } else if (outputsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<MaterialQuantity> outputs = (Collection<MaterialQuantity>) outputsObj;
                if (outputs != null && !outputs.isEmpty()) {
                    for (MaterialQuantity output : outputs) {
                        if (output != null && output.getItemId() != null) {
                            this.addOutputItem(commandBuilder, outputGridSelector, output, outputIndex++);
                        }
                    }
                }
            }
        }

        if (outputIndex == 0) {
            // No outputs found - hide output label
            commandBuilder.set(contentSelector + " #OutputLabel.Visible", false);
        }
    }

    /**
     * Helper method to add a single ingredient item to the display
     */
    private void addIngredientItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull String inputGridSelector,
                                   @Nonnull MaterialQuantity input, int inputIndex) {
        commandBuilder.append(inputGridSelector, "Pages/Lumengrid_Lumenia_RecipeInputItem.ui");
        
        // Get item ID or resource type
        String itemId = input.getItemId();
        String resourceType = null;
        
        // Try to get resource type if item ID is null/empty
        if (itemId == null || itemId.isEmpty()) {
            try {
                Method getResourceTypeMethod = MaterialQuantity.class.getMethod("getResourceType");
                Object resourceTypeObj = getResourceTypeMethod.invoke(input);
                if (resourceTypeObj != null) {
                    resourceType = resourceTypeObj.toString();
                }
            } catch (Exception e) {
                // Resource type method doesn't exist or failed - try alternative methods
                try {
                    String[] methodNames = {"getResourceTypeId", "getResourceTypeName", "getType"};
                    for (String methodName : methodNames) {
                        try {
                            Method method = MaterialQuantity.class.getMethod(methodName);
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
        
        // If both item ID and resource type are null/empty, skip
        if ((itemId == null || itemId.isEmpty()) && (resourceType == null || resourceType.isEmpty())) {
            // Still show something - use a placeholder
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", false);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemName.Text", "Unknown Material");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #Quantity.Text", "x" + input.getQuantity());
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", "ID: (unknown)");
            return;
        }
        
        // Use resource type if item ID is not available
        String displayId = (itemId != null && !itemId.isEmpty()) ? itemId : resourceType;
        String displayType = (itemId != null && !itemId.isEmpty()) ? "ID" : "Resource Type";
        
        // Set icon if we have an item ID
        if (itemId != null && !itemId.isEmpty()) {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", itemId);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", true);
        } else {
            // No item ID - hide icon
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", false);
        }

        // Try to get item name, with fallback to ID/resource type
        String itemName = displayId; // Default fallback
        if (itemId != null && !itemId.isEmpty()) {
            Item inputItem = Main.ITEMS.get(itemId);
            if (inputItem != null && inputItem.getTranslationKey() != null) {
                try {
                    String translatedName = I18nModule.get().getMessage(this.playerRef.getLanguage(), inputItem.getTranslationKey());
                    if (translatedName != null && !translatedName.isEmpty()) {
                        itemName = translatedName;
                    }
                } catch (Exception e) {
                    // If translation fails, use ID as fallback
                    itemName = displayId;
                }
            }
        } else if (resourceType != null && !resourceType.isEmpty()) {
            // For resource types, use the resource type name
            itemName = resourceType;
        }
        
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemName.Text", itemName);
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #Quantity.Text", "x" + input.getQuantity());
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", displayType + ": " + displayId);
    }

    /**
     * Helper method to add a single output item to the display
     */
    private void addOutputItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull String outputGridSelector,
                               @Nonnull MaterialQuantity output, int outputIndex) {
        commandBuilder.append(outputGridSelector, "Pages/Lumengrid_Lumenia_RecipeInputItem.ui");
        
        // Always set the item ID first as fallback
        String itemId = output.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            // Skip if no item ID
            return;
        }
        
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", "");
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", itemId);

        // Try to get item name, with fallback to ID
        String itemName = itemId; // Default fallback
        Item outputItem = Main.ITEMS.get(itemId);
        if (outputItem != null && outputItem.getTranslationKey() != null) {
            try {
                String translatedName = I18nModule.get().getMessage(this.playerRef.getLanguage(), outputItem.getTranslationKey());
                if (translatedName != null && !translatedName.isEmpty()) {
                    itemName = translatedName;
                }
            } catch (Exception e) {
                // If translation fails, use ID as fallback
                itemName = itemId;
            }
        }
        
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemName.Text", itemName);
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #Quantity.Text", "x" + output.getQuantity());
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemId.Text", "ID: " + itemId);
    }

    /**
     * Helper method to update shared pagination controls
     */
    private void updatePaginationControls(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder,
                                         int totalPages, int totalItems, int currentPage, @Nonnull String sectionType) {
        String pageChangeKey = "craft".equals(sectionType) ? GuiData.KEY_CRAFT_PAGE_CHANGE : GuiData.KEY_USAGE_PAGE_CHANGE;
        
        // Only show pagination if there are recipes AND more than one page
        if (totalItems > 0 && totalPages > 1) {
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", true);
            commandBuilder.set("#RecipePanel #PaginationControls #PaginationInfo.Text",
                    (currentPage + 1) + " / " + totalPages);

            // Previous page button
            if (currentPage > 0) {
                commandBuilder.set("#RecipePanel #PaginationControls #PrevPageButton.Visible", true);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PaginationControls #PrevPageButton",
                        EventData.of(pageChangeKey, "prev"), false);
            } else {
                commandBuilder.set("#RecipePanel #PaginationControls #PrevPageButton.Visible", false);
            }

            // Next page button
            if (currentPage < totalPages - 1) {
                commandBuilder.set("#RecipePanel #PaginationControls #NextPageButton.Visible", true);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #PaginationControls #NextPageButton",
                        EventData.of(pageChangeKey, "next"), false);
            } else {
                commandBuilder.set("#RecipePanel #PaginationControls #NextPageButton.Visible", false);
            }
        } else {
            // Hide pagination when no recipes or only one page
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        }
    }

    public static class GuiData {
        static final String KEY_SEARCH_QUERY = "@SearchQuery";
        static final String KEY_SELECTED_ITEM = "SelectedItem";
        static final String KEY_PAGE_CHANGE = "PageChange";
        static final String KEY_SELECTED_USAGE_TAB = "SelectedUsageTab";
        static final String KEY_USAGE_PAGE_CHANGE = "UsagePageChange";
        static final String KEY_ACTIVE_SECTION = "ActiveSection";
        static final String KEY_CRAFT_PAGE_CHANGE = "CraftPageChange";

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.<GuiData>builder(GuiData.class, GuiData::new)
            .addField(new KeyedCodec<>(KEY_SEARCH_QUERY, Codec.STRING),
                (data, s) -> data.searchQuery = s, data -> data.searchQuery)
            .addField(new KeyedCodec<>(KEY_SELECTED_ITEM, Codec.STRING),
                (data, s) -> data.selectedItem = s, data -> data.selectedItem)
            .addField(new KeyedCodec<>(KEY_PAGE_CHANGE, Codec.STRING),
                (data, s) -> data.pageChange = s, data -> data.pageChange)
            .addField(new KeyedCodec<>(KEY_SELECTED_USAGE_TAB, Codec.STRING),
                (data, s) -> data.selectedUsageTab = s, data -> data.selectedUsageTab)
            .addField(new KeyedCodec<>(KEY_USAGE_PAGE_CHANGE, Codec.STRING),
                (data, s) -> data.usagePageChange = s, data -> data.usagePageChange)
            .addField(new KeyedCodec<>(KEY_ACTIVE_SECTION, Codec.STRING),
                (data, s) -> data.activeSection = s, data -> data.activeSection)
            .addField(new KeyedCodec<>(KEY_CRAFT_PAGE_CHANGE, Codec.STRING),
                (data, s) -> data.craftPageChange = s, data -> data.craftPageChange)
            .build();

        private String searchQuery;
        private String selectedItem;
        private String pageChange;
        private String selectedUsageTab;
        private String usagePageChange;
        private String activeSection;
        private String craftPageChange;
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
