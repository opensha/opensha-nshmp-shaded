package org.opensha.nshmp.shading;

import java.util.Map;

import org.objectweb.asm.commons.Remapper;

final class NshmpRemapper extends Remapper {

    private final Map<String, String> classMap;

    NshmpRemapper(Map<String, String> classMap) {
        this.classMap = Map.copyOf(classMap);
    }

    @Override
    public String map(String internalName) {
        String mapped = classMap.get(internalName);
        if (mapped != null) {
            return mapped;
        }
        if (NshmpNameMapper.isNshmpClass(internalName)) {
            return NshmpNameMapper.mapInternalName(internalName);
        }
        return internalName;
    }
}
