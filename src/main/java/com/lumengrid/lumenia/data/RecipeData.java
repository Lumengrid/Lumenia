package com.lumengrid.lumenia.data;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure to hold recipe information (inputs and outputs)
 */
public class RecipeData {
    private final List<MaterialQuantity> inputs;
    private final List<MaterialQuantity> outputs;

    public RecipeData(List<MaterialQuantity> inputs, List<MaterialQuantity> outputs) {
        this.inputs = inputs != null ? new ArrayList<>(inputs) : new ArrayList<>();
        this.outputs = outputs != null ? new ArrayList<>(outputs) : new ArrayList<>();
    }

    public List<MaterialQuantity> getInputs() {
        return inputs;
    }

    public List<MaterialQuantity> getOutputs() {
        return outputs;
    }

    /**
     * Check if this recipe produces the given item
     */
    public boolean producesItem(String itemId) {
        return outputs.stream().anyMatch(output -> output.getItemId().equals(itemId));
    }

    /**
     * Check if this recipe uses the given item as input
     */
    public boolean usesItem(String itemId) {
        return inputs.stream().anyMatch(input -> input.getItemId().equals(itemId));
    }
}
