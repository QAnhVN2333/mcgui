# MC GUI agent instructions

## Project shape

- This is a Java 21 Gradle project that builds the `MCGUI` Spigot/Paper/Folia plugin.
- The main plugin code is under `src/main/java/ai/resourcepack/ui`; tests are under
  `src/test/java`, and Paper-dependent tests are under `src/paperTest/java`.
- `src/main/java/ai/resourcepack/ui/html` is the HTML/CSS renderer. It is deliberately
  independent of Bukkit and must remain usable off the server thread.
- `src/main/java/ai/resourcepack/ui/framework` is the framework/API used to draw
  pictures on walls and in fullscreen views. `examples/arcade` and `examples/atlas`
  are separate Gradle builds that compile against this checkout.
- Pages and other shipped assets live in `src/main/resources`; sample pages are in
  `examples/samples` and plugin examples in `examples/vanilla`.

## Implementation rules

- Read `NOTES.md` and the nearest existing tests before changing renderer, layout,
  painting, motion, caching, threading, packet, or resource-pack code.
- Preserve the renderer's separation from Bukkit. Do not introduce server APIs into
  `html`, `html/css`, `html/layout`, `html/paint`, `html/style`, `html/text`, or
  `html/res`.
- Rendering and page loading are asynchronous. Do not block the server thread on
  rendering or page-loading futures; use the existing render queues and
  `serverThread()` handoff patterns.
- Keep package-private collaboration in the top-level
  `ai.resourcepack.ui` package unless making an API change is intentional. Avoid
  splitting classes into subpackages merely for organization.
- Preserve premultiplied-image, immutable-style, document-copy, render-budget,
  layout-memoization, and resource-pool invariants described in `NOTES.md`.
- Compile against Spigot API and use only APIs available to all supported servers;
  do not add Paper-only calls to main plugin code.
- Reuse existing helpers, naming, event, chat, configuration, and resource-pack
  patterns. Do not silently swallow errors or add success-shaped fallbacks.
- Treat intentional visual, performance, protocol, or compatibility changes as
  observable behavior: update focused tests and the relevant documentation or
  regression baseline process.

## Tests and verification

- Use the Gradle wrapper with the repository's shell form: `sh gradlew ...`.
- For normal Java changes, run `sh gradlew test` or `sh gradlew build`; CI's primary
  check is `sh gradlew build`.
- After renderer or shipped-page changes, run
  `sh gradlew renderSamples` and inspect the generated images in `build/samples/`.
  Use `-Pdir=src/main/resources/pages` for shipped pages and `-Ponly=<name>` to
  narrow the render.
- Build both example plugins when framework/API changes:
  `sh gradlew -p examples/arcade build` and
  `sh gradlew -p examples/atlas build`.
- Paper tests require a prepared server directory and Java 25:
  `sh gradlew paperTest -PpaperServer=<path>`.
- Use the focused test or Gradle task first, then escalate to `build` and the
  applicable regression/performance checks. Do not change performance thresholds
  or render baselines to hide an unintended regression.
- For deliberate page-image changes, follow `CONTRIBUTING.md` and use the
  `render-change` review process; for deliberate performance cost, explain and
  update the specific gate in the same change.

## Contribution workflow

- Branch from `dev` and target pull requests at `dev`; `main` only accepts the
  promotion from this repository's `dev`.
- Use Conventional Commit titles and commit messages, such as
  `fix(render): preserve rounded clipping`.
- Keep changes surgical, add or update focused tests, and update `README.md`,
  `NOTES.md`, or `CONTRIBUTING.md` when the corresponding user behavior,
  architecture, or workflow changes.
- Never expose secrets or report vulnerabilities publicly; follow the security
  contact and disclosure guidance in `CONTRIBUTING.md`.
