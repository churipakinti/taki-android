# Compose screenshot goldens

Golden PNGs for the Roborazzi screenshot tests (`org.moire.ultrasonic.ui.**`), captured on
the JVM through Robolectric — no emulator, no device, no network.

## Running

```bash
# Verify the current UI against the committed goldens (the default; this is what CI runs).
./gradlew :ultrasonic:testDebugUnitTest

# Re-generate the goldens on purpose, e.g. after an intended visual change to a primitive
# or a design-token update. Review the diff before committing the new PNGs.
./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
```

`ultrasonic/build.gradle` forwards `-Proborazzi.test.record` / `-Proborazzi.test.verify` /
`-Proborazzi.test.compare` to the test JVM and defaults to **verify** when neither record nor
verify is passed.

## Rules

- A golden only changes through an intentional `-Proborazzi.test.record=true` run whose diff
  a human has looked at. An unexplained golden change in a review is a red flag.
- Tests must stay deterministic: fixed/fake inputs only, never a real `ImageLoader`, network,
  server, or playback state. `TakiArtwork` is captured with `model = null` (placeholder).
- Screens (issue #10) add their own cases at the QA-matrix cells from
  `docs/design/TAKI_DESIGN_SYSTEM_V2.md` section 20 (360x800 and 412x915, font scale 1.0 and
  1.30, long text, missing artwork, loading, empty).
