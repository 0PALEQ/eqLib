package com.cookiecraftmods.eqlib.bootstrap.neoforge;

import com.cookiecraftmods.eqlib.bootstrap.Payloads;
import net.neoforged.neoforgespi.locating.*;
import java.util.List;

/** Uses the discovery interface shared by supported NeoForge generations. */
public final class DependencyLocator implements IDependencyLocator {
    @Override public void scanMods(List<IModFile> files, IDiscoveryPipeline pipeline) {
        String loader="net.neoforged.fml.loading.FMLLoader";
        String minecraft=Payloads.minecraftVersion(files,loader);
        java.nio.file.Path adapter=Payloads.select("neoforge",minecraft,Payloads.development(loader));
        pipeline.addPath(adapter,ModFileDiscoveryAttributes.DEFAULT.withDependencyLocator(this),IncompatibleFileReporting.ERROR);
    }
}
