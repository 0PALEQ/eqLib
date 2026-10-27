package com.cookiecraftmods.eqlib.bootstrap;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** Early-loader code: Java 8, no Minecraft classes, and no game-class initialization. */
public final class Payloads {
    private Payloads() {}
    private static final Properties INDEX = new Properties();
    static {
        try (InputStream input = Payloads.class.getResourceAsStream("/META-INF/eqlib/adapters.properties")) {
            if (input == null) throw new IOException("Missing eqlib adapter index");
            INDEX.load(input);
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }

    public static boolean development(String loaderClass) {
        String override = System.getProperty("eqlib.development");
        if (override != null) return Boolean.parseBoolean(override);
        try {
            Class<?> type = Class.forName(loaderClass);
            Method method = type.getMethod("isProduction");
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null : type.getMethod("getCurrent").invoke(null);
            return !((Boolean) method.invoke(receiver));
        } catch (NoSuchMethodException absent) {
            return false;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot determine the eqlib loader environment", failure);
        }
    }

    public static String minecraftVersion(Iterable<?> files, String loaderClass) {
        try {
            for (Object file : files) {
                for (Object info : (Iterable<?>) file.getClass().getMethod("getModInfos").invoke(file)) {
                    if ("minecraft".equals(info.getClass().getMethod("getModId").invoke(info))) {
                        return info.getClass().getMethod("getVersion").invoke(info).toString();
                    }
                }
            }
            Class<?> type = Class.forName(loaderClass);
            Object version = type.getMethod("versionInfo").invoke(null);
            return version.getClass().getMethod("mcVersion").invoke(version).toString();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot identify Minecraft before loading eqlib", failure);
        }
    }

    private static byte[] withCore(byte[] adapter) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            // Keep the adapter manifest first for Forge's streaming JAR reader.
            for (String resource : new String[] {null, "/META-INF/eqlib/core.jar"}) {
                InputStream raw = resource == null ? new ByteArrayInputStream(adapter) : Payloads.class.getResourceAsStream(resource);
                if (raw == null) throw new IOException("Missing eqlib core");
                // ZipInputStream preserves the adapter manifest as a regular entry.
                try (java.util.zip.ZipInputStream input = new java.util.zip.ZipInputStream(raw)) {
                    for (java.util.zip.ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                        if (entry.isDirectory() || !names.add(entry.getName())) continue;
                        JarEntry copy = new JarEntry(entry.getName());
                        copy.setTime(0);
                        jar.putNextEntry(copy);
                        byte[] buffer = new byte[8192];
                        for (int length; (length = input.read(buffer)) >= 0;) jar.write(buffer, 0, length);
                        jar.closeEntry();
                    }
                }
            }
        }
        return output.toByteArray();
    }

    public static synchronized Path select(String loader, String minecraft, boolean development) {
        String target = loader + "_" + minecraft;
        String resource = INDEX.getProperty(target + (development ? ".dev" : ".prod"));
        if (resource == null) throw new IllegalStateException("Unsupported eqlib target: " + target);
        try {
            byte[] bytes;
            try (InputStream input = Payloads.class.getResourceAsStream("/" + resource)) {
                if (input == null) throw new IOException("Missing adapter " + resource);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int length; (length = input.read(buffer)) >= 0;) output.write(buffer, 0, length);
                bytes = withCore(output.toByteArray());
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder key = new StringBuilder();
            for (byte value : hash) key.append(String.format(Locale.ROOT, "%02x", value & 255));
            Path folder = Paths.get(".eqlib", key.toString()).toAbsolutePath();
            Files.createDirectories(folder);
            Path jar = folder.resolve(target + ".jar");
            if (!Files.isRegularFile(jar) || !Arrays.equals(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)), hash)) {
                Path temporary = Files.createTempFile(folder, "adapter-", ".tmp");
                try {
                    Files.write(temporary, bytes);
                    try { Files.move(temporary, jar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
            System.out.println("EQLIB_ADAPTER " + target + " " + (development ? "development" : "production"));
            return jar;
        } catch (Exception failure) { throw new IllegalStateException("Cannot prepare eqlib adapter " + target, failure); }
    }
}
