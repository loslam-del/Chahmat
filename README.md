# Chahmat — Chess Coach

Android chess companion with a compact floating match panel and post-game review.

## Current MVP
- Kotlin + Jetpack Compose Android app
- Import a PGN file from the device
- Read player/event headers and SAN move notation
- Step through moves with Previous / Next and jump from the move list
- Compact panel concept for clocks and notes

## Planned
- Replay legal positions on a chessboard
- Stockfish post-game analysis: missed captures, mistakes, and stronger alternatives
- Android floating overlay with drag/minimize behavior

## Fair play
Engine recommendations are intended for post-game analysis, not use during rated live games.

## Build
Open this repository in Android Studio and sync the Gradle project. The app currently targets Android 8.0+ (API 26) and compile SDK 35.

See docs/ROADMAP.md for implementation stages.
