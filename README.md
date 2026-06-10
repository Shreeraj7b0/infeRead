# infeRead

A feature-rich Android reading app built with Jetpack Compose. Supports multiple file formats, bookshelves, checklists, online sources, reading statistics, home-screen widgets, and a fully customisable reader experience.

---

## Table of Contents

1. [Overview](#overview)
2. [Supported File Formats](#supported-file-formats)
3. [Library Management](#library-management)
4. [File Import & Linking](#file-import--linking)
5. [Reader](#reader)
6. [Bookshelves](#bookshelves)
7. [Checklists](#checklists)
8. [Online Sources](#online-sources)
9. [Reading Statistics](#reading-statistics)
10. [Widgets](#widgets)
11. [Settings](#settings)
12. [Architecture](#architecture)
13. [Project Structure](#project-structure)

---

## Overview

infeRead is a privacy-first, offline-capable reading app. Your files stay on your device; nothing is uploaded externally. The app uses a **link-first** approach — when you add a file it creates a persistent pointer to the original rather than making a copy, so no disk space is wasted.

---

## Supported File Formats

| Category    | Extensions                          |
|-------------|-------------------------------------|
| Ebooks      | `.epub`                             |
| PDF         | `.pdf`                              |
| Text        | `.txt`, `.doc`, `.docx`             |
| Comic/Manga | `.cbz`, `.cbr`, `.cb7`              |
| Coding      | `.md`, `.py`, `.c`, `.java`, `.js`, `.css` |
| Images      | `.jpg`, `.jpeg`, `.png`, `.webp`, `.bmp`, `.svg`, `.heic`, `.heif` |

---

## Library Management

### Home Screen

The **Library tab** is the main view. Files are grouped by a user-selected **segregation mode**:

| Mode         | Description                                       |
|--------------|---------------------------------------------------|
| Format       | Grouped by file type (PDF, EPUB, TXT, etc.)       |
| Pages        | All files sorted by page count (descending)       |
| File Size    | All files sorted by on-disk size (descending)     |
| Bookmarked   | Only files you have bookmarked                    |
| Reading List | Only files you have marked "To Read"              |

### Section Management

- **Drag-to-reorder** sections via the handle icon in the navigation drawer.
- **Collapse/expand** sections with the arrow chevron next to each section header.
- Section order and collapse state are persisted per segregation mode.

### File Cards

Each file card shows:
- Thumbnail (auto-extracted, or set manually)
- Title, format badge, page count
- Reading progress bar
- Star rating (tap to rate 1–5)
- Bookmark and "To Read" indicators
- Finished badge (with completion date)

**Long-press** a card to open the context menu with options:
- Open in Reader
- Rename
- Rate (1–5 stars)
- Bookmark / Remove Bookmark
- Mark as To-Read / Remove from Reading List
- Mark as Finished / Mark as Unfinished
- Edit Thumbnail
- Add to / Remove from Bookshelf
- Convert PDF → EPUB
- Export (copy to `Downloads/infeRead/`)
- Share
- Relink (update the path if the file was moved)
- Delete from Library

### Search

- Tap the search icon in the top bar to expand a search field.
- **Deep search**: searches file titles, formats, checklist names, checklist items, bookshelf names, and the full text content of TXT files (files < 10 MB).
- Results are unified — books, checklists, bookshelves, and individual checklist items all appear together.
- Press Back to dismiss search.

---

## File Import & Linking

### Adding Files — the `+` Button

Tap the **+** floating action button (bottom-right, Library tab) to open the system file picker.

**Link vs. Import:**
- The app first attempts to **link** the file by acquiring a persistent `content://` URI permission. Linking stores only a pointer — the original file is never copied. This survives app restarts and most file moves on-device.
- If linking is not possible (e.g. cloud-only files that are not locally cached), the app **imports** (copies) the file into its private sandbox.
- A toast notification tells you which path was taken: *"Linking File…"* or *"Importing File…"*.

### Receiving Files from Other Apps (Share / Open With)

Files shared into infeRead from another app (e.g., a file manager, email client, or browser) are handled the same way: link first, import as fallback.

### Mass Import (from Settings → Import Manager)

- **Import Folder** — select an entire folder; every supported file inside is imported in batch.
- **Scan Folder for New Files** — selectively scan a folder, choose which extensions to include, and only add files not already in the library.
- **Multi-file picker** — select multiple individual files at once.
- Progress bar shows current / total files.
- Pause / Resume / Cancel controls for long imports.

### Linked vs Imported Files

- **Linked files** remain in their original location; deleting a linked file from the library removes only the database record.
- **Imported files** are stored inside the app's private sandbox; deleting an imported file from the library removes the database record **and** the copied file, but the original source file is unaffected.

### Relinking a File

If a linked file's original was moved, long-press it → **Relink** to point the record at the new location.

### Auto-Import from `Downloads/infeRead/`

On launch the app scans `Downloads/infeRead/` and automatically links any supported files found there that are not already in the library.

---

## Reader

The reader screen supports all file formats with per-format rendering:

### PDF Reader
- Rendered page-by-page using Android's `PdfRenderer`.
- Pinch-to-zoom and double-tap zoom.
- Page curl animation (swipe left/right).
- Continuous vertical scroll mode (toggle in reader settings).
- Jump to specific page via the scrubber or page number input.
- Night mode, Noir (greyscale) mode, Negative mode.
- Brightness slider overlay.
- Horizontal and vertical scroll directions.
- Crop / margin trim.

### EPUB Reader
- Full HTML/CSS rendering via a `WebView`.
- Custom fonts: 12+ font options (Serif, Sans-serif, Mono, Dyslexia-friendly, etc.).
- Font size, line height, letter spacing controls.
- Sepia, night, custom background colour themes.
- In-book search with previous/next result navigation.
- Text selection → copy, highlight, look up, translate, share.
- Annotation manager — view, jump to, and delete all highlights.
- Text-to-speech (TTS) with play/pause/stop and per-sentence highlighting.
- Auto-scroll with adjustable speed.

### TXT / Code Reader
- Rendered as scrollable text.
- Syntax highlighting for code files.
- Font and theme customisation shared with EPUB settings.

### Comic / CBZ / CBR / CB7 Reader
- Archive extraction with streaming support.
- Double-page spread mode.
- Left-to-right and right-to-left reading directions.
- Fit-width and fit-page zoom presets.

### Image Viewer
- Pan, pinch-to-zoom.
- Noir, Negative, and Colour filters.
- Export filtered image.

### Common Reader Features

| Feature | Description |
|---|---|
| Bookmarks | Tap the bookmark icon to save the current page. Multiple bookmarks per file. |
| Bookmark list | Pull up bookmarks panel; tap to jump to any saved page. |
| Reading progress | Progress percentage shown in the header; persisted on exit. |
| Keep-awake | Screen stays on while reading. |
| Auto-save | Position is saved automatically every few seconds and on exit. |
| Vertical scrubber | Drag the right-edge scrubber to quickly seek through a long document. |
| Chapter navigation | For EPUBs, jump directly to chapters from the table of contents. |

---

## Bookshelves

Bookshelves are named, colour-coded collections you can use to organise your library.

### Bookshelf Tab

Switch to the **Bookshelves** tab (second tab on the Home screen) to manage shelves.

**View modes:**
- **Shelf Stack** — shows each shelf as a stacked card display with cover art.
- **Vertical Stack** — compact list view.

**Sort modes** (cycle via the sort button):
- Alphabetical A→Z
- Alphabetical Z→A
- Book count ascending
- Book count descending

### Managing Bookshelves

- **Create** — tap the `+` FAB.
- **Rename** — long-press a shelf → Rename.
- **Colour** — long-press a shelf → Change Colour (full colour picker).
- **Delete** — long-press a shelf → Delete (books are not deleted, only the shelf).
- **Reorder** — long-press a shelf and drag to reorder.

### Adding Books to a Shelf

- Long-press a file card anywhere → **Add to Bookshelf** → pick one or more shelves.
- Alternatively, enable **Assignment Mode** (the bookshelf `+` FAB when already inside a shelf view), which lets you tap file cards to add/remove them.

---

## Checklists

Checklists are independent to-do/reading-list style lists, accessible from both the navigation drawer and the Library tab.

### Creating & Managing Checklists

- **New checklist** — tap `+` next to CHECKLISTS in the nav drawer, or use the main `+` area.
- **Rename** — long-press a checklist in the drawer → Rename.
- **Colour** — long-press → Change Colour (shown as a dot indicator).
- **Delete** — long-press → Delete.

### Checklist Items

Within an open checklist:
- **Add item** — type in the bottom input bar and press Enter or the send icon.
- **Check/uncheck** — tap the circle checkbox.
- **Rename item** — tap the item text to edit inline.
- **Delete item** — swipe left on the item, or use the item's context menu.
- **Indent / Outdent** — use Tab / Shift-Tab (or swipe right/left on the item) to nest items up to 3 levels deep.
- **Reorder** — long-press and drag the item.
- **Mark all done / Clear all** — from the checklist context menu.

### Exporting Checklists

Long-press a checklist → Export:
- **Export as PDF** — saves to `Downloads/infeRead/<name>.pdf`.
- **Share as PDF** — opens the system share sheet with the PDF attached.
- **Export as TXT** — saves to `Downloads/infeRead/<name>.txt`.
- **Share as TXT** — shares as plain text.

---

## Online Sources

The **Online** tab (accessible from the nav drawer) opens an in-app browser for reading sources.

- **Source switcher** — switch between configured online sources (e.g., Project Gutenberg, Anna's Archive).
- **Download manager** — files downloaded from online sources are tracked in a download panel. Completed downloads are automatically added to the library.
- **Active downloads panel** — shows in-progress downloads with file name, progress bar, and cancel button.
- **Offline mode** — toggle in Settings to disable the online tab entirely and prevent any network access.

---

## Reading Statistics

The **Stats screen** (accessible from the nav drawer or a dedicated stats icon) provides an overview of reading habits.

### Tracked Metrics

| Metric | Description |
|---|---|
| Total books | Number of files in library |
| Books finished | Count of completed books |
| Total pages read | Cumulative pages across all sessions |
| Total reading time | Cumulative time spent in the reader |
| Average session length | Mean reading session duration |
| Reading streak | Consecutive days with at least one reading session |

### Charts & Breakdowns

- **Pages read per day** — bar chart for the last 7 / 30 days.
- **Time read per day** — line chart.
- **Format breakdown** — pie chart of library composition by file type.
- **Most read books** — ranked list by pages read.
- **Recently read** — chronological list of last-opened files.

---

## Widgets

### Library Widget (`InfeReadWidget`)

A home-screen widget that shows your reading progress for a selected book.

- **Configuration activity** — launched when the widget is first placed; choose which book to display.
- Displays: book title, current page, total pages, and a visual progress bar.
- Tap the widget to open that book directly in the reader.

### Checklist Widget (`ChecklistWidget`)

A home-screen widget that displays one of your checklists at a time.

- Shows the checklist name and all items with their completion state.
- **Check/uncheck items** directly from the widget — no need to open the app.
- **Navigate between checklists** using the `‹` / `›` chevron buttons in the widget header.
- **Progress chip** (e.g. `3/7`) shows how many items are done.
- Tap the checklist title to open it full-screen in the app.
- **New list button** is shown when no checklists exist.
- Unchecked items appear before checked ones (mirrors Google Keep behaviour).

---

## Settings

Accessible via the gear icon in the nav drawer header.

### Reader Settings

| Setting | Description |
|---|---|
| Default font | Font family for EPUB/TXT rendering |
| Font size | Base font size |
| Line height | Line spacing multiplier |
| Letter spacing | Character spacing for readability |
| Background colour | Custom background for EPUB/TXT |
| Theme | Light, Sepia, Night, or Custom |
| Scroll direction | Horizontal (page-by-page) or Vertical (continuous) |
| Page animations | Enable/disable page curl |
| Continuous scroll | PDF continuous scroll toggle |
| Auto-scroll speed | Speed for auto-scroll feature |
| Keep screen on | Prevent sleep while reading |

### Library Settings

| Setting | Description |
|---|---|
| Card layout | Grid or list view |
| Cover size | Compact / Normal / Large thumbnails |
| Show progress | Toggle progress bar on cards |
| Sort within sections | Alphabetical, Date Added, Last Read, Rating |

### Import Settings

| Setting | Description |
|---|---|
| Import Manager | Full import/batch UI (see [File Import & Linking](#file-import--linking)) |
| Auto-import folder | Automatically scan `Downloads/infeRead/` on launch |

### Account & Sync

| Setting | Description |
|---|---|
| Profile | Set a display name and avatar |
| Reading goal | Daily page-count target |
| Offline mode | Disable all network access |

### About

App version, licences, and developer information.

---

## Architecture

infeRead follows **MVVM** (Model–View–ViewModel) with a single-activity architecture.

```
MainActivity
    └── NavHost (Compose Navigation)
            ├── SplashScreen
            ├── AuthScreen
            ├── HomeScreen          ← Library, Bookshelves, Checklists, Online tabs
            ├── ReaderScreen        ← Format-specific renderers
            ├── SettingsScreen
            └── StatsScreen
```

### Key Components

| Layer | Classes | Responsibility |
|---|---|---|
| **UI** | `HomeScreen`, `ReaderScreen`, `SettingsScreen`, `StatsScreen`, `BookShelfTab`, `OnlineStoreTab` | Compose UI, user interaction |
| **ViewModel** | `HomeViewModel`, `ReaderViewModel`, `OnlineStoreViewModel` | Business logic, state holders |
| **Data** | `FileRepository`, `InfeReadDao`, `InfeReadDatabase` | File I/O, database CRUD |
| **Models** | `LibraryFile`, `Checklist`, `ChecklistItem`, `Bookshelf`, `BookshelfItem`, `Annotation`, `ReadingSession`, `User` | Room entities |
| **Utils** | `EpubParser`, `EpubSanitizer`, `ArchiveExtractor`, `ArchiveStreamer`, `PdfToEpubConverter`, `CodeConverter` | Format parsing & conversion |
| **Network** | `AppDownloadManager`, `BookDownloader`, `GutenbergApi` | Download management, Gutenberg API |
| **Widgets** | `ChecklistWidget`, `InfeReadWidget`, config activities, receivers | Glance-based home-screen widgets |

### Data Persistence

- **Room** database (`InfeReadDatabase`) for all metadata: files, checklists, bookshelves, annotations, reading sessions.
- **SharedPreferences** for UI state: active tab, section order, reader settings, segregation mode.
- **DataStore** (via Glance's `PreferencesGlanceStateDefinition`) for widget state.

### File Storage Strategy

| File Type | Storage Location |
|---|---|
| Linked files | Original location on device (access via persistent `content://` URI) |
| Imported files | `filesDir/library/` private sandbox |
| Thumbnails | `filesDir/thumbnails/` |
| EPUB converted from PDF | Same directory as source PDF |
| Exported files | `Downloads/infeRead/` |
| Temp/share files | `cacheDir/` |

---

## Project Structure

```
app/src/main/
├── java/com/infer/inferead/
│   ├── MainActivity.kt                  # Single activity entry point
│   ├── data/
│   │   ├── Annotation.kt                # Highlight/annotation entity
│   │   ├── Bookshelf.kt                 # Bookshelf + BookshelfItem entities
│   │   ├── Checklist.kt                 # Checklist + ChecklistItem entities
│   │   ├── FileRepository.kt            # All file I/O: import, link, delete, export
│   │   ├── InfeReadDao.kt               # Room DAO — all SQL queries
│   │   ├── InfeReadDatabase.kt          # Room database definition
│   │   ├── LibraryFile.kt               # Main library item entity
│   │   ├── ReadingSession.kt            # Time-tracking entity
│   │   └── User.kt                      # User profile entity
│   ├── navigation/
│   │   └── AppNavigation.kt             # NavHost + deep-link handling
│   ├── network/
│   │   ├── AppDownloadManager.kt        # In-app download queue & progress
│   │   ├── BookDownloader.kt            # HTTP download helper
│   │   └── GutenbergApi.kt              # Project Gutenberg search API
│   ├── ui/
│   │   └── screens/
│   │       ├── AnnotationManagerDialog.kt
│   │       ├── AuthScreen.kt
│   │       ├── BookShelfTab.kt          # Bookshelves UI
│   │       ├── FormatRenderers.kt       # PDF, EPUB, TXT, Comic, Image renderers
│   │       ├── HomeScreen.kt            # Main library screen + nav drawer
│   │       ├── OnlineStoreTab.kt        # WebView-based online sources
│   │       ├── PageCurlModifier.kt      # Page curl animation
│   │       ├── ReaderScreen.kt          # Reader shell + controls
│   │       ├── SettingsScreen.kt        # All settings UI
│   │       ├── SharedNavPane.kt         # Resizable navigation drawer component
│   │       ├── SplashScreen.kt
│   │       ├── StatsScreen.kt           # Reading statistics
│   │       ├── TextSelectionData.kt
│   │       ├── VerticalScrubber.kt      # Right-edge page seek scrubber
│   │       └── Widgets.kt               # Reusable UI components
│   ├── utils/
│   │   ├── ArchiveExtractor.kt          # CBZ/CBR/CB7 extraction
│   │   ├── ArchiveStreamer.kt           # Streaming archive access
│   │   ├── CodeConverter.kt             # Syntax highlighting converter
│   │   ├── EpubParser.kt                # EPUB spine/chapter parser
│   │   ├── EpubSanitizer.kt             # HTML/CSS sanitisation for WebView
│   │   └── PdfToEpubConverter.kt        # PDF → EPUB conversion utility
│   ├── viewmodel/
│   │   ├── HomeViewModel.kt             # Library, checklist, bookshelf state
│   │   ├── OnlineStoreViewModel.kt      # Online tab state
│   │   └── ReaderViewModel.kt           # Reader state, sessions, annotations
│   └── widget/
│       ├── ChecklistWidget.kt           # Glance checklist widget + actions
│       ├── ChecklistWidgetReceiver.kt   # BroadcastReceiver for checklist widget
│       ├── InfeReadWidget.kt            # Glance reading-progress widget
│       ├── InfeReadWidgetConfigActivity.kt  # Widget book-picker config screen
│       └── InfeReadWidgetReceiver.kt    # BroadcastReceiver for reading widget
└── res/
    ├── drawable/                        # Icons, vector assets
    ├── layout/                          # Legacy XML layouts (if any)
    ├── values/                          # Colours, strings, themes
    └── xml/                             # Widget metadata, file provider config
```
