# Desktop Task Helper

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.0-02303A.svg)](https://gradle.org)

A lightweight system tray application that keeps you on top of **Jira tasks** and **GitHub pull requests** requiring your attention. It lives quietly in your system tray, shows a badge with the number of actionable items, and sends desktop notifications when new items arrive.

---

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Building](#building)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)
- [Author](#author)

## Features

- **System tray badge** -- red circle with item count when attention is needed, green checkmark when all clear
- **Jira integration** -- polls for tasks matching a configurable JQL query via REST API v3
- **GitHub integration** -- finds PRs where your review is requested using `gh` CLI (works with GitHub Enterprise)
- **Desktop notifications** -- native notifications via `notify-send` (Linux) or `osascript` (macOS)
- **Click to open** -- click any item in the tray menu to open it in your default browser
- **Settings UI** -- configure Jira credentials, JQL, and polling interval from the tray menu (API token is masked)
- **Cross-platform** -- Linux (native AppIndicator) and macOS (AWT SystemTray)
- **Self-contained binary** -- build a standalone executable with bundled JRE via `jpackage`

## Screenshots

<!-- Add screenshots here -->
<!-- ![Tray Menu](docs/screenshot-menu.png) -->
<!-- ![Settings](docs/screenshot-settings.png) -->

## Prerequisites

### For building

- **JDK 25+** ([install guide](https://openjdk.org/install/))

### For running

- **GitHub CLI** (`gh`) -- installed and authenticated
  ```bash
  # Install: https://cli.github.com/
  gh auth login
  ```
- **Jira API Token** -- generate at [Atlassian API Tokens](https://id.atlassian.com/manage-profile/security/api-tokens)
- **Linux only**:
  - `libayatana-appindicator3` -- native system tray (pre-installed on Ubuntu/GNOME)
  - `notify-send` -- clickable desktop notifications (from `libnotify-bin`, pre-installed on most desktops)
  ```bash
  # If missing:
  sudo apt install libayatana-appindicator3-1 libnotify-bin
  ```

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/adapik/desktop-task-helper.git
cd desktop-task-helper
```

### 2. Run

```bash
./gradlew run
```

On first launch, a configuration file is created at `~/.desktop-task-helper/config.properties`. Fill in your Jira credentials (or use **Settings** from the tray menu), and the app will start polling.

### 3. How it works

1. The app polls Jira and GitHub in parallel at a configurable interval (default: 2 min)
2. A tray icon shows the total number of items needing your attention
3. Click the tray icon to see the list of Jira tasks and GitHub PRs
4. Click any item to open it in your browser
5. When a task moves to a different status or a PR no longer needs your review, it disappears from the list
6. New items trigger a desktop notification

## Configuration

Edit `~/.desktop-task-helper/config.properties` directly or use **Settings** from the tray menu:

```properties
# Jira Configuration
jira.base_url=https://your-company.atlassian.net
jira.email=your-email@company.com
jira.api_token=your-jira-api-token
jira.jql=assignee = currentUser() AND status in ("In Review", "Code Review", "Waiting for review")

# GitHub: uses 'gh' CLI -- no token needed
# Make sure 'gh auth login' is completed

# Polling interval in seconds (min: 30, max: 3600)
poll.interval_seconds=120

# Desktop notifications
notifications.enabled=true
```

### Jira JQL Examples

```sql
-- Tasks assigned to you in review
assignee = currentUser() AND status in ("In Review", "Code Review")

-- Tasks where you are a reviewer
reviewer = currentUser() AND status = "In Review"

-- High priority tasks in your project
project = MYPROJ AND assignee = currentUser() AND priority in (High, Highest)
```

## Building

### Run from source

```bash
./gradlew run
```

### Fat JAR

```bash
./gradlew jar
java -jar build/libs/desktop-task-helper-1.0.0.jar
```

### Standalone Binary (recommended)

Creates a self-contained application with a bundled minimal JRE. No Java installation required on the target machine.

**Linux** (build on Linux):

```bash
./gradlew jpackage
```

Output: `build/package/desktop-task-helper/`

```bash
# Run
./build/package/desktop-task-helper/bin/desktop-task-helper

# Or copy anywhere
cp -r build/package/desktop-task-helper /opt/
/opt/desktop-task-helper/bin/desktop-task-helper
```

**macOS** (build on macOS):

```bash
./gradlew jpackage
```

Output: `build/package/desktop-task-helper.app`

```bash
# Move to Applications
mv build/package/desktop-task-helper.app /Applications/

# Or run directly
open build/package/desktop-task-helper.app
```

> **Note:** Cross-compilation is not supported by `jpackage`. You must build on the target OS.

## Architecture

```
src/main/kotlin/com/taskhelper/
    Main.kt                          -- Entry point, wiring
    config/
        Config.kt                    -- Configuration loading/saving
        SettingsDialog.kt            -- Swing settings UI
    client/
        JiraClient.kt               -- Jira REST API v3 client (OkHttp)
        GitHubClient.kt             -- GitHub PR search via 'gh' CLI
    model/
        TaskItem.kt                 -- Unified task/PR data model
    tray/
        TrayManager.kt              -- System tray (AppIndicator on Linux, AWT on macOS)
    notification/
        NotificationManager.kt      -- Desktop notifications
    service/
        PollingService.kt           -- Async polling with coroutines
```

### Key Design Decisions

- **`gh` CLI for GitHub** instead of API tokens -- works seamlessly with GitHub Enterprise and SSO
- **AppIndicator via JNA** on Linux -- AWT SystemTray uses XEmbed which renders incorrectly on GNOME/KDE; AppIndicator provides native tray integration
- **AWT SystemTray fallback** on macOS -- works correctly on macOS without native libraries
- **Coroutines** for parallel polling -- Jira and GitHub are fetched concurrently
- **Icon rendered as PNG file** for AppIndicator -- GNOME reads the icon from disk, ensuring pixel-perfect rendering

### Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.3.0 |
| Gradle | 9.4.0 |
| JDK | 25 |
| kotlinx-coroutines | 1.9.0 |
| kotlinx-serialization | 1.7.3 |
| OkHttp | 4.12.0 |
| JNA | 5.14.0 |
| SLF4J | 2.0.16 |

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License -- see the [LICENSE](LICENSE) file for details.

## Author

**Alexander Danilov** ([@adapik](https://github.com/adapik))
