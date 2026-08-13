# Product Requirements Document (PRD): tweather

## 1. Project Overview
**Product Name:** tweather
**Platform:** Android (Kotlin + Jetpack Compose)
**Vision:** A high-fidelity weather application that emulates the interface of a modern code editor (inspired by Obsidian and VS Code). The entire UI is presented as a series of dynamic files (`weather_data.json`, `search_query.json`, `settings.config`, `weather_history.diff`), complete with syntax highlighting, line numbers, and a developer-centric aesthetic.

---

## 2. Target Audience
- Developers and tech enthusiasts who appreciate "code-as-UI" aesthetics.
- Users looking for a minimalist, text-heavy weather experience without traditional graphical bloat.
- Fans of tools like Obsidian, VS Code, or CLI-based utilities.

---

## 3. Core Features & Functional Requirements

### 3.1. Code Canvas (Main Interface - `weather_data.json`)
- **Dynamic JSON Rendering**: Maps real-time weather data into a formatted JSON structure.
- **Syntax Highlighting**:
    - **Keys**: Blue (`#79c0ff`)
    - **String Values**: Light Blue/Green (`#a5d6ff`)
    - **Numbers/Booleans**: Orange (`#ffa657`)
    - **Comments**: Grey (`#8b949e`)
    - **Braces/Brackets**: White/Muted Grey (`#8b949e`)
- **Line Numbers**: A vertical gutter on the left side with incremental line numbering.
- **Weather Icons**: Integrated directly into the JSON as standard Unicode symbols (e.g., `☀️`, `🌧️`, `⛅`).

### 3.2. Data Modules (Weather Data)
- **Location**: City, region, country, and precise coordinates.
- **Current Conditions**: Temperature, "feels like," humidity, and weather status with icons.
- **Environmental Data**: Air Quality Index (PM2.5, PM10, etc.), UV Index, and Pollen levels.
- **Forecasts**: 24-hour hourly array and 7-day daily array.
- **Astronomy**: Sunrise/sunset times, moon phase (with icons like `🌔`), and daylight duration.
- **System Info**: API source, last sync timestamp, and cache status.

### 3.3. Navigation & Controls
- **Top Bar**: Displays the active file name (e.g., `terminal tweather.json`) as an editor tab.
- **Refresh Button**: A terminal-style Floating Action Button (FAB) or "Run" icon to fetch fresh data.
- **Bottom Navigation**: Tabs for **Explorer** (City browser), **Search** (City search), **Settings** (App configuration), and **Logs** (Historical changes).

### 3.4. Search Interface (`search_query.json`)
- **Input as Code**: Modeled as a JSON object where the `"search_term"` property acts as the input field.
- **Recent Searches**: Displays an array of previous searches within the JSON structure.

### 3.5. Settings & Configuration (`settings.config`)
- **Configuration File Pattern**: Settings are managed via a `.config` style interface.
- **Dynamic Toggles**: Boolean values represent app settings (e.g., `"severe_weather_alerts": true`).
- **Theme Selection**: Array of `"available_profiles"` (Obsidian, Dracula, Monokai).

### 3.6. Logs & History (`weather_history.diff`)
- **Git Diff Metaphor**: Weather update history presented as a "diff" file.
- **Commit Logic**: Each data fetch is a "commit" with a unique hash, author (`sys@tweather.app`), and relative timestamp.
- **Syntax Highlighting for Changes**:
    - **Additions (`+`)**: Green (`#2ea043`) for new values.
    - **Deletions (`-`)**: Red (`#f85149`) for old/superseded values.

---

## 4. Technical Specifications
- **Language**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose (Material 3)
- **Networking**: Retrofit with OkHttp / Kotlinx.serialization.
- **Typography**: **JetBrains Mono** (Mandatory for the monospaced editor feel).

---

## 5. UI/UX Design (Obsidian Syntax Theme)
- **Background (Surface)**: `#10141a`
- **Surface Container**: `#181c22`
- **Primary Accent**: `#79c0ff`
- **Text (On-Surface)**: `#e6edf3`
- **Brand Identity**: Logo featuring a cloud within curly braces `{ ☁️ }`, using syntax highlighting colors.

---

## 6. Project Assets
- **Design System**: {{DATA:DESIGN_SYSTEM:DESIGN_SYSTEM_1}}
- **Main App Logo**: {{DATA:IMAGE:IMAGE_6}}
- **Screens**:
    - Main Code Editor: {{DATA:SCREEN:SCREEN_15}}
    - Search: {{DATA:SCREEN:SCREEN_8}}
    - Settings: {{DATA:SCREEN:SCREEN_10}}
    - Logs (Git Diff): {{DATA:SCREEN:SCREEN_4}}