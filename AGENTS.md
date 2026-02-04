# Repository Guidelines

## Project Structure & Module Organization
- `app/` is the main Android application module.
- Source code lives in `app/src/main/java/me/ash/reader/` and is organized by `domain/`, `infrastructure/`, and `ui/`.
- Resources and assets are under `app/src/main/res/`.
- Room schemas are versioned in `app/schemas/` (update when changing DB entities).
- Release signing config expects `signature/keystore.properties` (local, not committed).
- CI workflows live in `.github/workflows/`, and store builds use `fastlane/metadata/`.

## Build, Test, and Development Commands
- `./gradlew assembleGithubRelease` builds the GitHub release APK (CI uses this).
- `./gradlew testGithubReleaseUnitTest` runs unit tests for the `githubRelease` variant (CI uses this).
- `./gradlew clean` deletes build outputs.
- Android Studio “Run” builds and installs a debug variant for local dev.

## Coding Style & Naming Conventions
- Kotlin/Compose codebase. Follow standard Kotlin style and Android Studio formatting.
- Indentation: 4 spaces, no tabs.
- Naming: `PascalCase` for classes, `camelCase` for functions/variables, `UPPER_SNAKE_CASE` for constants.
- Keep files in their feature area (e.g., UI screens in `ui/page/`, data in `domain/` or `infrastructure/`).

## Testing Guidelines
- Unit tests (when added) should go in `app/src/test/java/` and be named `*Test.kt`.
- Instrumented tests go in `app/src/androidTest/java/` and are run via the Android test runner.
- CI currently runs `testGithubReleaseUnitTest`; keep tests fast and deterministic.

## Commit & Pull Request Guidelines
- Recent history mixes Conventional Commits (`feat:`, `fix:`, `chore:`, `build:`) and short localized summaries.
- Prefer Conventional Commits where possible, e.g., `feat(ui): add scroll indicator`.
- PRs should include a clear summary, link related issues, and add screenshots or recordings for UI changes.
- If you modify database entities, include the updated Room schema files in `app/schemas/`.

## Security & Configuration Tips
- Do not commit signing keys or local SDK paths; keep them in `signature/` and `local.properties`.
