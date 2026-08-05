package org.opensha.nshmp.shading;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarFile;

public final class ShadedJarConsistencyCheck {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ShadedJarConsistencyCheck <gmm.jar> <lib.jar>");
        }
        Map<String, byte[]> gmm = readClasses(Path.of(args[0]));
        Map<String, byte[]> lib = readClasses(Path.of(args[1]));
        TreeSet<String> common = new TreeSet<>(gmm.keySet());
        common.retainAll(lib.keySet());
        for (String name : common) {
            if (!MessageDigest.isEqual(sha256(gmm.get(name)), sha256(lib.get(name)))) {
                throw new IllegalStateException("Common transformed class differs between artifacts: " + name);
            }
        }
        if (common.isEmpty()) {
            throw new IllegalStateException("No common transformed classes found");
        }
    }

    private static Map<String, byte[]> readClasses(Path jar) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory()
                        && entry.getName().endsWith(".class")
                        && entry.getName().startsWith(NshmpNameMapper.TARGET_PREFIX)) {
                    classes.put(entry.getName(), jarFile.getInputStream(entry).readAllBytes());
                }
            }
        }
        return classes;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
