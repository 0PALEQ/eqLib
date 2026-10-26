package com.cookiecraftmods.eqlib.bootstrap.forge;

import com.cookiecraftmods.eqlib.bootstrap.Payloads;
import net.minecraftforge.forgespi.locating.*;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.*;
import java.util.stream.Stream;

/** The legacy discovery interface also exists on newer Forge; there this provider is inert. */
public final class LegacyLocator implements IModLocator {
    private final Map<IModFile, FileSystem> jars = new HashMap<>();
    @Override public List<IModFile> scanMods() {
        try {
            Class.forName("net.minecraftforge.fml.loading.FMLLoader").getMethod("versionInfo");
            return Collections.emptyList();
        } catch (NoSuchMethodException legacy) {
            boolean dev = Payloads.development("net.minecraftforge.fml.loading.FMLLoader");
            Path path = Payloads.select("forge", "1.16.5", dev);
            IModFile file = ModFile.newFMLInstance(path, this);
            try { jars.put(file, FileSystems.newFileSystem(java.net.URI.create("jar:" + path.toUri()), Collections.<String,Object>emptyMap())); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            return Collections.singletonList(file);
        } catch (ClassNotFoundException absent) { throw new IllegalStateException(absent); }
    }
    @Override public String name() { return "eqlib legacy adapter"; }
    @Override public void initArguments(Map<String,?> arguments) {}
    @Override public boolean isValid(IModFile file) { return Files.isRegularFile(file.getFilePath()); }
    @Override public Path findPath(IModFile file, String... names) { return jars.get(file).getPath("/", names); }
    @Override public void scanFile(IModFile file, Consumer<Path> consumer) {
        try (Stream<Path> paths = Files.walk(jars.get(file).getPath("/"))) {
            paths.filter(path -> path.toString().endsWith(".class")).forEach(consumer);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public Optional<Manifest> findManifest(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) { return Optional.ofNullable(jar.getManifest()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
