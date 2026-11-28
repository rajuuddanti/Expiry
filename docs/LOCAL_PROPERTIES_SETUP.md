# Android Studio — local.properties setup

This guide explains how to create the local Gradle configuration file after cloning the MahaMart Expiry Manager repository.

## What is local.properties?

`local.properties` is a machine-specific configuration file in the **root of the Android project**. It is not a Kotlin source file and it must not be placed inside the `app` folder.

The project requires these two values in this file:

- `SUPABASE_URL` — the existing Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY` — the existing Supabase publishable key.

Android Studio/Gradle also uses `sdk.dir` to locate the Android SDK. Android Studio normally creates or maintains this entry for the local computer.

> Important: use the existing project URL and publishable key. Do not create a new Supabase project or replace the existing values. Never put a Supabase service-role/secret key in the Android app or this file.

## Step-by-step: create local.properties in Android Studio

1. Open the cloned `Expiry` project in Android Studio.
2. In the left panel, switch the view dropdown to **Project**.
3. Right-click the top-level project folder (the folder containing `app` and `settings.gradle.kts`).
4. Choose **New → File** and name it exactly `local.properties`.
5. Add the following properties, replacing the placeholders with the existing project values:

```properties
SUPABASE_URL=YOUR_EXISTING_SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY=YOUR_EXISTING_SUPABASE_PUBLISHABLE_KEY
```

Do not add quotation marks or spaces around the equals signs. If Android Studio has already added an `sdk.dir=...` line, keep it. Do not delete or replace that SDK path.

6. Save the file and select **File → Sync Project with Gradle Files**.
7. After sync succeeds, build using **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Alternative: copy the example file

Copy `local.properties.example` in the project root, rename the copy to `local.properties`, and replace both placeholders with the existing values. Gradle reads `local.properties`, not the example file.

## Important build behavior

The Gradle build now **requires** the root `local.properties` file and both properties. It deliberately has no hardcoded fallback URL or key. If the file or either property is missing, Gradle Sync/Build stops with a clear error message.

## Git and security

- `local.properties` is listed in `.gitignore`; it stays on each developer's computer and must not be committed.
- Commit `local.properties.example` and this guide, but never commit the real `local.properties`.
- Use only the existing client-side publishable key. Never include a Supabase service-role/secret key in the APK.
- This is a developer build setup only. Managers installing an already-built APK do not need to create or edit `local.properties`.
