# NeoForge natural spawn survey

The probe is a separate development mod. Build it in the NeoForge 1.21.1 platform directory with Java 21.

```powershell
.\gradlew.bat -I ../../scripts/spawn-survey/survey.gradle build spawnSurveyJar
```

From the repository root, start each seed in a new work directory. Supply an installed NeoForge dedicated server with its libraries and accepted EULA, the production mod JAR, and the matching GeckoLib JAR. Each process binds a random localhost port and stops after the last station.

```powershell
python scripts/spawn-survey/run.py --server <installed-server> --java <java-executable> --mob-jar <mod-jar> --gecko-jar <geckolib-jar> --probe-jar platforms/neoforge-1.21.1/build/survey/spawn-survey.jar --seed 38191 --work build/spawn-field/seed-38191 --property diagnose=true
```

Repeat for seeds 70439 and 91873, then aggregate the completed observations. The Python analyzer requires Python 3.11 or later.

```powershell
python scripts/spawn-survey/analyze.py build/spawn-field/seed-38191 build/spawn-field/seed-70439 build/spawn-field/seed-91873 --output build/spawn-field/results
```

Each seed contributes a 16×16 initial population window and three stationary observations for each of 15 habitats. The biome at an entity's position can differ from the station's biome. Each station preloads its 16×16 window. Measurement starts after at least 200 warm-up ticks and a full 7×7 spawning area is available. The default final sample index is 24000 inclusive. Counts of new entities exclude saved entities loaded back into the world. No kill or spawn command clears or fills a mob cap.

- `stations.csv`, `species.csv`, and `population_chunks.csv` contain the sample coordinates, counts for every observed species, and counts by populated chunk. Unoccupied chunks in a window contribute zero.
- `first_seen.csv` records the first successful natural spawn per species at each station. An absent row for a completed station means zero observed.
- `residents.csv` records living mobs in the 7×7 simulation square, across all heights, at the end of each observation. An individual can occur in successive snapshots if the stations overlap.
- `stages.csv` separates weighted group selection from individual position attempts and accepted entities. Individual candidates can outnumber group selections. The placement callback occurs within the enclosing candidate check.
- `capacity.csv` records global cap occupancy at 24000 sampled ticks per station. The eligible chunk-tick exposure is repeated for each pool.
- `controls.csv` covers floor and light tests in the Overworld. A separate run with `--property rulesOnly=true` checks both spawn reasons and the biome entries in all three dimensions. Pass `--config` plus `enabled`, `weight`, `min`, or `max` expected values as test properties to check a custom server config.
- The Nether fortress regression uses seed 91873 with `--property rulesOnly=true --property fortress=true`. It generates the affected chunk, then tests a deferred coat across NBT serialization and the first server tick at a fortress piece.

The analyzer rejects overlapping population windows, duplicate stationary observations, insufficient eligible chunk-ticks and discrepancies between natural spawn events and per-stage spawn counts. Completed stations can be inspected while other stations are still running. For a final report, verify the `SURVEY_COMPLETE` marker in every seed's server log.

