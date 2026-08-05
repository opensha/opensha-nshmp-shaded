package org.opensha.nshmp.shading;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

public final class NshmpJarTransformer {

    private static final Instant FIXED_TIME = Instant.parse("2000-01-01T00:00:00Z");

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: NshmpJarTransformer <input.jar> <output.jar>");
        }
        transform(Path.of(args[0]), Path.of(args[1]));
    }

    public static void transform(Path inputJar, Path outputJar) throws IOException {
        List<InputEntry> entries = readEntries(inputJar);
        Map<String, String> classMap = buildClassMap(entries);
        NshmpRemapper remapper = new NshmpRemapper(classMap);

        Map<String, byte[]> outputEntries = new TreeMap<>();
        for (InputEntry entry : entries) {
            String outputName = mapEntryName(entry.name(), classMap);
            if (outputName == null) {
                continue;
            }
            byte[] outputBytes = entry.name().endsWith(".class")
                    ? transformClass(entry.bytes(), remapper)
                    : transformResource(entry.name(), entry.bytes(), classMap);
            byte[] previous = outputEntries.putIfAbsent(outputName, outputBytes);
            if (previous != null) {
                throw new IllegalStateException("Duplicate transformed jar entry: " + outputName);
            }
        }

        Files.createDirectories(outputJar.toAbsolutePath().getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outputJar))) {
            for (Map.Entry<String, byte[]> entry : outputEntries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(FIXED_TIME.toEpochMilli());
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    static Map<String, String> buildClassMap(List<InputEntry> entries) {
        Map<String, String> classMap = new LinkedHashMap<>();
        Map<String, String> reverseMap = new HashMap<>();
        entries.stream()
                .map(InputEntry::name)
                .filter(name -> name.endsWith(".class"))
                .sorted()
                .forEach(name -> {
                    String internalName = name.substring(0, name.length() - ".class".length());
                    if (!NshmpNameMapper.isNshmpClass(internalName)) {
                        return;
                    }
                    String mapped = NshmpNameMapper.mapInternalName(internalName);
                    String previous = reverseMap.putIfAbsent(mapped, internalName);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Class mapping collision: " + previous + " and " + internalName + " both map to " + mapped);
                    }
                    classMap.put(internalName, mapped);
                });
        return classMap;
    }

    private static List<InputEntry> readEntries(Path inputJar) throws IOException {
        List<InputEntry> entries = new ArrayList<>();
        try (JarInputStream in = new JarInputStream(Files.newInputStream(inputJar))) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries.add(new InputEntry(entry.getName(), readAll(in)));
            }
        }
        entries.sort(Comparator.comparing(InputEntry::name));
        return entries;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private static String mapEntryName(String entryName, Map<String, String> classMap) {
        if (isSignatureMetadata(entryName)) {
            return null;
        }
        if (!entryName.endsWith(".class")) {
            return entryName;
        }
        String internalName = entryName.substring(0, entryName.length() - ".class".length());
        String mapped = classMap.get(internalName);
        return (mapped == null ? internalName : mapped) + ".class";
    }

    private static boolean isSignatureMetadata(String entryName) {
        if (!entryName.startsWith("META-INF/")) {
            return false;
        }
        return entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".DSA");
    }

    private static byte[] transformClass(byte[] bytes, NshmpRemapper remapper) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassRemapper(writer, remapper);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] transformResource(String entryName, byte[] bytes, Map<String, String> classMap) {
        if (!isClassNameResource(entryName)) {
            return bytes;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : classMap.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
            text = text.replace(
                    NshmpNameMapper.internalToClassName(entry.getKey()),
                    NshmpNameMapper.internalToClassName(entry.getValue()));
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isClassNameResource(String entryName) {
        return entryName.startsWith("META-INF/services/")
                || entryName.endsWith(".properties")
                || entryName.endsWith(".xml")
                || entryName.endsWith(".json")
                || entryName.endsWith(".txt");
    }

    record InputEntry(String name, byte[] bytes) {}
}
