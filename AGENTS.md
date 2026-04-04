# Micronaut Object Storage Agent Notes

- Default branch: `3.0.x`. There are no additional future maintenance branches in this checkout right now.
- For routine or audit work, fetch `origin` and compare against `origin/3.0.x` before logging doc or workflow regressions; isolated worktrees can start from a stale local base.
- Use `./gradlew` from the repository root. Prefer targeted module tasks while iterating and finish with repo-level verification such as `./gradlew check`.
- The active Java toolchain is declared in [`.sdkmanrc`](.sdkmanrc). If SDKMAN is available, use `sdk env install` and `sdk env` before running Gradle.
- Some tests require Docker because the repository uses Testcontainers-backed provider and emulator specs.
- Docs live under `src/main/docs/guide`. Build them with `./gradlew publishGuide` or `./gradlew docs`.
- If a workflow file says it is synced from `micronaut-project-template`, make the durable fix upstream and only patch this repository directly when the branch needs an immediate backport.
- Keep GitHub-facing commits and PR descriptions repo-focused. Do not mention internal automation systems, internal issue ids, or agent identities.
