# PC Switch Android — Status Summary (2026-11-12)

## Completed Work

### 1. v2.9.1 Release (E2E Bugfixes)
- **Status:** Released and published to GitHub
- **Release URL:** https://github.com/finnleyben-spec/pc-switch-android/releases/tag/v2.9.1
- **Changes:**
  - B4: Rapid ON→OFF toggle fix (finger-based gesture instead of animated knob value)
  - B3: Unknown state label handling
  - Edge-to-edge padding improvements
  - stateEntity in create flow
  - Delete confirmation dialog
  - Comprehensive logging
  - DataStore corruption recovery
  - Debug logs removed from EntityToggle

### 2. Entity Discovery UI Enhancements
- **Status:** Merged to main (commit f13d15b)
- **Branch:** coder/entity-discovery-enhancements
- **Changes:**
  - Domain filter (button, input_button, switch, light, cover, lock, fan, sensor, input_boolean, person)
  - Live state indicators in entity search results
  - New file: `app/src/main/java/dev/finnley/pcswitch/ui/EntityFilter.kt`
  - New test: `app/src/test/java/dev/finnley/pcswitch/ui/EntityFilterTest.kt` (23 unit tests)

### 3. Notification Templates
- **Status:** Merged to main (commit 4f84395)
- **Branch:** coder/notification-templates
- **Changes:**
  - Configurable notification templates with mode selection (all, boot_only, shutdown_only)
  - Settings fields: `notificationEnabled`, `notificationMode`, `notificationPcName`
  - `shouldNotify()` function in PcSwitchViewModel.kt
  - Settings UI for notification configuration
  - New test: `app/src/test/java/dev/finnley/pcswitch/state/NotificationTemplatesTest.kt` (6 unit tests)

## Validation Results

| Check | Result |
|-------|--------|
| Main branch clean | ✓ Working tree clean |
| All feature merges | ✓ Both branches merged (f13d15b, 4f84395) |
| Tests pass | ✓ 100 tests passed (`./gradlew testDebugUnitTest`) |
| v2.9.1 on GitHub | ✓ Released (https://github.com/finnleyben-spec/pc-switch-android/releases/tag/v2.9.1) |
| Notification templates in code | ✓ Settings.kt, PcSwitchViewModel.kt, SettingsScreen.kt |
| Entity filter in code | ✓ EntityFilter.kt with domain filter + state normalization |
| Live state indicators | ✓ normalizeStateForDisplay() in EntityFilter.kt |

## Git Status
- Local main is ahead of origin/main by 5 commits
- No uncommitted changes
- Ready to push: `git push origin main`

## Files Modified/Created
- `app/src/main/java/dev/finnley/pcswitch/ui/EntityFilter.kt` (new)
- `app/src/test/java/dev/finnley/pcswitch/ui/EntityFilterTest.kt` (new)
- `app/src/test/java/dev/finnley/pcswitch/state/NotificationTemplatesTest.kt` (new)
- `app/src/main/java/dev/finnley/pcswitch/state/Settings.kt` (notification fields)
- `app/src/main/java/dev/finnley/pcswitch/state/PcSwitchViewModel.kt` (shouldNotify)
- `app/src/main/java/dev/finnley/pcswitch/ui/SettingsScreen.kt` (notification UI)
- `app/src/main/java/dev/finnley/pcswitch/ui/MainScreen.kt` (state indicator)
