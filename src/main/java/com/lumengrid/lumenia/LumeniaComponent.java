package com.lumengrid.lumenia;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public class LumeniaComponent implements Component<EntityStore> {
    public static final BuilderCodec<LumeniaComponent> CODEC;
    public boolean openJeiKeybind = true;
    public String jeiKeybind = "crouching"; // "walking", "crouching", "running"
    public boolean wasWalkingLastTick = false;

    public static ComponentType<EntityStore, LumeniaComponent> getComponentType() {
        return Lumenia.getInstance().getComponentType();
    }

    private LumeniaComponent() {
        this.openJeiKeybind = Lumenia.getInstance().config.get().defaultOpenJeiKeybind;
        this.jeiKeybind = "crouching";
    }

    public LumeniaComponent(boolean openJeiKeybind) {
        this.openJeiKeybind = openJeiKeybind;
        this.jeiKeybind = "crouching";
    }

    public LumeniaComponent(boolean openJeiKeybind, String jeiKeybind) {
        this.openJeiKeybind = openJeiKeybind;
        this.jeiKeybind = jeiKeybind != null ? jeiKeybind : "crouching";
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        LumeniaComponent clone = new LumeniaComponent(this.openJeiKeybind, this.jeiKeybind);
        clone.wasWalkingLastTick = this.wasWalkingLastTick;
        return clone;
    }

    static {
        CODEC = BuilderCodec.builder(LumeniaComponent.class, LumeniaComponent::new)
                .append(new KeyedCodec<>("OpenJeiKeybind", Codec.BOOLEAN),
                        (o, v) -> o.openJeiKeybind = v,
                        (o) -> o.openJeiKeybind)
                .add()
                .append(new KeyedCodec<>("JeiKeybind", Codec.STRING),
                        (o, v) -> o.jeiKeybind = v != null ? v : "crouching",
                        (o) -> o.jeiKeybind != null ? o.jeiKeybind : "crouching")
                .add()
                .build();
    }
}
