# Roadmap

## Stage 1 — Android shell and PGN review
- [x] Kotlin / Compose project scaffold
- [x] Compact panel and post-game review screens
- [x] Import a PGN file using Android's document picker
- [x] Parse headers, SAN move tokens, comments, and simple variations
- [x] Step through moves and jump to a move from the move list
- [ ] Validate SAN against legal chess positions and render the board

## Stage 2 — Position and engine analysis
- [ ] Add a chess rules library to replay SAN into real board positions
- [ ] Bundle a properly licensed Stockfish build for supported Android ABIs
- [ ] UCI process wrapper and analysis queue
- [ ] Classify inaccuracies, mistakes, blunders, and missed captures
- [ ] Explain best alternatives in plain language

## Stage 3 — Floating panel
- [ ] Android overlay service and drag/minimize behavior
- [ ] Show clocks, move number, and notes
- [ ] No engine recommendations during rated live play

## Stage 4 — Polish
- [ ] Tests, accessibility, localization, and release build
