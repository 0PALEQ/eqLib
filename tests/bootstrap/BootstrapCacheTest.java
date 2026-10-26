import com.cookiecraftmods.eqlib.bootstrap.Payloads;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.JarFile;

/** Exercise the distribution on Java 8 without initializing any Minecraft or loader class. */
public final class BootstrapCacheTest {
    public static void main(String[] args) throws Exception {
        Properties index = new Properties();
        try (InputStream stream = Payloads.class.getResourceAsStream("/META-INF/eqlib/adapters.properties")) {
            index.load(stream);
        }
        int checked = 0;
        Path last = null;
        for (String key : index.stringPropertyNames()) {
            int split = key.indexOf('_');
            String loader = key.substring(0, split);
            String game = key.substring(split + 1, key.lastIndexOf('.'));
            Path selected = Payloads.select(loader, game, key.endsWith(".dev"));
            try (JarFile jar = new JarFile(selected.toFile())) {
                if (jar.getJarEntry("eqlib.mixins.json") == null
                        || jar.getJarEntry("com/cookiecraftmods/eqlib/core/clock/ClockTime.class") == null) {
                    throw new AssertionError("Missing adapter or merged core: " + key);
                }
            }
            try (URLClassLoader isolated = new URLClassLoader(new URL[] { selected.toUri().toURL() }, null)) {
                Class<?> clock = isolated.loadClass("com.cookiecraftmods.eqlib.core.clock.ClockTime");
                float angle = (Float) clock.getMethod("hourAngle", long.class).invoke(null, 6250L);
                if (Math.abs(angle - 7.5f) > 0.001f) throw new AssertionError("Core linkage: " + key);
            }
            checked++;
            last = selected;
        }
        byte[] original = Files.readAllBytes(last);
        Files.write(last, new byte[] {1, 2, 3});
        // The cache pathname records the target, irrespective of which mode was visited last.
        String target = last.getFileName().toString().replace(".jar", "");
        int split = target.indexOf('_');
        boolean repaired = false;
        for (boolean development : new boolean[] { false, true }) {
            Path restored = Payloads.select(target.substring(0, split), target.substring(split + 1), development);
            if (restored.equals(last) && Arrays.equals(original, Files.readAllBytes(restored))) repaired = true;
        }
        if (!repaired) throw new AssertionError("Corrupt adapter cache was not repaired");
        try {
            Payloads.select("fabric", "unsupported", false);
            throw new AssertionError("Unsupported game accepted");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("Unsupported eqlib target")) throw expected;
        }
        System.out.println("BOOTSTRAP_PASSED adapters=" + checked + " core-linkage=java8 cache-repair=checked unsupported=checked");
    }
}
