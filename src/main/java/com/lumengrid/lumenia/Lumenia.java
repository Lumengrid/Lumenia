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
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.role.support.RoleStats;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import it.unimi.dsi.fastutil.Pair;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.systems.PositionCacheSystems;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.function.consumer.TriConsumer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.registry.CodecMapRegistry;
import com.hypixel.hytale.server.core.util.Config;
import com.lumengrid.lumenia.commands.OpenJEICommand;
import com.lumengrid.lumenia.interactions.OpenLumeniaBookInteraction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.*;

public class Lumenia extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static Map<String, Item> ITEMS = new HashMap<>();
    public static Map<String, CraftingRecipe> RECIPES = new HashMap<>();
    public static Map<String, List<String>> ITEM_TO_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that produce it
    public static Map<String, List<String>> ITEM_FROM_RECIPES = new HashMap<>(); // Item ID -> Recipe IDs that use it as input
    public static final Map<String, Map<String, MobDropInfo>> MOB_LOOT = new HashMap<>(); // Item ID -> Map of role ID -> MobDropInfo
    private static final Map<String, BenchRecipeRegistry> registries = new Object2ObjectOpenHashMap<>();
    private static boolean discoveredMobLoot = false;
    private static Lumenia instance;
    public final Config<LumeniaConfig> config;
    private com.hypixel.hytale.component.ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, LumeniaComponent> componentType;

    public static Lumenia getInstance() {
        return instance;
    }

    public com.hypixel.hytale.component.ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, LumeniaComponent> getComponentType() {
        return this.componentType;
    }

    public Lumenia(@NonNullDecl JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("Lumenia", LumeniaConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();
        instance = this;
        this.config.save();

        this.componentType = this.getEntityStoreRegistry().registerComponent(LumeniaComponent.class, "Lumengrid_Lumenia", LumeniaComponent.CODEC);

        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Lumenia::onItemAssetLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, Lumenia::onRecipeLoad);
        this.getEventRegistry().register(RemovedAssetsEvent.class, CraftingRecipe.class, Lumenia::onRecipeRemove);
        this.getEventRegistry().registerGlobal(StartWorldEvent.class, Lumenia::onStartWorld);

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
        } catch (NoSuchMethodException _) {
        }

        for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
            String recipeId = recipe.getId();
            if (RECIPES.containsKey(recipeId)) {
                continue;
            }
            RECIPES.put(recipeId, recipe);

            // Track recipes that produce each item
            for (MaterialQuantity output : recipe.getOutputs()) {
                ITEM_TO_RECIPES.computeIfAbsent(output.getItemId(), k -> new ArrayList<>()).add(recipeId);
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
                                ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                            }
                            if (input != null && input.getResourceTypeId() != null) {
                                ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                            }
                        } else if (inputsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<MaterialQuantity> inputs = (List<MaterialQuantity>) inputsObj;
                            if (inputs != null && !inputs.isEmpty()) {
                                for (MaterialQuantity input : inputs) {
                                    if (input == null) {
                                        continue;
                                    }
                                    if (input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                    if (input.getResourceTypeId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                }
                            }
                        } else if (inputsObj instanceof MaterialQuantity[]) {
                            MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                            if (inputs != null && inputs.length > 0) {
                                for (MaterialQuantity input : inputs) {
                                    if (input == null) {
                                        continue;
                                    }
                                    if (input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                    if (input.getResourceTypeId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                }
                            }
                        } else if (inputsObj instanceof java.util.Collection) {
                            @SuppressWarnings("unchecked")
                            java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
                            if (inputs != null && !inputs.isEmpty()) {
                                for (MaterialQuantity input : inputs) {
                                    if (input == null) {
                                        continue;
                                    }
                                    if (input.getItemId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                    if (input.getResourceTypeId() != null) {
                                        ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Lumenia.LOGGER.atSevere().log("Lumenia: onRecipeLoad: " + e.getMessage(), e);
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
                                        if (input == null) {
                                            continue;
                                        }
                                        if (input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                        }
                                        if (input.getResourceTypeId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                                        }
                                    }
                                }
                            } else if (inputsObj instanceof MaterialQuantity[]) {
                                MaterialQuantity[] inputs = (MaterialQuantity[]) inputsObj;
                                if (inputs != null && inputs.length > 0) {
                                    for (MaterialQuantity input : inputs) {
                                        if (input == null) {
                                            continue;
                                        }
                                        if (input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                        }
                                        if (input.getResourceTypeId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
                                        }
                                    }
                                }
                            } else if (inputsObj instanceof java.util.Collection) {
                                @SuppressWarnings("unchecked")
                                java.util.Collection<MaterialQuantity> inputs = (java.util.Collection<MaterialQuantity>) inputsObj;
                                if (inputs != null && !inputs.isEmpty()) {
                                    for (MaterialQuantity input : inputs) {
                                        if (input == null) {
                                            continue;
                                        }
                                        if (input.getItemId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getItemId(), k -> new ArrayList<>()).add(recipeId);
                                        }
                                        if (input.getResourceTypeId() != null) {
                                            ITEM_FROM_RECIPES.computeIfAbsent(input.getResourceTypeId(), k -> new ArrayList<>()).add(recipeId);
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
                } catch (Exception _) {
                }
            }
        }
    }

    private static void onStartWorld(StartWorldEvent event) {
        if (!discoveredMobLoot) {
            discoveredMobLoot = true;
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                LOGGER.atWarning().log("Lumenia: NPCPlugin not available, cannot discover mob loot");
                return;
            }

            try {
                List<String> roles = npcPlugin.getRoleTemplateNames(true);
                if (roles == null || roles.isEmpty()) {
                    LOGGER.atWarning().log("Lumenia: No roles found");
                    return;
                }

                World world = event.getWorld();
                Store<EntityStore> store = world.getEntityStore().getStore();
                
                // Get first role to spawn an entity
                String firstRoleName = roles.get(0);
                int firstRoleIndex = npcPlugin.getIndex(firstRoleName);
                if (firstRoleIndex < 0) {
                    LOGGER.atWarning().log("Lumenia: Invalid first role: " + firstRoleName);
                    return;
                }

                // Spawn a temporary NPC entity
                TransformComponent transformComponent = new TransformComponent();
                Vector3d pos = new Vector3d(transformComponent.getPosition());
                Pair<com.hypixel.hytale.component.Ref<EntityStore>, NPCEntity> npcPair = 
                    npcPlugin.spawnEntity(store, firstRoleIndex, pos, (Vector3f) null, null, (TriConsumer<NPCEntity, com.hypixel.hytale.component.Ref<EntityStore>, Store<EntityStore>>) null);
                NPCEntity npcComponent = npcPair.second();

                // Process each role
                for (String roleName : roles) {
                    try {
                        int roleIndex = npcPlugin.getIndex(roleName);
                        if (roleIndex < 0) {
                            continue;
                        }

                        BuilderInfo builderInfo = npcPlugin.prepareRoleBuilderInfo(roleIndex);
                        if (builderInfo == null) {
                            continue;
                        }

                        Builder<?> roleBuilder = builderInfo.getBuilder();
                        if (roleBuilder == null) {
                            continue;
                        }

                        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                        holder.addComponent(NPCEntity.getComponentType(), npcComponent);
                        @SuppressWarnings("unchecked")
                        Builder<Role> roleBuilderTyped = (Builder<Role>) roleBuilder;
                        BuilderSupport builderSupport = new BuilderSupport(
                            npcPlugin.getBuilderManager(), 
                            npcComponent, 
                            holder, 
                            new ExecutionContext(), 
                            roleBuilderTyped, 
                            (RoleStats) null
                        );
                        
                        Role role = NPCPlugin.buildRole(roleBuilderTyped, builderInfo, builderSupport, roleIndex);
                        if (role == null) {
                            continue;
                        }

                        PositionCacheSystems.initialisePositionCache(role, builderSupport.getStateEvaluator(), 0.0D);
                        
                        // Try to get model ID from builder using reflection
                        String modelId = null;
                        try {
                            java.lang.reflect.Method getModelMethod = roleBuilderTyped.getClass().getMethod("getModel");
                            Object modelObj = getModelMethod.invoke(roleBuilderTyped);
                            if (modelObj != null) {
                                java.lang.reflect.Method getIdMethod = modelObj.getClass().getMethod("getId");
                                Object idObj = getIdMethod.invoke(modelObj);
                                if (idObj != null) {
                                    modelId = idObj.toString();
                                }
                            }
                        } catch (Exception e) {
                            // Try alternative method names
                            String[] methodNames = {"getModelId", "getModelAsset", "model", "modelId"};
                            for (String methodName : methodNames) {
                                try {
                                    java.lang.reflect.Method method = roleBuilderTyped.getClass().getMethod(methodName);
                                    Object result = method.invoke(roleBuilderTyped);
                                    if (result != null) {
                                        modelId = result.toString();
                                        break;
                                    }
                                } catch (Exception _) {
                                }
                            }
                        }
                        
                        String roleTranslationKey = role.getNameTranslationKey();
                        
                        String dropListId = role.getDropListId();
                        if (dropListId != null && !dropListId.isEmpty()) {
                            ItemDropList itemDropList = (ItemDropList) ItemDropList.getAssetMap().getAsset(dropListId);
                            if (itemDropList != null) {
                                LinkedList<ItemDrop> drops = new LinkedList<>();
                                ItemDropContainer container = itemDropList.getContainer();
                                if (container != null) {
                                    container.getAllDrops(drops);
                                    for (ItemDrop drop : drops) {
                                        String itemId = drop.getItemId();
                                        if (itemId != null && !itemId.isEmpty()) {
                                            Map<String, MobDropInfo> dropRates = 
                                                MOB_LOOT.computeIfAbsent(itemId, k -> new HashMap<>());
                                            MobDropInfo dropInfo = new MobDropInfo(
                                                roleName, 
                                                roleTranslationKey, 
                                                modelId,
                                                Map.entry(drop.getQuantityMin(), drop.getQuantityMax())
                                            );
                                            dropRates.put(roleName, dropInfo);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        LOGGER.atWarning().log("Lumenia: Error processing role " + roleName + ": " + e.getMessage());
                    }
                }

                // Clean up the temporary NPC
                npcComponent.remove();
                LOGGER.atInfo().log("Lumenia: Discovered mob loot for " + MOB_LOOT.size() + " items");
            } catch (Exception e) {
                LOGGER.atSevere().log("Lumenia: Error discovering mob loot: " + e.getMessage(), e);
            }
        }
    }
}
