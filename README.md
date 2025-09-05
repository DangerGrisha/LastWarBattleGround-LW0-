# LastWar — Character-based FFA Platform

This project is a platform for a PvP “free-for-all” (FFA) mode where each player selects a character/class and receives a unique kit of items and abilities. The goal is to dominate the arena by leveraging the strengths of your chosen character. The project targets Java 17 and uses Gradle for builds.

## What the platform does
- Lets a player pick a character (class) and automatically grants the corresponding kit.
- Prevents duplicate kit distribution within a single session.
- Provides a basic starter pack for comfortable gameplay (building blocks, food, utilities).
- Supports FFA gameplay: no teams; everyone fights for themselves.
- Safely executes server/player commands from the appropriate sender to avoid crashes.
- Produces logs to help debug character selection and item distribution.

## Requirements
- Java 17 (JDK 17)
- Gradle Wrapper (included in the project)
- A compatible server/runtime environment for the resulting artifact (if applicable)

Check versions:
- `java -version` → should be 17.x
- `./gradlew -v`

## Build
- Linux/macOS: `./gradlew clean build`
- Windows: `gradlew clean build`

Artifacts: `build/libs/*.jar`

## Install and run (generic flow)
1. Build the project (see “Build” above).
2. Place the resulting JAR into the target environment (for example, a server’s plugins directory, if applicable).
3. Restart or start the environment.
4. Join the game and select a character — the kit should be enough to start in FFA.

## For developers
- Import the project into IntelliJ IDEA as a Gradle project.
- Useful tasks:
  - `./gradlew build` — build
  - `./gradlew test` — run tests (if present)
  - `./gradlew clean` — clean build artifacts

## Gameplay in a nutshell
- Choose a character → receive the kit → fight on the arena (FFA).
- Kits and balance can be extended and tuned as the project evolves.

## Add this README to Git and push
If you just created/updated this README locally:
