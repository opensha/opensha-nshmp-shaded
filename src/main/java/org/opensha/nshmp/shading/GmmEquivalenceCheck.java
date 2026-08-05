package org.opensha.nshmp.shading;

import java.lang.reflect.Method;
import java.util.List;

public final class GmmEquivalenceCheck {

    private static final double TOLERANCE = 0.0;

    private static final List<String> GMMS = List.of("ASK_14", "BSSA_14", "CB_14", "CY_14");
    private static final List<String> IMTS = List.of("PGA", "SA0P2", "SA1P0");

    private GmmEquivalenceCheck() {}

    public static void main(String[] args) throws Exception {
        Object originalInput = input("gov.usgs.earthquake.nshmp.gmm.GmmInput");
        Object shadedInput = input("org.opensha.nshmp.shaded.gmm.NshmpGmmInput");

        for (String gmmName : GMMS) {
            for (String imtName : IMTS) {
                Object originalTree = calc(
                        "gov.usgs.earthquake.nshmp.gmm.Gmm",
                        "gov.usgs.earthquake.nshmp.gmm.Imt",
                        gmmName,
                        imtName,
                        originalInput);
                Object shadedTree = calc(
                        "org.opensha.nshmp.shaded.gmm.NshmpGmm",
                        "org.opensha.nshmp.shaded.gmm.NshmpImt",
                        gmmName,
                        imtName,
                        shadedInput);
                compare(gmmName, imtName, originalTree, shadedTree);
            }
        }
    }

    private static Object input(String inputClassName) throws Exception {
        Class<?> inputClass = Class.forName(inputClassName);
        Object builder = inputClass.getMethod("builder").invoke(null);
        builder = invoke(builder, "withDefaults");
        builder = invoke(builder, "mag", 6.5);
        builder = invoke(builder, "distances", 10.0, 12.0, 5.0);
        builder = invoke(builder, "dip", 45.0);
        builder = invoke(builder, "width", 14.0);
        builder = invoke(builder, "zTor", 1.0);
        builder = invoke(builder, "zHyp", 8.0);
        builder = invoke(builder, "rake", 90.0);
        builder = invoke(builder, "vs30", 760.0);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object calc(String gmmClassName, String imtClassName, String gmmName, String imtName, Object input)
            throws Exception {
        Class<? extends Enum> gmmClass = (Class<? extends Enum>) Class.forName(gmmClassName);
        Class<? extends Enum> imtClass = (Class<? extends Enum>) Class.forName(imtClassName);
        Object gmm = Enum.valueOf(gmmClass, gmmName);
        Object imt = Enum.valueOf(imtClass, imtName);
        Object model = gmmClass.getMethod("instance", imtClass).invoke(gmm, imt);
        Method calc = model.getClass().getMethod("calc", input.getClass());
        calc.setAccessible(true);
        return calc.invoke(model, input);
    }

    private static Object invoke(Object target, String method, double value) throws Exception {
        return target.getClass().getMethod(method, double.class).invoke(target, value);
    }

    private static Object invoke(Object target, String method, double rJB, double rRup, double rX) throws Exception {
        return target.getClass().getMethod(method, double.class, double.class, double.class).invoke(target, rJB, rRup, rX);
    }

    private static Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static void compare(String gmmName, String imtName, Object originalTree, Object shadedTree) throws Exception {
        List<?> originalBranches = (List<?>) originalTree;
        List<?> shadedBranches = (List<?>) shadedTree;
        if (originalBranches.size() != shadedBranches.size()) {
            throw new AssertionError(gmmName + " " + imtName + " branch count mismatch");
        }
        for (int i = 0; i < originalBranches.size(); i++) {
            Object originalBranch = originalBranches.get(i);
            Object shadedBranch = shadedBranches.get(i);
            assertEqual(gmmName, imtName, "branch id", branchId(originalBranch), branchId(shadedBranch));
            assertClose(gmmName, imtName, "branch weight", branchWeight(originalBranch), branchWeight(shadedBranch));
            assertClose(gmmName, imtName, "mean", groundMotionValue(originalBranch, "mean"), groundMotionValue(shadedBranch, "mean"));
            assertClose(gmmName, imtName, "sigma", groundMotionValue(originalBranch, "sigma"), groundMotionValue(shadedBranch, "sigma"));
        }
    }

    private static String branchId(Object branch) throws Exception {
        return (String) accessibleMethod(branch, "id").invoke(branch);
    }

    private static double branchWeight(Object branch) throws Exception {
        return (double) accessibleMethod(branch, "weight").invoke(branch);
    }

    private static double groundMotionValue(Object branch, String method) throws Exception {
        Object groundMotion = accessibleMethod(branch, "value").invoke(branch);
        return (double) accessibleMethod(groundMotion, method).invoke(groundMotion);
    }

    private static Method accessibleMethod(Object target, String method) throws Exception {
        Method reflectedMethod = target.getClass().getMethod(method);
        reflectedMethod.setAccessible(true);
        return reflectedMethod;
    }

    private static void assertEqual(String gmmName, String imtName, String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(gmmName + " " + imtName + " " + label + " mismatch: " + expected + " != " + actual);
        }
    }

    private static void assertClose(String gmmName, String imtName, String label, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(gmmName + " " + imtName + " " + label + " mismatch: " + expected + " != " + actual);
        }
    }
}
