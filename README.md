# Paste to Markdown

Paste to Markdown converts formatted HTML from the system clipboard into Markdown text. All conversion runs locally on the device without network requests.

## Overview

When you copy formatted text from a web browser or document editor, Android writes an HTML string to the system clipboard. This application reads that HTML and passes it to the Flexmark conversion library. You can edit the converted text directly in the editor, copy it to the clipboard, or write it to a file.

## Capabilities

- Converts HTML clipboard content to CommonMark output.
- Colors Markdown syntax in the text editor.
- Saves output directly to markdown files.
- Supports ATX and Setext heading styles.
- Supports hyphen, asterisk, and plus markers for list items.
- Supports backtick and tilde fences for code blocks.
- Supports asterisk and underscore delimiters for emphasis.
- Retains soft line breaks when the corresponding option is on.
- Pastes automatically on app launch when enabled.
- Copies output automatically after conversion when enabled.
- Uses dynamic theme colors from the Android system wallpaper.

## System requirements

- Android 8.0, API level 26, or higher.
- Java Development Kit 11 or higher.

## Build commands

Clone the repository and build the debug APK with Gradle:

```bash
git clone https://github.com/threehymns/pastetomarkdown.git
cd pastetomarkdown
./gradlew assembleDebug
```

Run unit tests with Gradle:

```bash
./gradlew test
```

## Source code structure

- `MainActivity.kt` coordinates screen state, permissions, and clipboard interactions.
- `MarkdownConverter.kt` configures the Flexmark parser and executes conversion passes.
- `MarkdownSyntaxHighlighter.kt` applies syntax colors inside the Compose text editor.
- `AppSettings.kt` loads and writes user preferences with SharedPreferences.
- `SettingsScreen.kt` provides controls for formatting rules and theme options.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
