# Text Converter (Java Swing)

Desktop app to convert Markdown into chat-platform-ready text for WhatsApp, Telegram, Discord, and Slack.

![Screenshot Placeholder](docs/screenshot-placeholder.png)

## Features

- Split-pane UI with Markdown input and converted output
- Format switcher for WhatsApp, Telegram, Discord, and Slack
- Auto live conversion (no manual Convert button)
- Copy, Clear, Save As, and one-click Save As PDF/Word/HTML actions
- File menu (Open/Save) and history viewer dialog
- Settings menu with app theme, export theme, preview delay, and history-on-copy toggle
- Tools menu for quick history save and operational actions
- SQLite-backed conversion history with delete and clear actions
- TXT, tagged TXT, PDF, Word (DOCX), HTML, JSON, RTF, and Markdown export support
- Real-time conversion preview with 300ms debounce
- Character counts and Markdown tag detection summary
- Undo/Redo and keyboard shortcuts

## Tech Stack

| Technology | Purpose |
|---|---|
| Java Swing | Desktop GUI |
| SQLite + sqlite-jdbc | Local history persistence |
| iText 5 | PDF export |
| JUnit 5 | Unit and DAO testing |
| Maven Shade Plugin | Fat JAR packaging |

## How To Run

1. Install Java 17+.
2. Build and test (using Maven Wrapper):

```powershell
.\mvnw.cmd clean test
```

3. Package executable fat JAR:

```powershell
.\mvnw.cmd clean package
```

4. Run the app:

```powershell
java -jar target\TextConverter.jar
```

The packaged JAR includes `Enable-Native-Access: ALL-UNNAMED` in the manifest to avoid the sqlite native-access warning on recent JDKs.

## IntelliJ Setup

1. Open `D:\project\textcon` as a project.
2. Set Project SDK to **Java 17** (`File > Project Structure > Project`).
3. Ensure Maven uses wrapper:
   - `Settings > Build, Execution, Deployment > Build Tools > Maven`
   - Maven home path: **Bundled (Maven 3)** or wrapper auto-detected.
4. Reload Maven project from the Maven tool window.
5. Create an Application run config:
   - Main class: `com.textcon.Main`
   - Use classpath of module: `textcon`
   - JRE: Java 17
   - Optional VM option (suppresses sqlite warning): `--enable-native-access=ALL-UNNAMED`

If `mvnw.cmd` says `JAVA_HOME not found`, set it once in PowerShell:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-24"
```

## IntelliJ Run Commands (Maven Tool Window or Terminal)

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\TextConverter.jar
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS conversion_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  original_text TEXT,
  converted_text TEXT,
  conversion_type VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Keyboard Shortcuts

- `Ctrl+Enter` Convert
- `Ctrl+C` Copy output (after conversion)
- `Ctrl+H` Open history
- `Ctrl+Z` Undo input
- `Ctrl+Y` Redo input

## Future Scope

- Import/export JSON history
- Theme switch (light/dark)
- Batch file conversion mode
- Custom conversion rules per platform
- Auto-update and installer packaging
