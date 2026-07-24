# Current iteration

- [x] 1. Enforce canonical player-facing title case separately from lowercase identifier normalization, migrate persisted names safely, and cover item/tooltips plus generated container titles.
- [x] 2. Diagnose and fix generated entity placement through the general spawner/runtime contract, using Phantom Skiff only as a regression case.
- [x] 3. Add a composable authored equipment-display path for head accessories and other armor that cannot be represented well by vanilla humanoid wrapping, while preserving the existing vanilla 64×32 route and old worlds.
- [x] 4. Update generation prompt/API/reference material so the model chooses the appropriate vanilla-wrap or authored-display route from supplied textual references.
- [x] 5. Run the full test/build suite, install the exact artifact into the LittleChemistry Prism instance, and commit the completed iteration.
