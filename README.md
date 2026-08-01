# infeRead

infeRead is a privacy-centric, multi-format reading application for Android designed to provide a premium, distraction-free reading experience. Built from the ground up to respect your device storage and data privacy, infeRead operates entirely offline and connects directly to your existing documents without duplicating files.

---

## The Link-First Philosophy
Most reading applications copy your files into their private storage, doubling the space consumed on your device. infeRead takes a different path. It uses a persistent linking mechanism to reference documents exactly where they reside on your system. If you move or reorganize your files, you can update their paths inside the app with a single click, keeping your storage clean and unburdened.

---

## Highlight Features

### Media-Mixed Background Soundtracks
Elevate your reading sessions with ambient soundtracks. infeRead features built-in high-quality sound loops—such as morning birds, gentle river streams, rainstorms, and night ambience—that mix dynamically with system audio. You can also import your own audio files, manage them from the settings panel, and assign custom thumbnail artwork for a highly personalized atmosphere.

### Dynamic Text-to-Speech
Listen to your books hands-free. The integrated Text-to-Speech engine parses your EPUB documents at sentence boundaries to deliver natural pacing. 
* **Seamless Settings**: Adjust playback speed, volume, and voice gender directly from the reader settings.
* **Smart Language Filtering**: The app samples text from the current chapter to automatically detect the language and filter the voice selector to relevant accents.
* **Stop-on-Demand**: A dedicated floating action button appears while reading is active, letting you pause the narration with a single tap.

### Native Markdown Editor
Read and write in the same space. For writers and developers, infeRead lets you toggle between rendering a Markdown document and editing its source code. The basic text editor matches your active reader configuration, including fonts, font sizes, line spacing, and contrast settings. Your changes are saved in the background with a brief, non-intrusive debounce to ensure typing remains fluid and responsive.

### High-Performance Comic Reader
Open and read comic book archives (.cbz, .cbr, and .cb7) with zero delays. The app pre-extracts pages sequentially, providing live progress updates. Once loaded, pages are cached persistently on your device so that re-opening a volume is instantaneous. To protect your storage, infeRead automatically discards older comic caches, keeping only your active reading material on disk.

### Code Sandboxing and Previews
Preview HTML, CSS, JavaScript, and Markdown code directly within the app. The browser preview features desktop layout toggles and custom dark modes. When the system-wide Offline Mode is enabled, all external network requests (including XMLHttpRequests, fetches, and WebSockets) are blocked and redirected to secure local error handlers, ensuring your code executes in a completely offline environment.

---

## Supported Formats

infeRead supports a wide array of document, book, and source code formats:
* **Ebooks**: EPUB
* **Documents**: PDF, TXT, DOCX
* **Comics**: CBZ, CBR, CB7
* **Coding**: MD, PY, C, JAVA, JS, CSS, HTML
* **Images**: JPG, JPEG, PNG, WEBP, BMP, SVG, HEIC, HEIF

---

## Key Features at a Glance

### Tailored Library Organization
Sort your reading materials using customized segregation views. Group your library by format, page count, file size, bookmarks, or reading lists. You can expand, collapse, or drag to reorder your library sections, and the app will remember your preferences for next time.

### Deep Search
Locate content instantly across your entire library. The search engine scans not only book titles and authors, but also bookshelf names, checklist contents, and the full-text body of text documents under 10 megabytes.

### Reading Statistics
Understand your habits with detailed performance metrics. View your reading speed, track session durations, log total reading time, and monitor your overall progress as you move through your library.

### Checklists and Bookshelves
Organize your goals. Create custom reading lists, track tasks on internal checklists, and group related items on bookshelves. You can link these elements to Android home-screen widgets for quick access.

### Fully Customisable Reader
Design your perfect layout. Adjust brightness, text size, and line height. Select your preferred typeface and swap between standard white, sepia, dark mode, or high-contrast E-Ink themes for comfortable reading in any environment.
