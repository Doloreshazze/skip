# Code audit (2026-04-27)

## Scope
- Reviewed Android app source and localization resources.
- Focus: optimization opportunities, hardcoded elements, localization coverage for popular languages.

## Findings

### 1) Optimization opportunities
1. **`onAccessibilityEvent` scans the whole tree on almost every event** (`clickFirstMatchingNode`) and can be called very frequently. Consider filtering event types/packages before full traversal and caching last unsuccessfully-searched window signature for a short cooldown. 
2. **Repeated DP->PX conversions** (`topOverlayOffsetPx`, `closeDropBottomMarginPx`, `overlayCornerRadiusPx`) run each time related methods are called. This is minor, but can be cached once after `onServiceConnected`.
3. **Overlay text update and visibility update are called from many paths** (including preference listener and touch handling). There is a slight risk of redundant UI work under event storms; debouncing overlay text updates may help.

### 2) Hardcoded elements
1. **SharedPreferences keys duplicated as string literals** in service (`"auto_click_prefs"`, `"sound_enabled"`, `"target_text"`) even though a dedicated prefs helper exists. This increases risk of desynchronization.
2. **Hardcoded class and package names** are used intentionally for compatibility heuristics:
   - `android.widget.EditText`
   - settings packages and launcher package allow/block lists
   These are functional hardcoded values; keep them centralized and documented.
3. **Most user-facing UI text is in string resources** (good). Remaining hardcoded visual constants (colors/paddings) in service are technical UI constants rather than user-visible translatable text.

### 3) Localization coverage
1. **Default locale (`values`) has 77 string keys.**
2. **Many locales (`ar`, `bn`, `es`, `es-rES`, `fr`, `hi`, `ja`, `pt`, `zh`, `zh-rCN`) include only 37 keys** — around half of UI remains untranslated and falls back to default language.
3. **`values-ru` has 72/77 keys**, missing 5 icon-symbol keys.

## Conclusion
- The project already uses Android localization folders for many popular languages.
- Coverage is incomplete for most locales and should be expanded to avoid mixed-language UI.
- Main optimization target is accessibility-event processing frequency and traversal strategy in the service.
