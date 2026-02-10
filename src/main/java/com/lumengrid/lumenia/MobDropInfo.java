package com.lumengrid.lumenia;

import java.util.Map;

public class MobDropInfo {
    public final String roleId;
    public final String roleTranslationKey;
    public final String modelId;
    public final Map.Entry<Integer, Integer> quantities; // min, max

    public MobDropInfo(String roleId, String roleTranslationKey, String modelId, Map.Entry<Integer, Integer> quantities) {
        this.roleId = roleId;
        this.roleTranslationKey = roleTranslationKey;
        this.modelId = modelId;
        this.quantities = quantities;
    }
}
