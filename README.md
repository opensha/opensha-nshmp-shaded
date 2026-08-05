# OpenSHA NSHMP-lib Shaded Artifacts

This project builds shaded/ASM-transformed versions of [nshmp-lib](https://code.usgs.gov/ghsc/nshmp/nshmp-lib) (and the smaller `nshmp-gmm` subset) for use in OpenSHA projects. This removes confusion between similarly-named classes between the two projects (many `nshmp-lib` classes were originally copied and modified from OpenSHA).

The shading affects every class under the `gov.usgs.earthquake.nshmp` package:

```text
gov.usgs.earthquake.nshmp
```

is relocated beneath:

```text
org.opensha.nshmp.shaded
```

Each top-level class name is prefixed with `Nshmp`. Nested and anonymous class suffixes are retained under the renamed outer class.

Examples:

```text
gov.usgs.earthquake.nshmp.gmm.Gmm
    -> org.opensha.nshmp.shaded.gmm.NshmpGmm

gov.usgs.earthquake.nshmp.gmm.GmmInput
    -> org.opensha.nshmp.shaded.gmm.NshmpGmmInput

gov.usgs.earthquake.nshmp.gmm.GmmInput$Builder
    -> org.opensha.nshmp.shaded.gmm.NshmpGmmInput$Builder

gov.usgs.earthquake.nshmp.calc.Site
    -> org.opensha.nshmp.shaded.calc.NshmpSite

gov.usgs.earthquake.nshmp.geo.Location
    -> org.opensha.nshmp.shaded.geo.NshmpLocation
```

Classes outside `gov/usgs/earthquake/nshmp/` are copied unchanged. The transformer rewrites bytecode references with ASM remapping rather than raw class-file string replacement.

## Shaded Artifacts

We shade both the full `nshmp-lib` and `nshmp-gmm` artifacts. The shaded artifact naming convention is:

```text
upstream ghsc:nshmp-gmm:<nshmpVersion>
    -> org.opensha:opensha-nshmp-gmm:<nshmpVersion>-opensha.<transformRevision>

upstream ghsc:nshmp-lib:<nshmpVersion>
    -> org.opensha:opensha-nshmp-lib:<nshmpVersion>-opensha.<transformRevision>
```

The current Gradle `maven-publish` block is draft metadata for that future publishing workflow. It is present so the artifact identity is explicit, but publishing is not yet part of ordinary local builds. Those artifacts will be available at:

```text
org.opensha:opensha-nshmp-gmm:<version>
org.opensha:opensha-nshmp-lib:<version>
```

_TODO: The publication block currently attaches the transformed binary jars and transformed source jars only. Repository selection, signing, release credentials and final dependency metadata should be completed as part of the OpenSHA publishing workflow before any real publication. Do not run publish tasks unless you are intentionally testing or completing that publishing workflow._

## Transformer Behavior

The transformer scans the full input jar before writing output, builds a complete source-to-target class map, and fails if two source classes map to the same target name.

For each jar:

- All `gov/usgs/earthquake/nshmp/` class entries are written to their relocated paths.
- Field names and method names are preserved.
- Class attributes, annotations, generic signatures, nests, permitted subclasses, enclosing-method metadata, and inner-class metadata are remapped by ASM.
- Ordinary non-class resources are copied.
- Text resources likely to contain class names, including `META-INF/services`, `.properties`, `.xml`, `.json`, and `.txt`, are rewritten.
- Signed-jar metadata entries ending in `.SF`, `.RSA`, or `.DSA` are removed.
- Output entries are sorted and timestamped deterministically.
- Duplicate output entries fail the build.

`package-info.class` is relocated without an added `Nshmp` class prefix.
`module-info.class` under the NSHMP package is treated as unsupported and fails explicitly.

Source jars are transformed separately with the same naming policy. Java source paths are relocated and top-level `.java` filenames are prefixed with `Nshmp`; package declarations, imports, fully-qualified references, static imports, and same-package NSHMP type references are rewritten to match the shaded bytecode. The transformed source jars are intended for IDE source navigation once these artifacts are published through Maven metadata.

## Versioning

Versions are configured in `gradle.properties`:

```text
nshmpVersion = 1.8.4
transformRevision = 1
```

The transformed artifact version is:

```text
<nshmpVersion>-opensha.<transformRevision>
```

For example:

```text
1.8.4-opensha.1
```

Increment `transformRevision` when the transformation process, metadata, or packaging changes without changing the upstream `nshmp-lib` version.

## Building Jars

Build both transformed jars:

```bash
./gradlew transformNshmpGmmJar transformNshmpLibJar
```

This writes:

```text
build/transformed/opensha-nshmp-gmm-1.8.4-opensha.1.jar
build/transformed/opensha-nshmp-lib-1.8.4-opensha.1.jar
```

These tasks resolve the upstream `nshmp-lib` artifacts and transform only the selected input jars. Upstream dependencies are not embedded into the transformed jars.

Build transformed source jars:

```bash
./gradlew transformNshmpGmmSourcesJar transformNshmpLibSourcesJar
```

This writes:

```text
build/transformed/opensha-nshmp-gmm-1.8.4-opensha.1-sources.jar
build/transformed/opensha-nshmp-lib-1.8.4-opensha.1-sources.jar
```

## Local Maven Testing

Publish the transformed binary and source jars to a local file-based Maven repository:

```bash
./gradlew publishToLocalTestMaven
```

This writes Maven metadata and artifacts under:

```text
build/local-maven/
```

Sibling OpenSHA checkouts can then resolve the shaded artifacts by Maven coordinates from this local repository:

```text
org.opensha:opensha-nshmp-gmm:1.8.4-opensha.1
org.opensha:opensha-nshmp-lib:1.8.4-opensha.1
```

This is the preferred local test workflow when you want Gradle and IDE tooling to discover the transformed `-sources.jar` files before the artifacts are published to a remote Maven repository.

## Validation

Run the standard verification suite:

```bash
./gradlew check
```

This runs:

- `test`: unit tests for representative mappings, jar structure, resource rewriting, signature metadata removal, and class loading.
- `validateGmmLibConsistency`: verifies that transformed classes common to the GMM-only and full-library jars are byte-for-byte identical.
- `validateGmmEquivalence`: compares representative original and transformed GMM calculations for identical branch weights, means, and sigmas.

The validation tasks build and inspect jars under `build/transformed`. They do not copy jars into any consumer project.

## Optional Sibling-Repo Copy

For development workflows where this repository is checked out next to OpenSHA repositories:

```text
../opensha/
../opensha-dev/
```

the copy task can place transformed binary jars into each repository's `lib/` directory:

```bash
./gradlew copyShadedJarsForLocalTesting
```

This writes:

```text
../opensha/lib/opensha-nshmp-gmm-1.8.4-opensha.1.jar
../opensha-dev/lib/opensha-nshmp-lib-1.8.4-opensha.1.jar
```

This task is only a convenience for flat-file binary consumer testing. It relies on the sibling directories existing and is not required to build, validate, or publish the transformed artifacts. Flat-file jar dependencies generally do not give Gradle enough metadata to automatically associate source jars; use the local Maven testing workflow when source attachment matters.
