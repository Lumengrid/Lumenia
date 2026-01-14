package com.lumengrid.lumenia.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lumengrid.lumenia.Main;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for formatting and sending recipe information as messages
 */
public class RecipeMessageHelper {

    /**
     * Formats and sends all recipe information for an item to a player
     */
    public static void sendRecipeInfo(Player player, String itemId) {
        Item item = Main.ITEMS.get(itemId);
        if (item == null) {
            player.sendMessage(Message.raw("Item not found: " + itemId).color(Color.RED));
            return;
        }

        var message = MessageHelper.multiLine();

        // Get player language
        Ref<EntityStore> ref = player.getReference();
        String language = "en-US";
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                language = playerRef.getLanguage();
            }
        }

        // Item header
        message.append(Message.raw("=== Recipe Information ===").color("#FFD700").bold(true)).nl();
        message.append(Message.translation(item.getTranslationKey()).bold(true).color(Color.YELLOW)).nl();
        message.append(Message.raw("Item ID: ").color("#93844c").bold(true))
               .append(Message.raw(itemId).color(Color.WHITE)).nl();
        message.separator();

        // Get all recipes that produce this item
        List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());

        if (recipeIds.isEmpty()) {
            message.append(Message.raw("No recipes found for this item.").color(Color.GRAY).italic(true)).nl();
        } else {
            message.append(Message.raw("Recipes (" + recipeIds.size() + "):").color("#00FFFF").bold(true)).nl().nl();

            int recipeNum = 1;
            for (String recipeId : recipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) continue;

                // Format recipe and merge into main message
                MessageHelper.ML recipeML = formatRecipe(recipe, recipeNum, recipeIds.size(), language);
                message.append(recipeML.build());
                recipeNum++;
            }
        }

        // Also show what this item is used in (recipes that use it as input)
        // We need to verify the item is actually in the recipe inputs
        List<String> usesRecipeIds = Main.ITEM_FROM_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (!usesRecipeIds.isEmpty()) {
            List<String> verifiedRecipeIds = new ArrayList<>();
            
            // Verify each recipe actually uses this item as input
            for (String recipeId : usesRecipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe == null) continue;

                // Verify the item is actually in this recipe's inputs
                Object inputsObj = null;
                try {
                    java.lang.reflect.Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
                    inputsObj = getInputMethod.invoke(recipe);
                } catch (NoSuchMethodException e) {
                    // Fallback to other method names if getInput doesn't exist
                    String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
                    for (String methodName : methodNames) {
                        try {
                            java.lang.reflect.Method fallbackMethod = CraftingRecipe.class.getMethod(methodName);
                            inputsObj = fallbackMethod.invoke(recipe);
                            if (inputsObj != null) break;
                        } catch (NoSuchMethodException ignored) {
                            // Try next method name
                        } catch (Exception ignored) {
                            // Failed to invoke method
                        }
                    }
                } catch (Exception e) {
                    // Failed to invoke getInput method
                }

                // Check if item is actually in the inputs
                boolean itemFoundInInputs = false;
                if (inputsObj != null) {
                    if (inputsObj instanceof MaterialQuantity) {
                        MaterialQuantity input = (MaterialQuantity) inputsObj;
                        if (input != null && itemId.equals(input.getItemId())) {
                            itemFoundInInputs = true;
                        }
                    } else if (inputsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                        if (inputs != null && !inputs.isEmpty()) {
                            for (MaterialQuantity input : inputs) {
                                if (input != null && itemId.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        }
                    } else if (inputsObj instanceof MaterialQuantity[]) {
                        MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                        if (inputs != null && inputs.length > 0) {
                            for (MaterialQuantity input : inputs) {
                                if (input != null && itemId.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        }
                    } else if (inputsObj instanceof java.util.Collection) {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
                        if (inputs != null && !inputs.isEmpty()) {
                            for (MaterialQuantity input : inputs) {
                                if (input != null && itemId.equals(input.getItemId())) {
                                    itemFoundInInputs = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (itemFoundInInputs) {
                    verifiedRecipeIds.add(recipeId);
                }
            }

            if (!verifiedRecipeIds.isEmpty()) {
                message.separator();
                message.append(Message.raw("Used In Recipes (" + verifiedRecipeIds.size() + "):").color(Color.GREEN).bold(true)).nl().nl();

                int usesNum = 1;
                for (String recipeId : verifiedRecipeIds) {
                    CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                    if (recipe == null) continue;

                    // Format uses recipe and merge into main message
                    MessageHelper.ML usesML = formatRecipeUses(recipe, usesNum, verifiedRecipeIds.size(), itemId, language);
                    message.append(usesML.build());
                    usesNum++;
                }
            }
        }

        player.sendMessage(message.build());
    }

    /**
     * Formats a single recipe for display
     */
    private static MessageHelper.ML formatRecipe(CraftingRecipe recipe, int recipeNum, int totalRecipes, String language) {
        var ml = MessageHelper.multiLine();

        ml.append(Message.raw("Recipe #" + recipeNum + " of " + totalRecipes).color(Color.DARK_GRAY).italic(true)).nl();
        ml.append(Message.raw("Recipe ID: ").color("#93844c").bold(true))
          .append(Message.raw(recipe.getId()).color(Color.WHITE)).nl();

        // Bench requirement
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            ml.append(Message.raw("Bench: ").color("#93844c").bold(true))
              .append(Message.raw(formatBench(bench.id) + " Tier " + bench.requiredTierLevel).color(Color.WHITE)).nl();
        }

        // Recipe inputs (ingredients)
        ml.append(Message.raw("Ingredients:").color("#93844c").bold(true)).nl();

        // Try to get inputs using reflection with fallback methods
        Object inputsObj = null;
        try {
            java.lang.reflect.Method getInputMethod = CraftingRecipe.class.getMethod("getInput");
            inputsObj = getInputMethod.invoke(recipe);
        } catch (NoSuchMethodException e) {
            // Fallback to other method names if getInput doesn't exist
            String[] methodNames = {"getInputs", "getIngredients", "getMaterials", "getRecipeInputs", "getRequiredMaterials"};
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method fallbackMethod = CraftingRecipe.class.getMethod(methodName);
                    inputsObj = fallbackMethod.invoke(recipe);
                    if (inputsObj != null) break;
                } catch (NoSuchMethodException ignored) {
                    // Try next method name
                } catch (Exception ignored) {
                    // Failed to invoke method
                }
            }
        } catch (Exception e) {
            // Failed to invoke getInput method
        }

        // Process inputs whether they're a single MaterialQuantity, List, array, or Collection
        boolean hasInputs = false;
        if (inputsObj != null) {
            if (inputsObj instanceof MaterialQuantity) {
                // getInput() returns a single MaterialQuantity
                MaterialQuantity input = (MaterialQuantity) inputsObj;
                if (input != null && input.getItemId() != null) {
                    Item inputItem = Main.ITEMS.get(input.getItemId());
                    String inputName = inputItem != null ?
                        I18nModule.get().getMessage(language, inputItem.getTranslationKey()) :
                        input.getItemId();
                    ml.append(Message.raw("  • ").color(Color.GRAY))
                      .append(Message.raw(inputName).color(Color.WHITE))
                      .append(Message.raw(" x" + input.getQuantity()).color(Color.YELLOW)).nl();
                    hasInputs = true;
                }
            } else if (inputsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null && input.getItemId() != null) {
                            Item inputItem = Main.ITEMS.get(input.getItemId());
                            String inputName = inputItem != null ?
                                I18nModule.get().getMessage(language, inputItem.getTranslationKey()) :
                                input.getItemId();
                            ml.append(Message.raw("  • ").color(Color.GRAY))
                              .append(Message.raw(inputName).color(Color.WHITE))
                              .append(Message.raw(" x" + input.getQuantity()).color(Color.YELLOW)).nl();
                            hasInputs = true;
                        }
                    }
                }
            } else if (inputsObj instanceof MaterialQuantity[]) {
                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                if (inputs != null && inputs.length > 0) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null && input.getItemId() != null) {
                            Item inputItem = Main.ITEMS.get(input.getItemId());
                            String inputName = inputItem != null ?
                                I18nModule.get().getMessage(language, inputItem.getTranslationKey()) :
                                input.getItemId();
                            ml.append(Message.raw("  • ").color(Color.GRAY))
                              .append(Message.raw(inputName).color(Color.WHITE))
                              .append(Message.raw(" x" + input.getQuantity()).color(Color.YELLOW)).nl();
                            hasInputs = true;
                        }
                    }
                }
            } else if (inputsObj instanceof java.util.Collection) {
                @SuppressWarnings("unchecked")
                java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
                if (inputs != null && !inputs.isEmpty()) {
                    for (MaterialQuantity input : inputs) {
                        if (input != null && input.getItemId() != null) {
                            Item inputItem = Main.ITEMS.get(input.getItemId());
                            String inputName = inputItem != null ?
                                I18nModule.get().getMessage(language, inputItem.getTranslationKey()) :
                                input.getItemId();
                            ml.append(Message.raw("  • ").color(Color.GRAY))
                              .append(Message.raw(inputName).color(Color.WHITE))
                              .append(Message.raw(" x" + input.getQuantity()).color(Color.YELLOW)).nl();
                            hasInputs = true;
                        }
                    }
                }
            }
        }

        if (!hasInputs) {
            ml.append(Message.raw("  (Ingredients not available - API may not expose recipe inputs)").color(Color.GRAY).italic(true)).nl();
        }

        // Recipe outputs
        ml.append(Message.raw("Outputs:").color("#93844c").bold(true)).nl();
        Object outputsObj = recipe.getOutputs();
        if (outputsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<MaterialQuantity> outputs = (List<MaterialQuantity>) outputsObj;
            if (outputs != null && !outputs.isEmpty()) {
                for (MaterialQuantity output : outputs) {
                    Item outputItem = Main.ITEMS.get(output.getItemId());
                    String outputName = outputItem != null ?
                        I18nModule.get().getMessage(language, outputItem.getTranslationKey()) :
                        output.getItemId();
                    ml.append(Message.raw("  • ").color(Color.GRAY))
                      .append(Message.raw(outputName).color(Color.WHITE).bold(true))
                      .append(Message.raw(" x" + output.getQuantity()).color(Color.GREEN).bold(true)).nl();
                }
            } else {
                ml.append(Message.raw("  (No outputs)").color(Color.GRAY).italic(true)).nl();
            }
        } else if (outputsObj instanceof MaterialQuantity[]) {
            MaterialQuantity[] outputs = (MaterialQuantity[]) outputsObj;
            if (outputs != null && outputs.length > 0) {
                for (MaterialQuantity output : outputs) {
                    Item outputItem = Main.ITEMS.get(output.getItemId());
                    String outputName = outputItem != null ?
                        I18nModule.get().getMessage(language, outputItem.getTranslationKey()) :
                        output.getItemId();
                    ml.append(Message.raw("  • ").color(Color.GRAY))
                      .append(Message.raw(outputName).color(Color.WHITE).bold(true))
                      .append(Message.raw(" x" + output.getQuantity()).color(Color.GREEN).bold(true)).nl();
                }
            } else {
                ml.append(Message.raw("  (No outputs)").color(Color.GRAY).italic(true)).nl();
            }
        } else {
            ml.append(Message.raw("  (No outputs)").color(Color.GRAY).italic(true)).nl();
        }

        ml.nl(); // Empty line between recipes
        return ml;
    }

    /**
     * Formats how an item is used in a recipe
     */
    private static MessageHelper.ML formatRecipeUses(CraftingRecipe recipe, int usesNum, int totalUses, String itemId, String language) {
        var ml = MessageHelper.multiLine();

        ml.append(Message.raw("Uses Recipe #" + usesNum + " of " + totalUses).color(Color.DARK_GRAY).italic(true)).nl();

        // Show what this recipe produces
        ml.append(Message.raw("Produces: ").color("#93844c").bold(true));
        Object outputsObj = recipe.getOutputs();
        List<Message> outputMessages = new ArrayList<>();
        if (outputsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<MaterialQuantity> outputsList = (List<MaterialQuantity>) outputsObj;
            if (outputsList != null && !outputsList.isEmpty()) {
                for (MaterialQuantity output : outputsList) {
                    Item outputItem = Main.ITEMS.get(output.getItemId());
                    String outputName = outputItem != null ?
                        I18nModule.get().getMessage(language, outputItem.getTranslationKey()) :
                        output.getItemId();
                    outputMessages.add(Message.raw(outputName + " x" + output.getQuantity()).color(Color.WHITE));
                }
            }
        } else if (outputsObj instanceof MaterialQuantity[]) {
            MaterialQuantity[] outputsArray = (MaterialQuantity[]) outputsObj;
            if (outputsArray != null && outputsArray.length > 0) {
                for (MaterialQuantity output : outputsArray) {
                    Item outputItem = Main.ITEMS.get(output.getItemId());
                    String outputName = outputItem != null ?
                        I18nModule.get().getMessage(language, outputItem.getTranslationKey()) :
                        output.getItemId();
                    outputMessages.add(Message.raw(outputName + " x" + output.getQuantity()).color(Color.WHITE));
                }
            }
        }
        if (!outputMessages.isEmpty()) {
            ml.append(Message.join(outputMessages.toArray(new Message[0]))).nl();
        }

        // Show bench requirement
        if (recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
            BenchRequirement bench = recipe.getBenchRequirement()[0];
            ml.append(Message.raw("  Requires: ").color(Color.GRAY))
              .append(Message.raw(formatBench(bench.id) + " Tier " + bench.requiredTierLevel).color(Color.WHITE)).nl();
        }

        ml.nl();
        return ml;
    }

    private static String formatBench(String name) {
        name = name.replaceAll("_", " ");
        if (!name.contains("Bench")) {
            name += " Bench";
        }
        return name;
    }

    /**
     * Sends a simplified recipe message (just the essentials)
     */
    public static void sendSimpleRecipeInfo(Player player, String itemId) {
        Item item = Main.ITEMS.get(itemId);
        if (item == null) {
            player.sendMessage(Message.raw("Item not found: " + itemId).color(Color.RED));
            return;
        }

        var message = MessageHelper.multiLine();
        message.append(Message.translation(item.getTranslationKey()).bold(true).color(Color.YELLOW)).nl();

        List<String> recipeIds = Main.ITEM_TO_RECIPES.getOrDefault(itemId, Collections.emptyList());
        if (recipeIds.isEmpty()) {
            message.append(Message.raw("No recipes available.").color(Color.GRAY)).nl();
        } else {
            message.append(Message.raw("Can be crafted in:").color("#00FFFF")).nl();
            Set<String> benches = new HashSet<>();
            for (String recipeId : recipeIds) {
                CraftingRecipe recipe = Main.RECIPES.get(recipeId);
                if (recipe != null && recipe.getBenchRequirement() != null && recipe.getBenchRequirement().length > 0) {
                    BenchRequirement bench = recipe.getBenchRequirement()[0];
                    benches.add(formatBench(bench.id) + " Tier " + bench.requiredTierLevel);
                }
            }
            for (String bench : benches) {
                message.append(Message.raw("  • ").color(Color.GRAY))
                       .append(Message.raw(bench).color(Color.WHITE)).nl();
            }
        }

        player.sendMessage(message.build());
    }
}
