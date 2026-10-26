package com.cookiecraftmods.eqlib.bootstrap.fabric;

import com.cookiecraftmods.eqlib.bootstrap.Payloads;
import com.google.gson.*;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

/** Select the game adapter before Mixin resolves any Minecraft-dependent implementation. */
public final class MixinSelector implements IMixinConfigPlugin {
    private JsonObject configuration;
    @Override public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        String version = loader.getModContainer("minecraft").orElseThrow(() -> new IllegalStateException("Minecraft is missing")).getMetadata().getVersion().getFriendlyString();
        Path adapter = Payloads.select("fabric", version, loader.isDevelopmentEnvironment());
        try (JarFile jar = new JarFile(adapter.toFile()); Reader reader = new InputStreamReader(jar.getInputStream(jar.getJarEntry("eqlib.mixins.json")), StandardCharsets.UTF_8)) {
            configuration = new JsonParser().parse(reader).getAsJsonObject();
        } catch (IOException failure) { throw new IllegalStateException("Cannot read eqlib mixins", failure); }
        FabricLauncherBase.getLauncher().addToClassPath(adapter);
    }
    @Override public String getRefMapperConfig() { return configuration.has("refmap") ? configuration.get("refmap").getAsString() : null; }
    @Override public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        for (JsonElement name : configuration.getAsJsonArray("mixins")) mixins.add(name.getAsString());
        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            for (JsonElement name : configuration.getAsJsonArray("client")) mixins.add(name.getAsString());
        }
        return mixins;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
