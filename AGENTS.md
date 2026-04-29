# AGENTS.md — Codex Context

This repository uses KMP + Decompose + Compose + Koin.

- Project context: `.claude/PROJECT-CONTEXT.md`
- Canonical local gate: `./gradlew ciCheck --no-configuration-cache`
- App build: `./gradlew :apps:android-next:assembleDebug --no-configuration-cache`
- Android/app JVM tests: `./gradlew test --no-configuration-cache`
- KMP JVM tests: `./gradlew allTests --no-configuration-cache`
- Static checks: `./gradlew detekt ktlintCheck --no-configuration-cache`

Architecture defaults:
- Domain/data live under `shared/{core,feature}`.
- Android presentation lives under `android/feature/*/presentation`.
- New presentation state holders are Decompose `Component`s, not AndroidX `ViewModel`s, unless an ADR/phase says otherwise.
- Compose screens render state and emit callbacks; do not resolve Koin or call repositories/use cases directly from screens.
- Skills and deep workflows are lazy-loaded from `.claude/skills` and `.claude/commands`; do not preload them unless the task needs them.
