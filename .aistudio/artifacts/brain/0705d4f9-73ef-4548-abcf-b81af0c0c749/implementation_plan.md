# Implementation plan for project README

This plan defines the content and structure for the repository README.md. The README explains what the app does, how to use it, the libraries it uses, and how to build and test it.

## User review and decisions

> [!IMPORTANT]
> - The README covers both end-user instructions and developer documentation.
> - The README omits ASCII architecture diagrams.
> - The text follows clean, unslop writing guidelines.

- Confirmed decision: The README organizes information into distinct sections with sentence-case headings.
- Confirmed decision: The architecture and technology stack use plain tables and concise descriptions.

---

## 1. Overview

Paste to Markdown is an Android app that converts formatted clipboard contents into standard Markdown. When a user copies formatted content from a web page, an email, or a document, the app reads the HTML markup from the clipboard and converts it to Markdown.

The app runs entirely on the device. It makes no network requests.

---

## 2. Document structure

The README will contain these sections:

1. Project name and description. A direct summary of the app purpose.
2. Features. Specific conversion capabilities and user settings.
3. How to use. Direct steps for converting text and changing settings.
4. Settings and configuration. Details on heading styles, bullet characters, code fences, and theme choices.
5. Tech stack and libraries. A table listing the core Android and Kotlin libraries.
6. Local development. Prerequisites and shell commands to build and run the app.
7. Testing. Commands to run unit and screenshot tests.

---

## 3. Decisions and trade-offs

- Plain language. Technical sections name exact classes, functions, and commands.
- Concrete feature descriptions. Rather than describing the interface as modern or intuitive, the document describes the exact controls: buttons, switches, and pickers.
- No ASCII diagrams. Component roles appear in a structured table.

---

## 4. Technical stack and architecture

### Architecture components

| Component | Class | Responsibility |
|---|---|---|
| Main activity | `MainActivity` | Hosts the screens and handles system back gestures. |
| Converter screen | `ConverterScreen` | Reads clipboard text, displays input and converted output, and applies syntax colors. |
| Settings screen | `SettingsScreen` | Displays theme options, Markdown syntax options, and clipboard automations. |
| Conversion engine | `MarkdownConverter` | Wraps Flexmark to transform HTML into Markdown according to stored preferences. |
| Settings storage | `AppSettingsRepository` | Reads and writes configuration values to Android SharedPreferences. |

### Technology stack

| Category | Library or tool | Version or role |
|---|---|---|
| Language | Kotlin | Modern language features, coroutines, and flows |
| UI | Jetpack Compose | Material 3 components and layouts |
| HTML parser | Flexmark `flexmark-html2md` | `0.64.8` for HTML to CommonMark conversion |
| Persistence | Android SharedPreferences | Stores settings locally without database overhead |
| Test runner | JUnit 4, Robolectric | Runs tests on local JVM without an emulator |
| Screenshot tests | Roborazzi | Captures and compares Compose UI screenshots |
| Build system | Gradle with Kotlin DSL | Manages build steps and dependency catalogs |
