# Taki documentation

The repository [README](../README.md) is the primary introduction to Taki. This index covers current project, product, and developer documentation.

## Project and developer documentation

- [Product direction](PRODUCT.md)
- [Default behavior](DEFAULT_BEHAVIOR.md)
- [Contributing](CONTRIBUTING.md)
- [Release signing](RELEASE_SIGNING.md)

## Technical references

- [Visual and language guide](technical/TAKI_GUIA_VISUAL_Y_LENGUAJE.md)
- [Compose migration plan](technical/TAKI_COMPOSE_MIGRATION_PLAN.md)
- [ReplayGain](technical/replayGain.md)

Version history lives in [`CHANGELOG.md`](../CHANGELOG.md) and in
[GitHub Releases](https://github.com/churipakinti/taki-android/releases), not
in this directory. Completed audits, one-off implementation plans, and research
are not kept here; Git history is the archive for that material.

Long-lived architecture and design records are the deliberate exception: documents
that define how a subsystem is built and are meant to be referenced for the life of
that subsystem (for example the [visual and language guide](technical/TAKI_GUIA_VISUAL_Y_LENGUAJE.md),
the [design system](design/TAKI_DESIGN_SYSTEM_V2.md), and the
[Compose migration plan](technical/TAKI_COMPOSE_MIGRATION_PLAN.md)) live here as
architecture-of-record and are updated in place, not retired once an issue closes.
