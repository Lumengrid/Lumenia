package com.lumengrid.lumenia;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.lumengrid.lumenia.commands.OpenJEICommand;
import com.lumengrid.lumenia.config.LumeniaConfig;
import com.lumengrid.lumenia.recipes.RubbleRecipeManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.*;

public class Main extends JavaPlugin {

    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    public static Map<String, List<String>> ITEM_TO_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that produce it
    public static Map<String, List<String>> ITEM_FROM_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that use it as input
    
    public static Config<LumeniaConfig> CONFIG;
    private static Main instance;

    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        CONFIG = this.withConfig("Lumenia", LumeniaConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();
        
        instance = this;
        CONFIG.save();

        RubbleRecipeManager.setLogger(this.getLogger());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Main::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeLoad);
        this.getEventRegistry().register(RemovedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeRemove);

        this.getCommandRegistry().registerCommand(new OpenJEICommand());
    }

    private static void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = event.getAssetMap().getAssetMap();


        boolean anyRecipesCreated = false;
        
        for (var entry : event.getLoadedAssets().entrySet()) {
            String itemId = entry.getKey();
            Item item = entry.getValue();
            RubbleRecipeManager.processStoneItem(itemId, item);
            RubbleRecipeManager.processRubbleItem(itemId, item);
        }

        for (var entry : ITEMS.entrySet()) {
            String itemId = entry.getKey();
            Item item = entry.getValue();
            RubbleRecipeManager.processStoneItem(itemId, item);
            RubbleRecipeManager.processRubbleItem(itemId, item);
        }
    }

    private static void onRecipeLoad(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        // Get the getInput method using reflection
        java.lang.reflect.Method getInputMethod = null;
        try {
            getInputMethod = CraftingRecipe.class.getMethod("getInput");
        } catch (NoSuchMethodException e) {
            // Method doesn't exist - recipe inputs tracking will be disabled
        }

        for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
            RECIPES.put(recipe.getId(), recipe);

            // Track recipes that produce each item
            for (MaterialQuantity output : recipe.getOutputs()) {
                ITEM_TO_RECIPES.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
            }

            // Track recipes that use each item as input
            if (getInputMethod != null) {
                try {
                    Object inputsObj = getInputMethod.invoke(recipe);
                    if (inputsObj != null) {
                        // Process inputs whether they're a List, array, Collection, or single MaterialQuantity
                        if (inputsObj instanceof MaterialQuantity) {
                            // getInput() returns a single MaterialQuantity
                            MaterialQuantity input = (MaterialQuantity) inputsObj;
                            if (input != null && input.getItemId() != null) {
                                ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                            }
                        } else if (inputsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                            if (inputs != null && !inputs.isEmpty()) {
                                for (MaterialQuantity input : inputs) {
                                    if (input != null && input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                    }
                                }
                            }
                        } else if (inputsObj instanceof MaterialQuantity[]) {
                            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                            if (inputs != null && inputs.length > 0) {
                                for (MaterialQuantity input : inputs) {
                                    if (input != null && input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                    }
                                }
                            }
                        } else if (inputsObj instanceof Collection) {
                            @SuppressWarnings("unchecked")
                            Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                            if (inputs != null && !inputs.isEmpty()) {
                                for (MaterialQuantity input : inputs) {
                                    if (input != null && input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Failed to invoke method - skip this recipe's inputs
                }
            } else {
                // getInput() method doesn't exist, try fallback methods
                String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
                for (String methodName : methodNames) {
                    try {
                        java.lang.reflect.Method fallbackMethod = CraftingRecipe.class.getMethod(methodName);
                        Object inputsObj = fallbackMethod.invoke(recipe);
                        if (inputsObj != null) {
                            if (inputsObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                                if (inputs != null && !inputs.isEmpty()) {
                                    for (MaterialQuantity input : inputs) {
                                        if (input != null && input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                        }
                                    }
                                }
                            } else if (inputsObj instanceof MaterialQuantity[]) {
                                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                                if (inputs != null && inputs.length > 0) {
                                    for (MaterialQuantity input : inputs) {
                                        if (input != null && input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                        }
                                    }
                                }
                            } else if (inputsObj instanceof Collection) {
                                @SuppressWarnings("unchecked")
                                Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                                if (inputs != null && !inputs.isEmpty()) {
                                    for (MaterialQuantity input : inputs) {
                                        if (input != null && input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipe.getId());
                                        }
                                    }
                                }
                            }
                            break; // Found a working method, stop trying others
                        }
                    } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException | IllegalAccessException ignored) {
                        // Try next method
                    }
                }
            }
        }
    }

    private static void onRecipeRemove(RemovedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        for (String recipeId : event.getRemovedAssets()) {
            CraftingRecipe recipe = RECIPES.remove(recipeId);
            if (recipe != null) {
                // Clean up mappings
                for (MaterialQuantity output : recipe.getOutputs()) {
                    List<String> recipes = ITEM_TO_RECIPES.get(output.getItemId());
                    if (recipes != null) {
                        recipes.remove(recipeId);
                    }
                }

                // Clean up input mappings if we were tracking them
                try {
                    java.lang.reflect.Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
                    Object inputsObj = getInputMethod.invoke(recipe);
                    
                    if (inputsObj != null) {
                        if (inputsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                            for (MaterialQuantity input : inputs) {
                                List<String> recipes = ITEM_FROM_RECIPES.get(input.getItemId());
                                if (recipes != null) {
                                    recipes.remove(recipeId);
                                }
                            }
                        } else if (inputsObj instanceof MaterialQuantity[]) {
                            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                            for (MaterialQuantity input : inputs) {
                                List<String> recipes = ITEM_FROM_RECIPES.get(input.getItemId());
                                if (recipes != null) {
                                    recipes.remove(recipeId);
                                }
                            }
                        } else if (inputsObj instanceof Collection) {
                            @SuppressWarnings("unchecked")
                            Collection<MaterialQuantity> inputs = (Collection<MaterialQuantity>) inputsObj;
                            for (MaterialQuantity input : inputs) {
                                List<String> recipes = ITEM_FROM_RECIPES.get(input.getItemId());
                                if (recipes != null) {
                                    recipes.remove(recipeId);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Method not found - skip input cleanup
                }
            }
        }
    }
}
