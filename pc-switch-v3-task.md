# PC Switch v3.0 — Redesign & Public Release Task Specification

**Status:** Task specification for next development session
**Created:** 2026-11-12
**Goal:** Transform pc-switch from a personal tool into a polished, public-ready Android app

---

## Current State (v2.9.1)

- Native Jetpack Compose app (~5600 LOC), Kotlin
- Multi-switch architecture with WebSocket state sync
- Self-update via GitHub Releases (SHA-256 validated)
- Entity search, deep links, QS tile, notifications
- Dark gothic aesthetic (warm paper-white on near-black)
- PC status via TCP checks + HA state entities
- All personal config is user-provided (no hardcoded secrets)

---

## 1. UI Modernization (High Priority)

### Design Language: Gothic Cozy

Replace the current dark theme with a refined "gothic cozy" aesthetic — moody but warm, editorial but functional.

**Color Tokens:**
```hemlock
// Backgrounds
BG_PRIMARY:    "#1A161E"  // near-black with purple undertone
BG_SURFACE:    "#231D2A"  // elevated surface
BG_ELEVATED:   "#2C2433"  // dialogs, cards

// Accents
ACCENT:        "#8B2252"  // deep crimson (primary interactive)
ACCENT_LIGHT:  "#A83468"  // hover/pressed
ACCENT_GLOW:   "#C94481"  // active state highlight

// Text
TEXT_PRIMARY:  "#EDE4D4"  // warm off-white
TEXT_SECONDARY:"#A89D8D"  // muted warm gray
TEXT_MUTED:    "#6B6055"  // disabled/hint

// States
SUCCESS:       "#4A7C59"
WARNING:       "#B08A2E"
ERROR:         "#A33A2A"

// Borders
BORDER:        "#3A3040"  // subtle separator
BORDER_FOCUS:  "#8B2252"  // accent focus ring
```

**Typography:**
- Display/Headings: **Cinzel** (serif, gothic) — app title, switch names
- Body/UI Text: **Source Serif Pro** — descriptions, status text
- Technical/Mono: **JetBrains Mono** — entity IDs, IP addresses, version numbers

**Layout Principles:**
- Toggle-focused: large PC cards dominate the screen (~140dp height)
- No cluttered boxes — clean spacing, generous padding
- Bottom navigation bar (Home | Switches | Settings)
- Subtle ornament textures as background accents (low-opacity)

### Tasks

- [ ] Define complete design token system (colors, typography, spacing, elevation)
- [ ] Implement new theme in `ui/Design.kt`
- [ ] Redesign MainScreen with large PC toggle cards (~140dp)
- [ ] Add bottom navigation bar (Home, Switches, Settings)
- [ ] Implement Cinzel/Source Serif/JetBrains Mono font loading
- [ ] Add subtle ornament background textures
- [ ] Redesign SettingsScreen with modern form layout
- [ ] Redesign SwitchesScreen with card-based list
- [ ] Eliminate duplicate UI elements (e.g., status shown twice)
- [ ] Ensure correct formatting throughout (consistent spacing, alignment)
- [ ] Update all icons to match new aesthetic
- [ ] Test dark mode contrast ratios (WCAG AA minimum)

---

## 2. Update System Improvements (High Priority)

Current implementation uses PackageInstaller API via FileProvider. Needs to be more robust and user-friendly.

### Tasks

- [ ] **Safer self-updates:** Validate APK integrity before install
  - SHA-256 checksum verification (already implemented, ensure robustness)
  - Signature verification against known good signature
  - Validate APK is well-formed before presenting install prompt
- [ ] **Download progress tracking with OkHttp**
  - Show real-time download progress (bytes/sec, ETA, percentage)
  - Support resume on download interruption
  - Handle network errors gracefully with retry
- [ ] **Settings preservation after updates**
  - Verify SharedPreferences in `getFilesDir()` survive app updates
  - Test: update from v2.9.1 → v3.0.0, confirm all settings persist
  - Document migration path for settings schema changes
- [ ] **Robust error handling**
  - Clear error messages for each failure mode
  - Download failed → retry button
  - Install failed → diagnose common causes (storage, permissions)
  - Corrupted download → automatic re-download
- [ ] **Validation**
  - HTTPS always for download (never HTTP)
  - Validate version.json structure before parsing
  - Validate APK filename matches expected pattern
- [ ] **User experience**
  - Show changelog before update
  - "Update later" option (dismiss notification)
  - Background update check (not on every app launch)
  - Notify only for significant updates (minor can be silent)

---

## 3. Generalization (Critical for Public Release)

Remove all hardcoded values and personal assumptions. The app must work out-of-the-box for any HA user.

### Server Detection

- [ ] **Dynamic server detection via mDNS/Bonjour**
  - Use `homeassistant.local` as default discovery target
  - Implement mDNS resolution (consider `dns_sd` or Android's built-in support)
  - Scan for HA instances on local network
- [ ] **Manual IP fallback**
  - Allow manual entry of HA server address
  - Support both IP and hostname
  - Validate connectivity before saving
- [ ] **No hardcoded values**
  - Remove `192.168.0.205` default (or make it clearly a placeholder)
  - Remove `100.106.82.86` Tailscale IP from defaults
  - All endpoints configurable by user

### Onboarding Flow

- [ ] **Proper onboarding experience**
  1. Welcome screen with app purpose
  2. Server detection (mDNS scan → show detected HA instances)
  3. Manual entry fallback if detection fails
  4. Authentication (token entry with validation)
  5. Device naming (user names their first switch)
  6. Permissions request (with explanations)
  7. Success screen with quick start guide
- [ ] **Token validation**
  - Validate HA token during onboarding
  - Clear error messages for invalid tokens
  - Guide user to create long-lived access token

### Documentation

- [ ] **README.md**
  - App overview and features
  - Installation instructions
  - Configuration guide
  - Troubleshooting section
  - Screenshots
- [ ] **Screenshots for Play Store**
  - Capture high-quality screenshots of all major screens
  - Follow Play Store screenshot guidelines (1080x1920 recommended)
  - Include diverse scenarios (onboarding, main screen, settings)
- [ ] **GPL-3.0 license**
  - Add `LICENSE` file with GPL-3.0 text
  - Update `build.gradle.kts` with license metadata
  - Add license notice to about screen

---

## 4. Toggle Customization (Medium Priority)

Enable users to configure multiple PCs with different settings.

### Tasks

- [ ] **Multiple PC configurations**
  - Each user can define multiple PCs with different IPs
  - Each PC has its own toggle card
  - Easy add/edit/delete workflow
- [ ] **Toggle size and position customization**
  - Allow users to resize toggle cards (small/medium/large)
  - Enable reordering of toggle cards
  - Persist layout preferences
- [ ] **IP/MAC validation**
  - Validate IP address format on entry
  - Validate MAC address format for Wake-on-LAN
  - Provide helpful error messages
- [ ] **Auto-formatting**
  - Auto-format IP addresses as user types
  - Auto-format MAC addresses (add colons)
  - Suggest corrections for common typos

---

## Priority Order

1. **Generalization** (server detection, onboarding, no hardcoded values) — required for public release
2. **UI Modernization** (gothic cozy design, layout improvements) — public-facing quality
3. **Update System Improvements** (robustness, UX) — critical for self-hosted updates
4. **Toggle Customization** (multiple PCs, layout options) — nice-to-have power features

---

## Design Tokens Reference

```hemlock
// pc-switch v3.0 design tokens
export const colors = {
    bg_primary:    "#1A161E",
    bg_surface:    "#231D2A",
    bg_elevated:   "#2C2433",
    accent:        "#8B2252",
    accent_light:  "#A83468",
    accent_glow:   "#C94481",
    text_primary:  "#EDE4D4",
    text_secondary:"#A89D8D",
    text_muted:    "#6B6055",
    success:       "#4A7C59",
    warning:       "#B08A2E",
    error:         "#A33A2A",
    border:        "#3A3040",
    border_focus:  "#8B2252"
};

export const typography = {
    display: { family: "Cinzel", size: 36, weight: 700 },
    heading: { family: "Cinzel", size: 24, weight: 600 },
    body:    { family: "Source Serif Pro", size: 16, weight: 400 },
    caption: { family: "Source Serif Pro", size: 12, weight: 400 },
    mono:    { family: "JetBrains Mono", size: 14, weight: 400 }
};

export const spacing = {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
    xxl: 48
};

export const radii = {
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    full: 999
};
```

---

## Testing Checklist

- [ ] App works with fresh install (no previous version)
- [ ] Update from v2.9.1 preserves all settings
- [ ] mDNS discovery finds HA on local network
- [ ] Manual IP entry works for non-local servers
- [ ] Token validation provides clear feedback
- [ ] All UI elements render correctly on various screen sizes
- [ ] Dark mode contrast ratios meet WCAG AA
- [ ] Self-update downloads and installs correctly
- [ ] Multiple PC configurations persist across app restarts
- [ ] Documentation is complete and accurate

---

## Success Criteria

- [ ] App is ready for Google Play Store submission
- [ ] No hardcoded personal values remain in codebase
- [ ] Onboarding flow guides new users successfully
- [ ] UI is modern, consistent, and visually appealing
- [ ] Self-update system is robust and user-friendly
- [ ] All tests pass
- [ ] Documentation is complete
