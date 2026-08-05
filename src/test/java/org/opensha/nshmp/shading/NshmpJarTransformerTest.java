package org.opensha.nshmp.shading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class NshmpJarTransformerTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void transformsJarStructureAndResources() throws IOException {
        var input = tempDir.resolve("input.jar");
        var output = tempDir.resolve("output.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(input))) {
            writeClass(out, "gov/usgs/earthquake/nshmp/gmm/Gmm");
            writeClass(out, "gov/usgs/earthquake/nshmp/gmm/GmmInput$Builder");
            writeClass(out, "com/example/Unrelated");
            writeEntry(out, "META-INF/TEST.SF", "invalid");
            writeEntry(out, "META-INF/services/example.Service", "gov.usgs.earthquake.nshmp.gmm.Gmm\n");
        }

        NshmpJarTransformer.transform(input, output);

        Set<String> entries = new HashSet<>();
        try (JarFile jar = new JarFile(output.toFile())) {
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement().getName());
            }
            String services = new String(jar.getInputStream(jar.getEntry("META-INF/services/example.Service")).readAllBytes());
            assertTrue(services.contains("org.opensha.nshmp.shaded.gmm.NshmpGmm"));
        }

        assertFalse(entries.contains("gov/usgs/earthquake/nshmp/gmm/Gmm.class"));
        assertTrue(entries.contains("org/opensha/nshmp/shaded/gmm/NshmpGmm.class"));
        assertTrue(entries.contains("org/opensha/nshmp/shaded/gmm/NshmpGmmInput$Builder.class"));
        assertTrue(entries.contains("com/example/Unrelated.class"));
        assertFalse(entries.contains("META-INF/TEST.SF"));
    }

    private static void writeClass(JarOutputStream out, String internalName) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitEnd();
        writeEntry(out, internalName + ".class", writer.toByteArray());
    }

    private static void writeEntry(JarOutputStream out, String name, String text) throws IOException {
        writeEntry(out, name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeEntry(JarOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
