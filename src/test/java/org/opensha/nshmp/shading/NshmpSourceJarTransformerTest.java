package org.opensha.nshmp.shading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NshmpSourceJarTransformerTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void transformsSourceJarStructureAndContents() throws IOException {
        var input = tempDir.resolve("input-sources.jar");
        var output = tempDir.resolve("output-sources.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(input))) {
            writeEntry(out, "gov/usgs/earthquake/nshmp/gmm/Gmm.java", """
                    package gov.usgs.earthquake.nshmp.gmm;

                    import gov.usgs.earthquake.nshmp.calc.Site;

                    public enum Gmm {
                      TEST;
                      Site site;
                    }
                    """);
            writeEntry(out, "gov/usgs/earthquake/nshmp/gmm/GmmInput.java", """
                    package gov.usgs.earthquake.nshmp.gmm;

                    import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
                    import gov.usgs.earthquake.nshmp.model.Rupture;

                    public class GmmInput {
                      enum Field { MW }
                      Rupture rupture;
                    }
                    """);
            writeEntry(out, "gov/usgs/earthquake/nshmp/calc/Site.java", """
                    package gov.usgs.earthquake.nshmp.calc;

                    public class Site {}
                    """);
            writeEntry(out, "gov/usgs/earthquake/nshmp/model/Rupture.java", """
                    package gov.usgs.earthquake.nshmp.model;

                    public class Rupture {}
                    """);
            writeEntry(out, "META-INF/TEST.RSA", "invalid");
        }

        NshmpSourceJarTransformer.transform(input, output);

        Set<String> entries = new HashSet<>();
        try (JarFile jar = new JarFile(output.toFile())) {
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement().getName());
            }
            String gmmInput = new String(jar.getInputStream(
                    jar.getEntry("org/opensha/nshmp/shaded/gmm/NshmpGmmInput.java")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(gmmInput.contains("package org.opensha.nshmp.shaded.gmm;"));
            assertTrue(gmmInput.contains("import static org.opensha.nshmp.shaded.gmm.NshmpGmmInput.Field.MW;"));
            assertTrue(gmmInput.contains("import org.opensha.nshmp.shaded.model.NshmpRupture;"));
            assertTrue(gmmInput.contains("public class NshmpGmmInput"));
            assertTrue(gmmInput.contains("NshmpRupture rupture;"));
        }

        assertFalse(entries.contains("gov/usgs/earthquake/nshmp/gmm/Gmm.java"));
        assertTrue(entries.contains("org/opensha/nshmp/shaded/gmm/NshmpGmm.java"));
        assertTrue(entries.contains("org/opensha/nshmp/shaded/gmm/NshmpGmmInput.java"));
        assertFalse(entries.contains("META-INF/TEST.RSA"));
    }

    private static void writeEntry(JarOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
