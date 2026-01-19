package com.lumengrid.lumenia;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class LumeniaConfig {
    public static final BuilderCodec<LumeniaConfig> CODEC;
    public boolean defaultOpenJeiKeybind = true;

    static {
        CODEC = BuilderCodec.builder(LumeniaConfig.class, LumeniaConfig::new)
                .append(new KeyedCodec<>("DefaultOpenJeiKeybind", Codec.BOOLEAN),
                        (o, i) -> o.defaultOpenJeiKeybind = i,
                        (o) -> o.defaultOpenJeiKeybind)
                .add()
                .build();
    }
}
