# Chahmat — Chess Coach

Android chess companion focused on post-game learning.

## Implemented in the current MVP
- Import a PGN file from Android's document picker
- Replay every move on a chessboard
- Validate SAN moves against legal chess positions; report the move number if a PGN contains an illegal move
- Ask Stockfish for the three strongest continuations in the selected position
- Review a whole game: compare engine evaluations around each played move, suggest a stronger alternative, and flag likely missed captures
- Compact match-panel concept (the actual always-on-top Android service is still planned)

## How to try it
1. Open the project in Android Studio and sync Gradle.
2. Run the app on an Android device (Android 8.0 / API 26 or newer).
3. Tap Разбор → Импортировать PGN and select a PGN file.
4. Use Лучшие ходы for the current position or Проверить партию for a move-by-move review.
5. A sample PGN is in sample-games/ruy-lopez.pgn.

## Analysis notes
- Stockfish Lite is bundled with the app; the first analysis can take a moment while the engine initializes.
- Whole-game review currently uses a depth-limited search for each position. Move labels are estimates, not absolute judgments; engine depth, hardware, and position complexity affect them.
- The review is designed for completed games. Engine suggestions are not shown as live advice during rated games.

## Build
- JDK 21
- Gradle 9.6.0
- Android compile SDK 36
- Minimum Android API 26

The GitHub Actions workflow builds a debug APK and uploads it as the chahmat-debug-apk artifact when the build succeeds.

## Third-party components
See THIRD_PARTY_NOTICES.md. Stockfish is GPL-3.0, so this project is distributed under GPL-3.0.
