# Hytale Server JAR

Place the `HytaleServer.jar` file in this directory, or configure the path in `gradle.properties`.

The build script will automatically look for the JAR in the following locations (in order):
1. `libs/hytale-server.jar` (this directory)
2. macOS: `~/Library/Application Support/Hytale/install/{patchline}/package/game/{gameBuild}/Server/HytaleServer.jar`
3. Windows: `~/AppData/Roaming/Hytale/install/{patchline}/package/game/{gameBuild}/Server/HytaleServer.jar`

You can configure `gameBuild` and `patchline` in `gradle.properties` if needed.
