package com.cookiecraftmods.eqlib.bootstrap.forge;

import com.cookiecraftmods.eqlib.bootstrap.Payloads;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import java.util.*;

public final class DependencyLocator extends AbstractJarFileDependencyLocator {
    @Override public String name() { return "eqlib selected adapter"; }
    @Override public void initArguments(Map<String,?> arguments) {}
    @Override public List<IModFile> scanMods(Iterable<IModFile> files) {
        String loader="net.minecraftforge.fml.loading.FMLLoader";
        String minecraft=Payloads.minecraftVersion(files,loader);
        java.nio.file.Path adapter=Payloads.select("forge",minecraft,Payloads.development(loader));
        net.minecraftforge.forgespi.locating.IModLocator.ModFileOrException result=createMod(adapter);
        if (result.ex()!=null) throw result.ex();
        return Collections.singletonList(result.file());
    }
}
