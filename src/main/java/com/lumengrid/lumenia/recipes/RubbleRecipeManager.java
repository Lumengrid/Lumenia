package com.lumengrid.lumenia.recipes;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.lumengrid.lumenia.Main;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages dynamic rubble recipes for stone items
 * Creates recipes: 1 stone = 4 rubble, 4 rubble = 1 stone
 */
public class RubbleRecipeManager {
    
    private static final String RUBBLE_PREFIX = "Rubble_";
    private static Object logger;
    
    /**
     * Set the logger instance for logging recipe generation
     */
    public static void setLogger(Object loggerInstance) {
        logger = loggerInstance;
    }
    
    private static void log(Level level, String message) {
        return;
//        if (logger != null) {
//            try {
//                // Try to call logger methods using reflection
//                Method logMethod = logger.getClass().getMethod("log", Level.class, String.class);
//                logMethod.invoke(logger, level, message);
//            } catch (Exception e) {
//                // Fallback to System.out
//                System.out.println("[RubbleRecipeManager] " + level.getName() + ": " + message);
//            }
//        } else {
//            System.out.println("[RubbleRecipeManager] " + level.getName() + ": " + message);
//        }
    }
    
    private static void logInfo(String message) {
        log(Level.INFO, message);
    }
    
    private static void logWarning(String message) {
        log(Level.WARNING, message);
    }
    
    private static void logError(String message, Exception e) {
        log(Level.SEVERE, message);
    }
    
    /**
     * Check if an item is a rock item and create rubble recipes for it
     * @return true if any new recipes were created, false otherwise
     */
    public static boolean processStoneItem(String itemId, Item item) {
        if (item == null || itemId == null) {
            return false;
        }

        if (!isRockItem(item)) {
            log(Level.WARNING, itemId + " is NOT a Rock");
            return false;
        }
        log(Level.INFO, itemId + " is a Rock");
        String family = getItemFamily(item);
        if (family == null || family.isEmpty()) {
            log(Level.WARNING, itemId + " empty family");
            return false;
        }

        String rubbleItemId = RUBBLE_PREFIX + family;
        log(Level.INFO, rubbleItemId + " for " + itemId);
        if (!Main.ITEMS.containsKey(rubbleItemId)) {
            return false;
        }

        return createRubbleRecipes(item, itemId, rubbleItemId, family);
    }
    
    /**
     * Check if an item is a rubble item and create reverse recipes for it
     * @return true if any new recipes were created, false otherwise
     */
    public static boolean processRubbleItem(String itemId, Item item) {
        if (item == null || itemId == null || !itemId.startsWith(RUBBLE_PREFIX)) {
            return false;
        }
        
        String family = itemId.substring(RUBBLE_PREFIX.length());
        if (family.isEmpty()) {
            return false;
        }

        String rockItemId = findRockItemByFamily(family);
        if (rockItemId == null) {
            return false;
        }

        Item rockItem = Main.ITEMS.get(rockItemId);
        if (rockItem == null) {
            return false;
        }
        return createRubbleRecipes(item, rockItemId, itemId, family);
    }
    
    /**
     * Find a rock item by its family name
     */
    private static String findRockItemByFamily(String family) {
        for (var entry : Main.ITEMS.entrySet()) {
            Item item = entry.getValue();
            if (item != null && isRockItem(item)) {
                String itemFamily = getItemFamily(item);
                if (family.equals(itemFamily)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
    
    /**
     * Create a MaterialQuantity using reflection
     */
    private static MaterialQuantity createMaterialQuantity(String itemId, int quantity) {
        try {
            try {
                Constructor<MaterialQuantity> constructor = MaterialQuantity.class.getConstructor(String.class, int.class);
                return constructor.newInstance(itemId, quantity);
            } catch (NoSuchMethodException e) {
                // Try other constructors
            }
            
            try {
                Constructor<MaterialQuantity> constructor = MaterialQuantity.class.getConstructor(String.class, Integer.class);
                return constructor.newInstance(itemId, Integer.valueOf(quantity));
            } catch (NoSuchMethodException e) {
                // Try factory methods
            }
            
            Method[] methods = MaterialQuantity.class.getMethods();
            for (Method method : methods) {
                if ((method.getName().equals("of") || method.getName().equals("create") || 
                     method.getName().equals("newInstance")) && method.getParameterCount() == 2) {
                    try {
                        return (MaterialQuantity) method.invoke(null, itemId, quantity);
                    } catch (Exception e) {
                        // Try next method
                    }
                }
            }

            try {
                Constructor<MaterialQuantity> noArgConstructor = MaterialQuantity.class.getDeclaredConstructor();
                noArgConstructor.setAccessible(true);
                MaterialQuantity mq = noArgConstructor.newInstance();
                setField(mq, "itemId", itemId);
                setField(mq, "quantity", quantity);
                setField(mq, "amount", quantity);
                return mq;
            } catch (Exception e) {
                // Failed
            }
            
        } catch (Exception e) {
            // Silently fail
        }
        
        return null;
    }
    
    /**
     * Create both recipes: stone -> rubble and rubble -> stone
     * @param item The item being processed (either stone or rubble)
     * @param stoneItemId The stone item ID
     * @param rubbleItemId The rubble item ID
     * @param material The material family name
     * @return true if any new recipes were created, false otherwise
     */
    private static boolean createRubbleRecipes(Item item, String stoneItemId, String rubbleItemId, String material) {
        logInfo("Creating rubble recipes for: " + stoneItemId + " <-> " + rubbleItemId + " (family: " + material + ")");
        
        List<CraftingRecipe> recipesToRegister = new ArrayList<>();
        
        // Recipe 1: 1 stone = 4 rubble (crafted in hand)
        String recipeId1 = "lumenia_rubble_from_stone_" + material.toLowerCase();
        if (Main.RECIPES.containsKey(recipeId1)) {
            logInfo("Recipe already exists: " + recipeId1);
        } else {
            logInfo("Creating recipe: " + recipeId1 + " (1x " + stoneItemId + " -> 4x " + rubbleItemId + ")");
            MaterialQuantity input1 = createMaterialQuantity(stoneItemId, 1);
            MaterialQuantity output1 = createMaterialQuantity(rubbleItemId, 4);
            
            if (input1 == null || output1 == null) {
                logError("Failed to create MaterialQuantity for recipe " + recipeId1, null);
            } else {
                CraftingRecipe recipe1 = createRecipe(
                    recipeId1,
                    Arrays.asList(input1),
                    Arrays.asList(output1),
                    null
                );
                
                if (recipe1 != null) {
                    Main.RECIPES.put(recipeId1, recipe1);
                    Main.ITEM_TO_RECIPES.computeIfAbsent(rubbleItemId, k -> new ArrayList<>()).add(recipeId1);
                    Main.ITEM_FROM_RECIPES.computeIfAbsent(stoneItemId, k -> new ArrayList<>()).add(recipeId1);
                    recipesToRegister.add(recipe1);
                    logInfo("Successfully created recipe: " + recipeId1);
                } else {
                    logError("Failed to create CraftingRecipe: " + recipeId1, null);
                }
            }
        }

        if (!recipesToRegister.isEmpty()) {
            try {
                Method collectMethod = Item.class.getMethod("collectRecipesToGenerate", Collection.class);
                
                // Register with the item being processed
                if (item != null) {
                    collectMethod.invoke(item, recipesToRegister);
                    logInfo("Registered " + recipesToRegister.size() + " recipes with item");
                }
                
                // Also register with the other item (stone or rubble) if it exists
                Item stoneItem = Main.ITEMS.get(stoneItemId);
                Item rubbleItem = Main.ITEMS.get(rubbleItemId);
                
                if (stoneItem != null && stoneItem != item) {
                    collectMethod.invoke(stoneItem, recipesToRegister);
                    logInfo("Registered " + recipesToRegister.size() + " recipes with stone item");
                }
                
                if (rubbleItem != null && rubbleItem != item) {
                    collectMethod.invoke(rubbleItem, recipesToRegister);
                    logInfo("Registered " + recipesToRegister.size() + " recipes with rubble item");
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist - recipes are already in RECIPES map, so this is optional
                logWarning("collectRecipesToGenerate method not found on Item class");
            } catch (Exception e) {
                logError("Failed to register recipes with item", e);
            }
        }
        
        return !recipesToRegister.isEmpty();
    }
    
    /**
     * Create a CraftingRecipe using reflection
     */
    private static CraftingRecipe createRecipe(String recipeId, List<MaterialQuantity> inputs, 
                                               List<MaterialQuantity> outputs, 
                                               com.hypixel.hytale.protocol.BenchRequirement[] benchRequirement) {
        try {
            // Try to find a constructor or builder for CraftingRecipe
            // First, try common constructor patterns
            
            // Pattern 1: Constructor with id, inputs, outputs, bench
            Constructor<?>[] constructors = CraftingRecipe.class.getConstructors();
            for (Constructor<?> constructor : constructors) {
                try {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    Object[] args = new Object[paramTypes.length];
                    
                    // Try to match parameters
                    int argIndex = 0;
                    for (int i = 0; i < paramTypes.length; i++) {
                        Class<?> paramType = paramTypes[i];
                        if (String.class.isAssignableFrom(paramType) && argIndex == 0) {
                            args[i] = recipeId;
                            argIndex++;
                        } else if (List.class.isAssignableFrom(paramType) || 
                                   paramType.isArray() && MaterialQuantity.class.isAssignableFrom(paramType.getComponentType())) {
                            // Inputs
                            if (inputs != null) {
                                if (List.class.isAssignableFrom(paramType)) {
                                    args[i] = inputs;
                                } else {
                                    args[i] = inputs.toArray(new MaterialQuantity[0]);
                                }
                            } else {
                                args[i] = paramType.isArray() ? new MaterialQuantity[0] : new ArrayList<MaterialQuantity>();
                            }
                        } else if (List.class.isAssignableFrom(paramType) || 
                                  (paramType.isArray() && MaterialQuantity.class.isAssignableFrom(paramType.getComponentType()))) {
                            // Outputs
                            if (outputs != null) {
                                if (List.class.isAssignableFrom(paramType)) {
                                    args[i] = outputs;
                                } else {
                                    args[i] = outputs.toArray(new MaterialQuantity[0]);
                                }
                            } else {
                                args[i] = paramType.isArray() ? new MaterialQuantity[0] : new ArrayList<MaterialQuantity>();
                            }
                        } else if (paramType.isArray() && 
                                  paramType.getComponentType().getName().contains("BenchRequirement")) {
                            // Bench requirement
                            args[i] = benchRequirement != null ? benchRequirement : new Object[0];
                        } else {
                            args[i] = null;
                        }
                    }
                    
                    return (CraftingRecipe) constructor.newInstance(args);
                } catch (Exception e) {
                    // Try next constructor
                    continue;
                }
            }
            
            // Pattern 2: Try using a builder or factory method
            Method[] methods = CraftingRecipe.class.getMethods();
            for (Method method : methods) {
                if (method.getName().equals("builder") || method.getName().equals("create") || 
                    method.getName().equals("of") || method.getName().equals("newRecipe")) {
                    try {
                        if (method.getParameterCount() == 0) {
                            Object builder = method.invoke(null);
                            // Try to call builder methods
                            return buildRecipeWithBuilder(builder, recipeId, inputs, outputs, benchRequirement);
                        }
                    } catch (Exception e) {
                        // Try next method
                    }
                }
            }
            
            // Pattern 3: Try creating with minimal parameters and setting fields via reflection
            // This is a last resort
            try {
                Constructor<?> noArgConstructor = CraftingRecipe.class.getDeclaredConstructor();
                noArgConstructor.setAccessible(true);
                CraftingRecipe recipe = (CraftingRecipe) noArgConstructor.newInstance();
                
                // Try to set fields via reflection
                setField(recipe, "id", recipeId);
                setField(recipe, "inputs", inputs != null ? inputs : new ArrayList<>());
                setField(recipe, "outputs", outputs != null ? outputs : new ArrayList<>());
                setField(recipe, "benchRequirement", benchRequirement);
                
                return recipe;
            } catch (Exception e) {
                // Failed
            }
            
        } catch (Exception e) {
            // Silently fail
        }
        
        return null;
    }
    
    /**
     * Try to build a recipe using a builder pattern
     */
    private static CraftingRecipe buildRecipeWithBuilder(Object builder, String recipeId, 
                                                         List<MaterialQuantity> inputs,
                                                         List<MaterialQuantity> outputs,
                                                         com.hypixel.hytale.protocol.BenchRequirement[] benchRequirement) {
        try {
            Class<?> builderClass = builder.getClass();
            Method[] methods = builderClass.getMethods();
            
            // Try common builder method names
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if (methodName.contains("id") && method.getParameterCount() == 1 && 
                    String.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    builder = method.invoke(builder, recipeId);
                } else if ((methodName.contains("input") || methodName.contains("ingredient")) && 
                          method.getParameterCount() == 1) {
                    if (inputs != null && !inputs.isEmpty()) {
                        Class<?> paramType = method.getParameterTypes()[0];
                        if (List.class.isAssignableFrom(paramType)) {
                            builder = method.invoke(builder, inputs);
                        } else if (paramType.isArray()) {
                            builder = method.invoke(builder, (Object) inputs.toArray(new MaterialQuantity[0]));
                        }
                    }
                } else if ((methodName.contains("output") || methodName.contains("result")) && 
                          method.getParameterCount() == 1) {
                    if (outputs != null && !outputs.isEmpty()) {
                        Class<?> paramType = method.getParameterTypes()[0];
                        if (List.class.isAssignableFrom(paramType)) {
                            builder = method.invoke(builder, outputs);
                        } else if (paramType.isArray()) {
                            builder = method.invoke(builder, (Object) outputs.toArray(new MaterialQuantity[0]));
                        }
                    }
                } else if (methodName.contains("bench") && method.getParameterCount() == 1) {
                    if (benchRequirement != null) {
                        builder = method.invoke(builder, (Object) benchRequirement);
                    }
                } else if (methodName.contains("build") || methodName.contains("create")) {
                    return (CraftingRecipe) method.invoke(builder);
                }
            }
        } catch (Exception e) {
            // Builder pattern failed
        }
        
        return null;
    }
    
    /**
     * Set a field value using reflection
     */
    private static void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            // Field doesn't exist or can't be set
        }
    }
    
    /**
     * Check if an item is a rock item by checking if it has the "Type" tag with value "Rock"
     */
    private static boolean isRockItem(Item item) {
        if (item == null) {
            return false;
        }
        
        try {
            AssetExtraInfo.Data itemData = item.getData();
            if (itemData == null) {
                return false;
            }
 
            java.util.Map<String, String[]> rawTags = itemData.getRawTags();
            if (rawTags == null) {
                return false;
            }
            
            // Check if "Type" tag exists
            if (rawTags.containsKey("Type")) {
                String[] typeValues = rawTags.get("Type");
                if (typeValues != null) {
                    for (String value : typeValues) {
                        if ("Rock".equals(value)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail
            return false;
        }
        
        return false;
    }
    
    /**
     * Get the Family property from an item by checking tags
     */
    private static String getItemFamily(Item item) {
        if (item == null) {
            return null;
        }
        
        try {
            AssetExtraInfo.Data itemData = item.getData();
            if (itemData == null) {
                return null;
            }
            
            java.util.Map<String, String[]> rawTags = itemData.getRawTags();
            if (rawTags == null) {
                return null;
            }
            
            // Check if "Family" tag exists
            if (rawTags.containsKey("Family")) {
                String[] familyValues = rawTags.get("Family");
                if (familyValues != null && familyValues.length > 0) {
                    // Return the first family value
                    return familyValues[0];
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
        
        return null;
    }
}
