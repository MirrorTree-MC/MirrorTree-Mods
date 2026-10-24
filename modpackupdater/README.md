# ModpackUpdater

## Overview
ModpackUpdater is a Fabric mod designed to strictly synchronize modpack files (mods, resource packs, configuration, etc.) upon the client's startup. It ensures players always have the exact version of the modpack as defined by the creator, automatically updating, adding, or removing files as needed.

## For Players / User Guide
This mod is intended to be pre-installed in a modpack.

**How it works during gameplay:**
1.  **Launch**: When you launch the game, the mod automatically connects to a remote update server.
2.  **Check & Download**: It compares your local files with the server's manifest. If updates are found (new mods, updated configs, removed mods), it downloads/deletes files accordingly.
3.  **UI Feedback**: You will see a "Checking for updates..." or "Updating..." overlay on your screen.
4.  **Restart**: Once the update process checks are complete and files are synchronized, if any changes were made, a prompt will ask you to restart the game to apply the changes effectively.

**Note**: This mod ensures consistency. Valid files that are not in the manifest (e.g., extra mods you added manually) might be deleted if configured so by the modpack creator (specifically for mods folder cleanup).

## For Modpack Creators / Developers

### Architecture
The update system consists of three main components:
1.  **Client-side Mod**: `ModpackUpdater` (this mod) runs on the player's client.
2.  **Update Manifest**: A JSON file (`modpack_update_manifest.json`) hosted online that acts as the source of truth for all file versions/hashes.
3.  **Generator Tool**: A Python script (`tool/modrinth_get.py`) to automate generating the manifest from Modrinth and manual entries.

### Configuration & Setup

#### 1. Mod Configuration (Config File)
The update URL is configured via a JSON file.

*   **File**: `config/modpackupdater.json` (created after first launch)
*   **Content**:
    ```json
    {
      "updateUrl": "https://your-file-server.com/modpack_update_manifest.json"
    }
    ```

You can ship this config file with your modpack (in the `overrides/config/` folder) to ensure players have the correct URL out of the box.

If the config file is missing or the URL is not set, the mod will log an error and skip the update check.

#### 2. Generating the Update Manifest
To manage the files your players receive, you use the `modpack_update_manifest.json`. You can generate this file using the included Python script.

**Prerequisites:**
- Python 3 installed.
- Install required libraries: `pip install requests tqdm`

**Step A: Configure Automatic Mod Updates (Modrinth)**
- Edit `tool/modrinth_get.py`.
- Update the `slug_list` array with the Modrinth slugs of the mods you want to automatically track and update.

**Step B: Configure Manual Files**
For files not on Modrinth (e.g., custom configs, specific resource packs, local mods), edit `data/manual_links.json`.

*   **Format Example**:
    ```json
    {
      "files": [
        {
          "path": "mods/CustomMod.jar",
          "url": "https://your-file-server.com/files/CustomMod.jar",
          "mode": "version",
          "type": "mod",
          "version": "1.0.0",
          "id": "custommod"
        },
        {
          "path": "config/some-config.json",
          "url": "https://your-file-server.com/files/config.json",
          "mode": "hash"
        }
      ],
      "delete": [
        "mods/OldLegacyMod.jar"
      ]
    }
    ```
    *   `mode`: `version` (uses version string comparison), `hash` (uses SHA-256 hash), or `always` (force download every time).

**Step C: Run the Generator**
Run the script to fetch latest versions and merge manual links:
```bash
python3 tool/modrinth_get.py
```
This script will output/update `data/modpack_update_manifest.json`.

#### 3. Hosting
1.  Upload the generated `modpack_update_manifest.json` to your web server / GitHub Release / Cloud Storage.
2.  Ensure the URL matches what you hardcoded in Step 1.
3.  Ensure all file URLs referenced in the manifest are publicly accessible.

### Building from Source
1.  Clone the repository.
2.  Open in IntelliJ IDEA (recommended).
3.  Run `genSources` gradle task.
4.  Run `build` gradle task to compile.

### Licensing
This project is licensed under the terms included in the `LICENSE` file.
