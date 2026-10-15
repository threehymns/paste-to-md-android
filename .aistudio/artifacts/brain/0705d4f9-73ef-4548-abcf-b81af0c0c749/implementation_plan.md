# Settings Page Implementation Plan

A modern, cohesive Settings experience for Paste to Markdown that allows users to customize app appearance (System/Light/Dark mode, Dynamic Color), configure Markdown conversion rules (heading styles, bullet markers, code fences, line break handling), and toggle workflow automations (auto-paste on launch, auto-copy converted output).

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your confirmed choices in the interview phase:
> - **Settings Categories**: Appearance, Markdown formatting rules, and clipboard workflow automations.
> - **Theme Customization**: System default, Light, and Dark modes with an Android 12+ Dynamic Color toggle.
> - **Clipboard Automations**: Configurable toggle switches for Auto-paste on app launch and Auto-copy converted Markdown.

- **Confirmed Decision 1**: Persist preferences locally using Android's native `SharedPreferences` for fast, reactive access without adding unnecessary network or database overhead.
- **Confirmed Decision 2**: Modern Material Design 3 grouped settings layout featuring distinct category cards, descriptive subtexts, interactive switches, selection dialogs, and clear touch affordances.
- **Confirmed Decision 3**: Dynamic Markdown converter configuration that applies formatting preferences (ATX headers, bullet types, emphasis delimiters) directly to Flexmark converter instances.

---

## 1. Overview & Core Concept

- **What It Does**: Adds a dedicated Settings screen accessible via a gear icon in the top app bar, allowing users to tailor how rich text/HTML is parsed into Markdown and how the app interacts with system theme and clipboard.
- **Target Audience / Persona**: Writers, developers, note-takers, and power users who transfer content from web browsers, emails, or docs into Markdown-based knowledge bases (Obsidian, Notion, GitHub, Logseq) and want consistent formatting rules and time-saving clipboard automation.
- **Key Value**: Eliminates repetitive manual cleanup of pasted Markdown (e.g. converting setext underlines to ATX `#` tags, changing `*` bullets to `-`) and provides visual comfort via custom themes and fast automated clipboard workflows.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Accessing Settings**:
   - The user opens the app and sees a top navigation bar displaying the app title and a settings icon button.
   - Tapping the settings icon navigates smoothly to the Settings screen with a back arrow and hardware back gesture support (`BackHandler`).
2. **Customizing Appearance**:
   - In the **Appearance** section, the user taps the Theme Mode selector to toggle between "Follow System", "Light", and "Dark".
   - On Android 12+, a "Dynamic Colors" switch allows turning Material You wallpaper-based theming on or off.
   - The theme updates immediately without requiring an app restart.
3. **Configuring Markdown Formatting**:
   - In the **Markdown Syntax** section, users can configure:
     - **Heading Style**: ATX (`# Heading`) vs Setext (`===` / `---`).
     - **Bullet Marker**: Dash (`-`), Asterisk (`*`), or Plus (`+`).
     - **Code Block Style**: Fenced (` ``` `) vs Indented (4 spaces).
     - **Emphasis Marker**: Asterisk (`**bold**`, `*italic*`) vs Underscore (`__bold__`, `_italic_`).
     - **Soft Breaks**: Preserve soft line breaks as two spaces or convert to single paragraphs.
4. **Clipboard & Workflow Automation**:
   - **Auto-Paste on App Launch**: Automatically checks clipboard on resume and converts rich text if available.
   - **Auto-Copy Converted Output**: Automatically copies the generated Markdown directly back to the clipboard upon conversion, showing a brief toast confirmation.
5. **Reset & About**:
   - Shows the app version and open-source Flexmark credit.
   - Includes a "Reset to Defaults" option with confirmation to quickly restore out-of-the-box defaults.

### Visual Identity & Theme

- **Aesthetic Direction**: Clean, utilitarian Material 3 design with elevated grouped cards, semantic surface tints, and clear hierarchy.
- **Color Palette & Atmosphere**:
  - Primary colors respect the selected theme mode and dynamic colors palette.
  - Category containers use `surfaceContainerLow` and `surfaceContainerHigh` with rounded corners (16.dp to 24.dp).
  - Switches and selection pills use vibrant primary accents with smooth ripple states.
- **Typography & Rhythm**:
  - Category headers: `titleSmall` in `primary` color with uppercase letter spacing.
  - Setting titles: `bodyLarge` with prominent font weight.
  - Secondary descriptions: `bodySmall` in `onSurfaceVariant`.
- **Accessibility & Spacing**:
  - All interactive tiles maintain minimum 48.dp touch targets.
  - Clear icon indicators beside each major category (Palette, Code/Format, Clipboard, Info).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Persistence Mechanism (SharedPreferences vs DataStore)**
  - *Chosen Approach*: Lightweight Android `SharedPreferences` exposed as reactive Kotlin state flows inside a dedicated `AppSettings` repository.
  - *Why*: SharedPreferences is built into the Android framework, requires zero new external libraries, starts up synchronously with zero latency on first frame render, and handles dozens of scalar configuration keys with maximum reliability.
  - *Alternatives Considered*: Jetpack DataStore was considered, but adds extra dependencies and asynchronous datastore setup for simple key-value settings.

- **Decision 2: Navigation Strategy (State-Based Navigation with BackHandler)**
  - *Chosen Approach*: ViewModel/State-driven screen switching (`currentScreen` enum: `Converter` vs `Settings`) paired with `BackHandler` and top bar back navigation.
  - *Why*: The app has two primary screens (Main Converter and Settings). State-driven screen switching avoids heavyweight navigation graph overhead while preserving full system gesture and hardware back button support.
  - *Alternatives Considered*: Full Navigation Compose library. Adding NavHost for a 2-screen utility app introduces unnecessary serialization and route graph boilerplate.

- **Decision 3: Flexmark Converter Builder Customization**
  - *Chosen Approach*: Dynamically configure `FlexmarkHtmlConverter` builder options based on the active `AppSettings` state, memoized or rebuilt when settings change.
  - *Why*: Flexmark provides rich conversion options (e.g. `EXTENSIONS`, `LIST_BULLET_MARKER`, `SETEXT_HEADINGS`, `CODE_SUB_EXTENSIONS`). Passing user preferences directly into the converter provides true output customization.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       MainActivity                          │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │  AppSettingsProvider   │       │   App Navigation Bar  │  │
│  │ (Dark/Light/Dynamic)   │       │ (Title + Settings/Back)│  │
│  └───────────┬───────────┘       └───────────┬───────────┘  │
│              │                               │              │
│              ▼                               ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Screen State Switcher                    │  │
│  │                                                       │  │
│  │   ┌────────────────────────┐  ┌────────────────────┐  │  │
│  │   │  PasteToMarkdownScreen │  │   SettingsScreen   │  │  │
│  │   │                        │  │                    │  │  │
│  │   │ - Text conversion      │  │ - Appearance       │  │  │
│  │   │ - Auto-paste / copy    │  │ - Markdown Syntax  │  │  │
│  │   │ - Syntax highlighting  │  │ - Clipboard Rules  │  │  │
│  │   │ - Dynamic Flexmark     │  │ - Reset to Default │  │  │
│  │   └────────────────────────┘  └────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                              │
│                              ▼                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │               AppSettings Repository                  │  │
│  │  (SharedPrefs backing store + reactive StateFlow)     │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Model & Settings Keys

- **Theme Settings**:
  - `theme_mode`: `System` | `Light` | `Dark`
  - `dynamic_color`: `Boolean` (default `true`)
- **Clipboard Settings**:
  - `auto_paste_on_launch`: `Boolean` (default `false`)
  - `auto_copy_on_convert`: `Boolean` (default `false`)
- **Markdown Rules**:
  - `heading_style`: `ATX` (`#`) | `SETEXT` (`===`)
  - `bullet_marker`: `-` | `*` | `+`
  - `code_block_fence`: ```` ``` ```` | `~~~`
  - `emphasis_marker`: `*` | `_`
  - `preserve_soft_breaks`: `Boolean` (default `false`)

### Verification Plan

- Run `compile_applet` to ensure clean compilation without any dependency errors.
- Verify screen transitions between the Markdown Converter and Settings page.
- Test that toggling Light/Dark theme and Dynamic Color instantly refreshes the UI.
- Verify that Markdown formatting selections correctly reflect in the converted output.
