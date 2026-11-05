# Roadmap

## Stage 1 — Android shell and PGN review
- [x] Kotlin / Compose Android project
- [x] Import PGN from Android's document picker
- [x] Parse game headers, SAN moves, comments, and simple variations
- [x] Replay the game and validate each SAN move against legal chess positions
- [x] Render the board and navigate through positions
- [x] Reject invalid moves with move-number context

## Stage 2 — Post-game engine analysis
- [x] Bundle Stockfish Lite for Android
- [x] Show the top three engine continuations for the selected position
- [x] Evaluate positions throughout the imported game
- [x] Estimate move-quality loss and show stronger alternatives
- [x] Flag likely missed captures when the engine's best alternative is a capture
- [ ] Add deeper analysis settings and opening-book labels
- [ ] Add richer explanation text for tactics and threats

## Stage 3 — Floating panel
- [ ] Implement Android overlay service and drag/minimize behavior
- [ ] Show clocks, move number, and user notes
- [ ] Keep engine recommendations disabled during rated live play

## Stage 4 — Polish
- [x] Add CI workflow for debug APK builds
- [x] Add replay tests and sample PGN
- [ ] Verify builds on physical Android devices
- [ ] Accessibility, localization, and release packaging
