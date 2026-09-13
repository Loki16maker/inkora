# Inkora

Inkora is a local-first study workspace for PDFs, notebooks, annotations, and handwritten notes. It combines a document library with a spacious canvas so you can read, draw around pages, organize study material, and review it later.

[Download the latest release](https://github.com/Loki16maker/inkora/releases/latest) · [Windows MSI](https://github.com/Loki16maker/inkora/releases/download/v1.3.11/Inkora-1.3.11.msi) · [Android APK](https://github.com/Loki16maker/inkora/releases/download/v1.3.11/Inkora-1.3.11-android.apk)

## Features

- PDF reading with page navigation, contents, bookmarks, text search, OCR for scanned pages, and PDF export
- Draw on pages or in the surrounding workspace with pen, highlight, eraser, selection, pan, shapes, text, sticky notes, and images
- Margin notes and persistent annotations saved with each document
- Notebooks, whiteboards, quick notes, and basic rich text documents
- Windows File Explorer and Android system document picker imports
- DOC, DOCX, PPT, and PPTX text extraction for searchable study notes
- Study split view for comparing two documents
- Review scheduling with spaced-repetition intervals
- ChatGPT quiz handoff without an OpenAI API key: Inkora copies a prompt, opens the browser, and imports pasted quiz JSON
- Optional cloud accounts, sync, share links, invitations, and access roles
- GitHub-based in-app update checks for Windows and Android
- Light and dark themes, keyboard shortcuts, zoom controls, and a floating drawing toolbar

## Install

### Windows

Download the [MSI installer](https://github.com/Loki16maker/inkora/releases/download/v1.3.11/Inkora-1.3.11.msi) or the [portable ZIP](https://github.com/Loki16maker/inkora/releases/download/v1.3.11/Inkora-1.3.11-windows-portable.zip).

The MSI keeps the application registered for future updates. The portable ZIP can be extracted and run without an installer.

### Android

Download [Inkora-1.3.11-android.apk](https://github.com/Loki16maker/inkora/releases/download/v1.3.11/Inkora-1.3.11-android.apk), allow installation from the browser or file manager when Android asks, and install it over an existing Inkora installation.

## Creating a ChatGPT quiz

1. Open a document and select **Quiz**.
2. Choose a difficulty and select **Open ChatGPT**.
3. Paste Inkora's copied prompt into ChatGPT.
4. Copy ChatGPT's JSON response.
5. Select **Paste JSON** in Inkora and import the quiz.

Inkora does not store ChatGPT cookies or require an OpenAI API key. 9router is not required. The browser remains responsible for ChatGPT authentication.

## Cloud collaboration

The current cloud layer supports account sign-in, document sync metadata, private storage hooks, share links, invitations, and viewer/commenter/editor roles. Real-time simultaneous drawing and text editing are still planned; shared edits currently use saved snapshots and explicit sync.

Cloud configuration and database migrations are documented in [docs](docs).

## Build from source

Requirements:

- Windows for the desktop package task
- JDK 21 (the project targets JVM 17 bytecode)
- Android SDK with API 35 for Android builds
- Git

Clone the repository and run:

```powershell
git clone https://github.com/Loki16maker/inkora.git
cd inkora

# Compile Windows and Android targets
.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid --no-daemon

# Build a Windows MSI
.\gradlew.bat :composeApp:packageMsi --no-daemon --no-configuration-cache

# Build a debug Android APK
.\gradlew.bat :composeApp:assembleDebug --no-daemon
```

Release Android builds use the signing environment variables configured in the GitHub Actions workflow. Release packaging publishes the MSI, portable ZIP, signed APK, and `latest.json` manifest.

## Project layout

- `composeApp/src/commonMain` — shared Compose UI, models, storage, sync, and study features
- `composeApp/src/androidMain` — Android document picker, PDF renderer, OCR, updates, and sharing
- `composeApp/src/desktopMain` — Windows file access, PDFBox rendering/export, updater, and packaging
- `supabase/migrations` — cloud schema and row-level security policies
- `docs` — feature notes and setup documentation

## Current limitations

- Office files are imported as searchable text; full visual DOCX/PPTX page rendering is not yet available.
- Real-time collaborative cursors and simultaneous editing are not yet available.
- iOS host integrations remain placeholders.
- Cloud features require a configured Supabase project and network access.

## Contributing

Open an issue with a reproducible example or a focused feature proposal. Keep platform-specific code in its target source set and run the Windows and Android compile tasks before opening a pull request.

