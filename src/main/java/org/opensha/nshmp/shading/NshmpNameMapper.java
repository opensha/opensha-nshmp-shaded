package org.opensha.nshmp.shading;

import java.util.Objects;

final class NshmpNameMapper {

    static final String SOURCE_PREFIX = "gov/usgs/earthquake/nshmp/";
    static final String TARGET_PREFIX = "org/opensha/nshmp/shaded/";
    static final String TOP_LEVEL_PREFIX = "Nshmp";

    private NshmpNameMapper() {}

    static boolean isNshmpClass(String internalName) {
        return internalName != null && internalName.startsWith(SOURCE_PREFIX);
    }

    static String mapInternalName(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        if (!isNshmpClass(internalName)) {
            return internalName;
        }
        String relative = internalName.substring(SOURCE_PREFIX.length());
        int slash = relative.lastIndexOf('/');
        String packagePath = slash >= 0 ? relative.substring(0, slash + 1) : "";
        String simpleName = slash >= 0 ? relative.substring(slash + 1) : relative;

        if (simpleName.equals("package-info")) {
            return TARGET_PREFIX + packagePath + simpleName;
        }
        if (simpleName.equals("module-info")) {
            throw new IllegalArgumentException("module-info.class is not supported for NSHMP relocation");
        }

        int nestedAt = simpleName.indexOf('$');
        String outer = nestedAt >= 0 ? simpleName.substring(0, nestedAt) : simpleName;
        String nestedSuffix = nestedAt >= 0 ? simpleName.substring(nestedAt) : "";
        return TARGET_PREFIX + packagePath + TOP_LEVEL_PREFIX + outer + nestedSuffix;
    }

    static String internalToClassName(String internalName) {
        return internalName.replace('/', '.');
    }

    static String classNameToInternal(String className) {
        return className.replace('.', '/');
    }
}
