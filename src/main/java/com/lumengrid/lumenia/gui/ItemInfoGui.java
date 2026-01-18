package com.lumengrid.lumenia.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;

/**
 * GUI for displaying item information and recipes
 */
public class ItemInfoGui extends InteractiveCustomUIPage<ItemInfoGui.GuiData> {

    private String selectedItem;
    private String activeSection = "craft";
    private int craftPage = 0;
    private int usagePage = 0;
    private static final int CRAFT_RECIPES_PER_PAGE = 1;
    private static final int USAGE_RECIPES_PER_PAGE = 1;

    public ItemInfoGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String itemId) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.selectedItem = itemId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                     @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Lumengrid_Lumenia_ItemInfo.ui");
        
        if (this.selectedItem != null && !this.selectedItem.isEmpty()) {
            this.buildItemInfo(ref, uiCommandBuilder, uiEventBuilder, store);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull GuiData data) {
        super.handleDataEvent(ref, store, data);

        if (data.selectedItem != null) {
            this.selectedItem = data.selectedItem;
            this.activeSection = "craft";
            this.craftPage = 0;
            
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildItemInfo(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.activeSection != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            this.activeSection = data.activeSection;
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            
            if ("craft".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
                List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
            } else if ("usage".equals(this.activeSection)) {
                commandBuilder.set("#RecipePanel #CraftSection.Visible", false);
                List<String> usageRecipeIds = Main.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
                int validUsageCount = this.countValidUsageRecipes(usageRecipeIds);
                this.buildUsageSection(ref, commandBuilder, eventBuilder, store, usageRecipeIds, validUsageCount);
            }
            
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.craftPageChange != null && this.selectedItem != null && !this.selectedItem.isEmpty()) {
            List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
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
            List<String> usageRecipeIds = Main.ITEM_FROM_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
            List<String> validUsageRecipeIds = this.getValidUsageRecipes(usageRecipeIds);
            
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

        if (data.giveItem != null && !data.giveItem.isEmpty()) {
            ComponentAccessor<EntityStore> componentAccessor = store;
            Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
            if (playerComponent != null) {
                Item item = Main.ITEMS.get(data.giveItem);
                if (item != null) {
                    try {
                        var inv = playerComponent.getInventory();
                        if (inv != null) {
                            // Use reflection to create item stack and add it
                            java.lang.reflect.Method createItemStackMethod = Item.class.getMethod("createItemStack", int.class);
                            Object itemStack = createItemStackMethod.invoke(item, 1);
                            java.lang.reflect.Method addItemMethod = inv.getClass().getMethod("addItem", itemStack.getClass());
                            addItemMethod.invoke(inv, itemStack);
                            playerComponent.sendMessage(Message.raw("Item given!").color("#00ff00"));
                        }
                    } catch (Exception e) {
                        playerComponent.sendMessage(Message.raw("Error giving item: " + e.getMessage()).color("#ff0000"));
                    }
                }
            }
        }
    }

    private void buildItemInfo(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
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

        Item item = Main.ITEMS.get(this.selectedItem);
        if (item == null) {
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
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", "");
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.ItemId", this.selectedItem);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemIcon.Visible", true);
        commandBuilder.set("#RecipePanel #ItemInfo #ItemName.TextSpans", Message.translation(item.getTranslationKey()).bold(true));
        commandBuilder.set("#RecipePanel #ItemInfo #ItemId.Text", this.selectedItem);
        
        commandBuilder.set("#RecipePanel #UsageSection.Visible", false);

        List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(this.selectedItem, Collections.emptyList());
        
        // Clear and build item properties
        commandBuilder.clear("#RecipePanel #ItemInfo #ItemProperties");
        int propIndex = 0;
        
        // Add vanilla/modded info first, then Max Stack on the same row
        String originInfo = this.resolveItemOrigin(this.selectedItem);
        String maxStackText = "Max Stack: " + item.getMaxStack();
        
        // Use a template file for the row with Max Stack and Origin
        commandBuilder.append("#RecipePanel #ItemInfo #ItemProperties", "Pages/Lumengrid_Lumenia_ItemPropertyRow.ui");
        String rowPath = "#RecipePanel #ItemInfo #ItemProperties[" + propIndex + "]";
        commandBuilder.set(rowPath + " #MaxStackLabel.Text", maxStackText);
        commandBuilder.set(rowPath + " #OriginLabel.Text", originInfo);
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

        this.activeSection = "craft";
        this.craftPage = 0;
        
        commandBuilder.set("#RecipePanel #UsageSection.Visible", false);
        commandBuilder.set("#RecipePanel #CraftSection.Visible", true);

        commandBuilder.set("#RecipePanel #SectionButtons.Visible", true);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #HowToCraftButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "craft"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #SectionButtons #UsedInButton",
                EventData.of(GuiData.KEY_ACTIVE_SECTION, "usage"), false);

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
            commandBuilder.set("#RecipePanel #ItemInfo #GiveItemButton.Visible", false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecipePanel #ItemInfo #GiveItemButton",
                    EventData.of(GuiData.KEY_GIVE_ITEM, this.selectedItem), false);
        } else {
            commandBuilder.set("#RecipePanel #ItemInfo #GiveItemButton.Visible", false);
        }

        this.buildCraftSection(ref, commandBuilder, eventBuilder, store, recipeIds);
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
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) {
                    continue;
                }

                try {
                    commandBuilder.append("#RecipePanel #CraftSection #CraftList", "Pages/Lumengrid_Lumenia_RecipeDisplay.ui");
                    this.buildRecipeDisplay(commandBuilder, recipe, "#RecipePanel #CraftSection #CraftList", recipeIndex);
                    ++recipeIndex;
                } catch (Exception e) {
                    continue;
                }
            }
        }
    }

    private void buildUsageSection(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                                   @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store,
                                   List<String> usageRecipeIds, int validUsageCount) {
        commandBuilder.clear("#RecipePanel #UsageSection #UsageList");
        commandBuilder.set("#RecipePanel #UsageSection.Visible", true);
        
        List<String> validUsageRecipeIds = this.getValidUsageRecipes(usageRecipeIds);

        if (validUsageRecipeIds.isEmpty()) {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", true);
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Text", "This item is not used in any recipes.");
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", false);
            commandBuilder.set("#RecipePanel #PaginationControls.Visible", false);
        } else {
            commandBuilder.set("#RecipePanel #UsageSection #NoUsageRecipes.Visible", false);
            commandBuilder.set("#RecipePanel #UsageSection #UsageList.Visible", true);

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

            this.updatePaginationControls(commandBuilder, eventBuilder, totalUsagePages, validUsageRecipeIds.size(), this.usagePage, "usage");

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
                    continue;
                }
            }
        }
    }

    private List<String> getValidCraftRecipes(List<String> recipeIds) {
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
        return validRecipeIds;
    }

    private List<String> getValidUsageRecipes(List<String> usageRecipeIds) {
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
                    } catch (NoSuchMethodException ignored) {
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
        return validUsageRecipeIds;
    }

    private int countValidUsageRecipes(List<String> usageRecipeIds) {
        return this.getValidUsageRecipes(usageRecipeIds).size();
    }

    private void buildRecipeDisplay(@Nonnull UICommandBuilder commandBuilder, @Nonnull CraftingRecipe recipe,
                                   @Nonnull String listSelector, int recipeIndex) {
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
            if (benchItemId != null && Main.ITEMS.containsKey(benchItemId)) {
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
        } catch (Exception e) {
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
                    this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                }
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
                            this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                if (inputs != null && inputs.length > 0) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null) {
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
                            this.addIngredientItem(commandBuilder, inputGridSelector, input, inputIndex++);
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
            commandBuilder.set(contentSelector + " #OutputLabel.Visible", false);
        }
    }

    private void addIngredientItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull String inputGridSelector,
                                   @Nonnull MaterialQuantity input, int inputIndex) {
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
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemName.Text", "Unknown Material");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #Quantity.Text", "x" + input.getQuantity());
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", "ID: (unknown)");
            return;
        }
        
        String displayId = (itemId != null && !itemId.isEmpty()) ? itemId : resourceType;
        String displayType = (itemId != null && !itemId.isEmpty()) ? "ID" : "Resource Type";
        
        if (itemId != null && !itemId.isEmpty()) {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", itemId);
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", true);
        } else {
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.ItemId", "");
            commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemIcon.Visible", false);
        }

        String itemName = displayId;
        if (itemId != null && !itemId.isEmpty()) {
            Item inputItem = Main.ITEMS.get(itemId);
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
        commandBuilder.set(inputGridSelector + "[" + inputIndex + "] #ItemId.Text", displayType + ": " + displayId);
    }

    private void addOutputItem(@Nonnull UICommandBuilder commandBuilder, @Nonnull String outputGridSelector,
                               @Nonnull MaterialQuantity output, int outputIndex) {
        commandBuilder.append(outputGridSelector, "Pages/Lumengrid_Lumenia_RecipeInputItem.ui");
        
        String itemId = output.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", "");
        commandBuilder.set(outputGridSelector + "[" + outputIndex + "] #ItemIcon.ItemId", itemId);

        String itemName = itemId;
        Item outputItem = Main.ITEMS.get(itemId);
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
            if (Main.ITEMS.containsKey(pattern)) {
                return pattern;
            }
        }
        
        String lowerBenchId = benchId.toLowerCase();
        for (String itemId : Main.ITEMS.keySet()) {
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
            String originFull = this.resolveItemOwnerFull(itemId);
            boolean isVanilla = this.isVanillaOwner(originFull);

            if (isVanilla) {
                return "vanilla";
            } else {
                String modName = originFull;
                if (!modName.isEmpty()) {
                    int colon = modName.indexOf(':');
                    if (colon >= 0 && colon + 1 < modName.length()) {
                        modName = modName.substring(colon + 1);
                    }
                    return modName;
                }
            }
        } catch (Exception e) {

        }
        return "";
    }

    @Nonnull
    private String resolveItemOwnerFull(@Nonnull String itemId) {
        if (itemId != null && !itemId.isEmpty()) {
            int colon = itemId.indexOf(':');
            if (colon > 0) {
                String ns = itemId.substring(0, colon);
                if (ns.equalsIgnoreCase("core")) {
                    return "Hytale:Hytale";
                }
                return ns;
            }
        }

        String fromPack = this.getOriginFromItemPackMap(itemId);
        return fromPack != null && !fromPack.isEmpty() ? fromPack : "";
    }

    private boolean isVanillaOwner(String ownerFull) {
        return ownerFull != null && ownerFull.equalsIgnoreCase("Hytale:Hytale");
    }

    private String getOriginFromItemPackMap(@Nonnull String itemId) {
        try {
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            if (assetMap == null) {
                return null;
            }

            java.util.Collection<AssetPack> assetPacks = AssetModule.get().getAssetPacks();
            if (assetPacks == null) {
                return null;
            }

            String lowerItemId = itemId.toLowerCase(Locale.ENGLISH);
            
            for (AssetPack pack : assetPacks) {
                if (pack == null) continue;
                
                java.util.Set<String> keys = assetMap.getKeysForPack(pack.getName());
                if (keys == null || !keys.contains(lowerItemId)) {
                    continue;
                }

                String origin = null;
                try {
                    PluginManifest manifest = pack.getManifest();
                    if (manifest != null) {
                        String group = manifest.getGroup();
                        String name = manifest.getName();
                        if (group != null && !group.isEmpty() && name != null && !name.isEmpty()) {
                            origin = group + ":" + name;
                        } else if (name != null && !name.isEmpty()) {
                            origin = name;
                        }
                    }
                } catch (Exception e) {
                }

                if (origin == null || origin.isEmpty()) {
                    origin = pack.getName();
                }

                return origin;
            }
        } catch (Exception e) {
        }
        
        return null;
    }

    public static class GuiData {
        static final String KEY_SELECTED_ITEM = "SelectedItem";
        static final String KEY_ACTIVE_SECTION = "ActiveSection";
        static final String KEY_CRAFT_PAGE_CHANGE = "CraftPageChange";
        static final String KEY_USAGE_PAGE_CHANGE = "UsagePageChange";
        static final String KEY_GIVE_ITEM = "GiveItem";

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.<GuiData>builder(GuiData.class, GuiData::new)
            .addField(new KeyedCodec<>(KEY_SELECTED_ITEM, Codec.STRING),
                (data, s) -> data.selectedItem = s, data -> data.selectedItem)
            .addField(new KeyedCodec<>(KEY_ACTIVE_SECTION, Codec.STRING),
                (data, s) -> data.activeSection = s, data -> data.activeSection)
            .addField(new KeyedCodec<>(KEY_CRAFT_PAGE_CHANGE, Codec.STRING),
                (data, s) -> data.craftPageChange = s, data -> data.craftPageChange)
            .addField(new KeyedCodec<>(KEY_USAGE_PAGE_CHANGE, Codec.STRING),
                (data, s) -> data.usagePageChange = s, data -> data.usagePageChange)
            .addField(new KeyedCodec<>(KEY_GIVE_ITEM, Codec.STRING),
                (data, s) -> data.giveItem = s, data -> data.giveItem)
            .build();

        private String selectedItem;
        private String activeSection;
        private String craftPageChange;
        private String usagePageChange;
        private String giveItem;
    }
}
