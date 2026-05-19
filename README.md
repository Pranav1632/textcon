# Text Converter (Java Swing)

Desktop app to convert Markdown into chat-platform-ready text for WhatsApp, Telegram, Discord, and Slack.

> Current target platform: **Windows 10/11 only**.

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

## Create Windows Desktop App (Installer)

1. Install **JDK 17+** (with `jpackage`).
2. For `msi`/`exe` output, install **WiX Toolset** and ensure it is on `PATH`.
3. Run the packaging script from project root:

```powershell
.\scripts\package-windows.ps1 -Type msi
```

Alternative package types:

```powershell
.\scripts\package-windows.ps1 -Type exe
.\scripts\package-windows.ps1 -Type app-image
.\scripts\package-windows.ps1 -Type all
```

Generated files are written to:

```text
dist\
```

Useful options:

```powershell
.\scripts\package-windows.ps1 -Type msi -AppVersion 1.2.0
.\scripts\package-windows.ps1 -Type all -OutputDir release
.\scripts\package-windows.ps1 -Type exe -SkipTests
.\scripts\package-windows.ps1 -Type msi -IconPath "D:\icons\textcon.ico"
```

### Raw jpackage command (manual)

```powershell
.\mvnw.cmd clean package
jpackage --type msi --name TextCon --input target --main-jar TextConverter.jar --main-class com.textcon.Main --dest dist --app-version 1.0.0 --vendor "TextCon" --description "Markdown text converter desktop app" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --java-options "--enable-native-access=ALL-UNNAMED"
```

## Manual Steps You Still Need

1. Install and maintain toolchain on your release machine:
   - JDK 17+ with `jpackage`
   - WiX Toolset for `msi` and `exe`
2. Provide a production app icon (`.ico`) if you want branded installer/shortcut visuals.
3. Test installer on clean Windows 10 and Windows 11 machines (install, launch, uninstall, upgrade).
4. For public distribution, code-sign the generated `.exe`/`.msi` using your certificate.
5. Keep versioning discipline: update `pom.xml` version before each release (or pass `-AppVersion`).

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
  export_path TEXT,
  export_format VARCHAR(20),
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
