package com.lumengrid.lumenia;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.crafting.BenchRecipeRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.registry.CodecMapRegistry;
import com.hypixel.hytale.server.core.util.Config;
import com.lumengrid.lumenia.commands.OpenJEICommand;
import com.lumengrid.lumenia.interactions.OpenLumeniaBookInteraction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.lang.reflect.Field;
import java.util.*;

public class Main extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    public static Map<String, List<String>> ITEM_TO_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that produce it
    public static Map<String, List<String>> ITEM_FROM_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that use it as input
    private static final Map<String, BenchRecipeRegistry> registries = new Object2ObjectOpenHashMap<>();
    private static Main instance;
    public final Config<LumeniaConfig> config;
    private com.hypixel.hytale.component.ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, LumeniaComponent> componentType;

    public static Main getInstance() {
        return instance;
    }

    public com.hypixel.hytale.component.ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, LumeniaComponent> getComponentType() {
        return this.componentType;
    }

    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("Lumenia", LumeniaConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();
        instance = this;
        this.config.save();

        this.componentType = this.getEntityStoreRegistry().registerComponent(LumeniaComponent.class, "Lumengrid_Lumenia", LumeniaComponent.CODEC);

        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Main::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeLoad);
        this.getEventRegistry().register(RemovedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeRemove);

        this.getCommandRegistry().registerCommand(new OpenJEICommand());

        this.getEntityStoreRegistry().registerSystem(new CheckKeybindSystem());

        CodecMapRegistry.Assets<Interaction, ?> interactionRegistry = getCodecRegistry(Interaction.CODEC);
        interactionRegistry.register("OpenLumeniaBookInteraction", OpenLumeniaBookInteraction.class, OpenLumeniaBookInteraction.CODEC);
    }

    private static void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = event.getAssetMap().getAssetMap();
    }

    private static void onRecipeLoad(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        java.lang.reflect.Method getInputMethod = null;
        BenchRequirement benchReq = new BenchRequirement();
        benchReq.id = CraftingRecipe.FIELDCRAFT_REQUIREMENT;
        benchReq.requiredTierLevel = 1;
        benchReq.type = BenchType.fromValue(0);
        benchReq.categories = new String[] { "Lumenia" };
        BenchRequirement[] benchField = new BenchRequirement[] { benchReq };
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
                        } else if (inputsObj instanceof java.util.Collection) {
                            @SuppressWarnings("unchecked")
                            java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
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
                            } else if (inputsObj instanceof java.util.Collection) {
                                @SuppressWarnings("unchecked")
                                java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
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
//            try {
//                LOGGER.atInfo().log("LUMENIA" + recipe.getId());
//                Field benchRequirementField = CraftingRecipe.class.getDeclaredField("benchRequirement");
//                benchRequirementField.setAccessible(true);
//
//                if (recipe.getBenchRequirement() != null && Arrays.stream(recipe.getBenchRequirement())
//                        .noneMatch(bench -> bench != null && CraftingRecipe.FIELDCRAFT_REQUIREMENT.equals(bench.id))) {
//                    for (BenchRequirement benchRequirement : recipe.getBenchRequirement()) {
//                        BenchRecipeRegistry benchRecipeRegistry = registries.computeIfAbsent(CraftingRecipe.FIELDCRAFT_REQUIREMENT, BenchRecipeRegistry::new);
//                        benchRequirementField.set(recipe, benchField);
//
//                        benchRecipeRegistry.addRecipe(benchRequirement, recipe);
//                        LOGGER.atInfo().log("LUMENIA " + benchRequirement.id + " " + benchRequirement.type.toString() + " " + benchRequirement.requiredTierLevel);
//                    }
//                }
//            } catch (Exception e) {
//                LOGGER.atSevere().log("LUMENIA" +e.getMessage());
//            }

        }
        computeBenchRecipeRegistries();
    }

    private static void computeBenchRecipeRegistries() {
        for (BenchRecipeRegistry registry : registries.values()) {
            registry.recompute();
        }
    }

    private static void onRecipeRemove(RemovedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        for (String recipeId : event.getRemovedAssets()) {
            CraftingRecipe recipe = RECIPES.remove(recipeId);
            if (recipe != null) {
                for (MaterialQuantity output : recipe.getOutputs()) {
                    List<String> recipes = ITEM_TO_RECIPES.get(output.getItemId());
                    if (recipes != null) {
                        recipes.remove(recipeId);
                    }
                }

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
                        } else if (inputsObj instanceof java.util.Collection) {
                            @SuppressWarnings("unchecked")
                            java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
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
