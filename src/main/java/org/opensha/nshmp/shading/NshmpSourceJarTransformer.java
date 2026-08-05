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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NshmpSourceJarTransformer {

    private static final Instant FIXED_TIME = Instant.parse("2000-01-01T00:00:00Z");
    private static final Pattern NSHMP_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?(gov\\.usgs\\.earthquake\\.nshmp(?:\\.[A-Za-z_$][\\w$]*)+)",
            Pattern.MULTILINE);
    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)\\b");

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: NshmpSourceJarTransformer <input-sources.jar> <output-sources.jar>");
        }
        transform(Path.of(args[0]), Path.of(args[1]));
    }

    public static void transform(Path inputJar, Path outputJar) throws IOException {
        List<InputEntry> entries = readEntries(inputJar);
        Map<String, String> sourceMap = buildSourceMap(entries);

        Map<String, byte[]> outputEntries = new TreeMap<>();
        for (InputEntry entry : entries) {
            String outputName = mapEntryName(entry.name(), sourceMap);
            if (outputName == null) {
                continue;
            }
            byte[] outputBytes = entry.name().endsWith(".java")
                    ? transformSource(entry.name(), entry.bytes(), sourceMap)
                    : transformResource(entry.name(), entry.bytes(), sourceMap);
            byte[] previous = outputEntries.putIfAbsent(outputName, outputBytes);
            if (previous != null) {
                throw new IllegalStateException("Duplicate transformed source jar entry: " + outputName);
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

    static Map<String, String> buildSourceMap(List<InputEntry> entries) {
        Map<String, String> sourceMap = new LinkedHashMap<>();
        Map<String, String> reverseMap = new HashMap<>();
        entries.stream()
                .map(InputEntry::name)
                .filter(name -> name.endsWith(".java"))
                .sorted()
                .forEach(name -> {
                    String internalName = name.substring(0, name.length() - ".java".length());
                    if (!NshmpNameMapper.isNshmpClass(internalName)) {
                        return;
                    }
                    String mapped = NshmpNameMapper.mapInternalName(internalName);
                    String previous = reverseMap.putIfAbsent(mapped, internalName);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Source mapping collision: " + previous + " and " + internalName + " both map to " + mapped);
                    }
                    sourceMap.put(internalName, mapped);
                });
        return sourceMap;
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

    private static String mapEntryName(String entryName, Map<String, String> sourceMap) {
        if (isSignatureMetadata(entryName)) {
            return null;
        }
        if (!entryName.endsWith(".java")) {
            return entryName;
        }
        String internalName = entryName.substring(0, entryName.length() - ".java".length());
        String mapped = sourceMap.get(internalName);
        return (mapped == null ? internalName : mapped) + ".java";
    }

    private static boolean isSignatureMetadata(String entryName) {
        if (!entryName.startsWith("META-INF/")) {
            return false;
        }
        return entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".DSA");
    }

    private static byte[] transformSource(String entryName, byte[] bytes, Map<String, String> sourceMap) {
        String text = new String(bytes, StandardCharsets.UTF_8);

        for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
            text = text.replace(
                    NshmpNameMapper.internalToClassName(entry.getKey()),
                    NshmpNameMapper.internalToClassName(entry.getValue()));
        }
        text = text.replace("gov.usgs.earthquake.nshmp", "org.opensha.nshmp.shaded");

        for (Map.Entry<String, String> replacement : simpleNameReplacements(entryName, bytes, sourceMap).entrySet()) {
            text = text.replaceAll("(?<!\\.)\\b" + Pattern.quote(replacement.getKey()) + "\\b", replacement.getValue());
        }

        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> simpleNameReplacements(String entryName, byte[] bytes, Map<String, String> sourceMap) {
        Map<String, String> replacements = new TreeMap<>(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
        String internalName = entryName.endsWith(".java")
                ? entryName.substring(0, entryName.length() - ".java".length())
                : "";
        String packagePath = internalName.contains("/")
                ? internalName.substring(0, internalName.lastIndexOf('/'))
                : "";

        for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
            String sourcePackage = entry.getKey().contains("/")
                    ? entry.getKey().substring(0, entry.getKey().lastIndexOf('/'))
                    : "";
            if (sourcePackage.equals(packagePath)) {
                replacements.put(simpleName(entry.getKey()), simpleName(entry.getValue()));
            }
        }

        String originalText = new String(bytes, StandardCharsets.UTF_8);
        declaredNestedTypeNames(originalText).forEach(replacements::remove);
        Matcher matcher = NSHMP_IMPORT.matcher(originalText);
        while (matcher.find()) {
            String candidate = NshmpNameMapper.classNameToInternal(matcher.group(1));
            while (candidate.startsWith(NshmpNameMapper.SOURCE_PREFIX) && candidate.contains("/")) {
                String mapped = sourceMap.get(candidate);
                if (mapped != null) {
                    replacements.put(simpleName(candidate), simpleName(mapped));
                    break;
                }
                candidate = candidate.substring(0, candidate.lastIndexOf('/'));
            }
        }

        return replacements;
    }

    private static List<String> declaredNestedTypeNames(String text) {
        List<String> nestedNames = new ArrayList<>();
        int depth = 0;
        for (String line : text.split("\\R")) {
            Matcher matcher = TYPE_DECLARATION.matcher(line);
            while (matcher.find()) {
                if (depth > 0) {
                    nestedNames.add(matcher.group(1));
                }
            }
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}' && depth > 0) {
                    depth--;
                }
            }
        }
        return nestedNames;
    }

    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    private static byte[] transformResource(String entryName, byte[] bytes, Map<String, String> sourceMap) {
        if (!isClassNameResource(entryName)) {
            return bytes;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
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
