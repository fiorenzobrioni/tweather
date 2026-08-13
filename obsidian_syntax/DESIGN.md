---
name: Obsidian Syntax
colors:
  surface: '#10141a'
  surface-dim: '#10141a'
  surface-bright: '#353940'
  surface-container-lowest: '#0a0e14'
  surface-container-low: '#181c22'
  surface-container: '#1c2026'
  surface-container-high: '#262a31'
  surface-container-highest: '#31353c'
  on-surface: '#dfe2eb'
  on-surface-variant: '#c0c7d1'
  inverse-surface: '#dfe2eb'
  inverse-on-surface: '#2d3137'
  outline: '#8a919b'
  outline-variant: '#404750'
  surface-tint: '#96ccff'
  primary: '#b5d9ff'
  on-primary: '#003353'
  primary-container: '#79c0ff'
  on-primary-container: '#004e7b'
  inverse-primary: '#00639a'
  secondary: '#74dd7e'
  on-secondary: '#003910'
  secondary-container: '#007f2d'
  on-secondary-container: '#c4ffc2'
  tertiary: '#e6cbff'
  on-tertiary: '#421b6a'
  tertiary-container: '#d2a8ff'
  on-tertiary-container: '#5d3885'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#cee5ff'
  primary-fixed-dim: '#96ccff'
  on-primary-fixed: '#001d32'
  on-primary-fixed-variant: '#004a76'
  secondary-fixed: '#90fa97'
  secondary-fixed-dim: '#74dd7e'
  on-secondary-fixed: '#002106'
  on-secondary-fixed-variant: '#00531b'
  tertiary-fixed: '#efdbff'
  tertiary-fixed-dim: '#dbb8ff'
  on-tertiary-fixed: '#2b0052'
  on-tertiary-fixed-variant: '#593482'
  background: '#10141a'
  on-background: '#dfe2eb'
  surface-variant: '#31353c'
typography:
  headline-lg:
    fontFamily: JetBrains Mono
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: JetBrains Mono
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  code-block:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 22px
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  status-bar:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 14px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin: 16px
  indent: 20px
---

## Brand & Style
This design system is a specialized intersection of high-utility developer tools and hyper-local environmental data. The brand personality is technical, precise, and utilitarian, mimicking the focused environment of a high-end code editor. 

The aesthetic follows a **Modern-Technical** approach: 
- **High-Contrast:** Absolute clarity is prioritized, utilizing a deep dark canvas to make vibrant data points pop.
- **Minimalist Structuralism:** Depth is communicated through 1px strokes rather than shadows, creating a flat but layered architectural feel.
- **Functional Aesthetics:** Every visual element serves a data-driven purpose, using syntax highlighting logic to categorize weather phenomena (e.g., temperature as numbers, conditions as strings).

## Colors
The palette is rooted in a deep charcoal/black background (`#0d1117`) to maximize contrast and reduce eye strain in low-light environments. 

- **Primary (Keys):** Soft blue is used for structural labels and primary navigation.
- **Secondary (Strings):** Emerald green represents active weather states or "success" metrics.
- **Tertiary (Keywords):** Pink/purple is reserved for system-level alerts and global constants (like "Current Location").
- **Numbers:** Orange is used exclusively for numeric weather data (degrees, humidity percentages, wind speed).
- **Borders:** A consistent `#30363d` is used for all structural separation, ensuring a clean, wireframe-like appearance.

## Typography
The system exclusively employs **JetBrains Mono** to maintain a rigorous, technical atmosphere. This monospaced constraint ensures that vertical alignment remains consistent, mimicking a code editor's grid.

- **Headlines:** Use bold weights with tight letter spacing for impact.
- **Body:** Standardized at 14px for readability on mobile.
- **Labels:** Uppercase styles are encouraged for terminal-style headers and metadata.
- **Numeric Data:** Always use tabular figures (native to monospaced fonts) to ensure temperatures and wind speeds align perfectly in lists.

## Layout & Spacing
The layout operates on a **4px baseline grid** to ensure mathematical precision. 

- **Structure:** Use a single-column fluid layout for mobile, with 16px side margins. 
- **Tree-View Logic:** Content nesting uses a fixed 20px indentation per level, marked by a 1px vertical guide wire in `#30363d`.
- **Code Blocks:** Sections are contained within boxes with 12px internal padding.
- **Terminal Status Bar:** A fixed-height (28px) bar at the bottom or top of containers for secondary metadata (e.g., "Last Updated: 12:01:04").

## Elevation & Depth
In alignment with the code editor aesthetic, this design system avoids traditional drop shadows and blurs.

- **Layering:** Depth is achieved through "Tonal Stacking." The base layer is `#0d1117`. Elements on top of this (like cards or code blocks) can use a slightly lighter fill or, preferably, are simply defined by their `#30363d` 1px border.
- **Interaction:** Active or focused states are indicated by changing the border color to the Primary Blue (`#79c0ff`) or adding a solid 1px offset "focus ring."
- **Floating Elements:** The only exception to the "no shadow" rule is the Floating Action Button (FAB), which utilizes a localized "Glow" effect (a diffused outer glow using the primary or secondary color) to denote its high-level priority.

## Shapes
The shape language is primarily **Geometric and Sharp**. 

- **Containers:** Code blocks, terminal bars, and list items use a strict 4px (`rounded-sm`) corner radius to maintain a professional, rigid feel.
- **Interactive Elements:** Buttons follow the same 4px rule to feel integrated into the grid.
- **FAB:** The Floating Action Button is the only fully circular element, creating a deliberate visual break from the otherwise rectangular environment.

## Components

### Code Block Containers
Weather data should be wrapped in a container with a 1px border (`#30363d`). The header of the block should mimic an editor tab, featuring a filename (e.g., `daily_forecast.json`) and a "collapse" icon.

### Tree-View List Items
For hourly forecasts or detailed metrics, use a tree-view structure.
- **Iconography:** Use simple `+` and `-` or `▸` and `▾` symbols.
- **Guide Lines:** A 1px vertical line should connect nested children to their parent.

### Terminal Status Bar
Used for system messages. Background should be a slightly lighter charcoal or a solid primary color with black text. Content is always left-aligned with "Git-style" branch icons for location switching.

### Floating Action Button (FAB)
- **Shape:** Circular.
- **Visuals:** Solid primary color background with a matching 10px-20px spread glow effect (`box-shadow: 0 0 15px #79c0ff88`).
- **Icon:** Simple 2pt stroke icons.

### Input Fields
Styled as single-line prompts (e.g., `> Search Location _`). Use a blinking underscore character for the cursor effect to emphasize the terminal theme.

### Checkboxes & Radios
Represented as code-style brackets: `[x]` for checked, `[ ]` for unchecked. These are interactive but rendered using the typography style rather than native OS controls.