package com.lumengrid.lumenia.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class LumeniaConfig {

    public static final BuilderCodec<LumeniaConfig> CODEC = BuilderCodec.builder(LumeniaConfig.class, LumeniaConfig::new)
            .append(new KeyedCodec<String>("KeyboardShortcut", Codec.STRING),
                    (config, value, extraInfo) -> config.keyboardShortcut = value,
                    (config, extraInfo) -> config.keyboardShortcut).add()
            .build();

    private String keyboardShortcut = "L";

    public LumeniaConfig() {
    }

    public String getKeyboardShortcut() {
        return keyboardShortcut;
    }

    public void setKeyboardShortcut(String keyboardShortcut) {
        this.keyboardShortcut = keyboardShortcut;
    }
}
