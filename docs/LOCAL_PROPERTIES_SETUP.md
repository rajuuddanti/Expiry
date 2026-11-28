# Android Studio — local.properties setup

This guide explains how to create the local Gradle configuration file after cloning the MahaMart Expiry Manager repository.

## What is local.properties?

`local.properties` is a machine-specific configuration file in the **root of the Android project**. It is not the same as a Kotlin source file and it must not be placed inside the `app` folder.

The project currently reads these two values from this file:

- `SUPABASE_URL` — the existing Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY` — the existing Supabase publishable key.

Android Studio/Gradle also uses `sdk.dir` to locate the Android SDK. Android Studio normally creates or maintains this entry for the local computer.

> Important: use the existing project URL and publishable key supplied by the project owner. Do not create a new Supabase project or replace the existing values. Never put a Supabase service-role/secret key in the Android app or this file.

## Step-by-step: create local.properties in Android Studio

### 1. Open the cloned project

1. Start Android Studio.
2. Select **Open** (or **File → Open**).
3. Select the cloned `Expiry` project folder — the outer project folder, not the `app` folder.
4. Wait for Android Studio to finish loading the project.

### 2. Switch the left panel to Project view

1. Find the panel on the left, usually labelled **Project**.
2. Open the view dropdown at the top of that panel.
3. Select **Project** rather than **Android**. This makes it easier to see the actual project-root files.
4. Expand the top-level project folder.

At the top level you should see files/folders such as:

- `app`
- `gradle`
- `build.gradle.kts` (if present at project root)
- `settings.gradle.kts` (or `settings.gradle`)
- `gradle.properties`

Create `local.properties` at this same top level, alongside the project settings file. **Do not create it inside `app`, `app/src`, or a Kotlin package.**

### 3. Create the file

1. Right-click the top-level project folder.
2. Choose **New → File**.
3. Enter the exact filename: `local.properties`.
4. Press **Enter**.
5. If Android Studio says the file already exists, open the existing root-level `local.properties` instead of creating a second one.

If Android Studio does not show **New → File**, confirm that you right-clicked the top-level project folder in **Project** view.

### 4. Add the Supabase properties

Open `local.properties` and add these two lines, replacing the placeholders with the **existing** values:

```properties
SUPABASE_URL=YOUR_EXISTING_SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY=YOUR_EXISTING_SUPABASE_PUBLISHABLE_KEY
```

Do not include quotation marks around the values. Keep each property on its own line. Do not add spaces around the equals sign.

If Android Studio has already added an `sdk.dir=...` line, **leave it there**. Do not delete or replace that SDK path. If the file does not contain `sdk.dir`, Android Studio/Gradle may add it during project sync; if Gradle reports that the SDK location is missing, set it to the Android SDK location shown in **File → Settings → Languages & Frameworks → Android SDK** (Windows).

### 5. Save and sync Gradle

1. Press **Ctrl + S** to save the file.
2. Select **File → Sync Project with Gradle Files**. Depending on the Android Studio version, a **Sync Now** prompt may appear at the top; select it.
3. Wait for sync to finish.
4. If sync fails, read the first meaningful error in the **Build** or **Sync** output. Check the filename, spelling of both property names, and that the values were pasted completely.

### 6. Build the APK

After a successful Gradle sync:

1. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Wait for the build to finish.
3. Android Studio displays a notification with a link to locate the APK. For the debug build, it is normally under `app/build/outputs/apk/debug/app-debug.apk`.

## Alternative: copy the example file

The repository includes `local.properties.example` as a template.

1. In the project root, copy `local.properties.example`.
2. Rename the copy to `local.properties`.
3. Open `local.properties` and replace the placeholders with the existing Supabase URL and publishable key.
4. Keep any existing `sdk.dir` entry if Android Studio has added one.
5. Save, sync Gradle, and build.

The example file is only a template; Gradle reads `local.properties`, not `local.properties.example`.

## Common mistakes

- **Wrong folder:** the file is in `app/local.properties`. Move/create it in the outer project root.
- **Wrong filename:** it is named `local.properties.txt`. Ensure Windows File Explorer is showing file extensions and the filename is exactly `local.properties`.
- **Wrong property spelling:** use exactly `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`.
- **Quotes added:** do not wrap values in single or double quotes.
- **SDK path removed:** preserve the `sdk.dir` line created for this computer.
- **Example file edited only:** Gradle reads `local.properties`; copy the example to that exact filename first.

## Git and security

- `local.properties` is intentionally listed in `.gitignore`; it stays on the developer's computer and is not committed.
- Commit `local.properties.example` and this guide, but never commit the real `local.properties`.
- Do not paste private keys, service-role keys, passwords, signing credentials, or access tokens into source control.
- The Android app must use only the existing client-side publishable key. A service-role key must never be included in an APK.

## Note about the current Gradle configuration

The current `app/build.gradle.kts` reads the two properties and has fallback values if either property is absent. This guide documents the explicit local setup so developers can see and manage the configuration on their own machine. Do not change the existing Supabase project, URL, key, schema, or policies as part of this setup.
