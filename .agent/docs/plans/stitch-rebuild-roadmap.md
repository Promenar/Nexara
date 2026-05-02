# Phase 3: Pure Stitch MD3 Rebuild Roadmap

> **CRITICAL WARNING:** This document is the SINGLE SOURCE OF TRUTH for the native UI rebuild. DO NOT rely on old React Native code or previous memory for UI styling. Follow the rules defined here strictly.

## 1. The Goal
Re-implement the entire Android UI using **Jetpack Compose Material 3** strictly adhering to the `global_theme_specs.md` downloaded from Stitch. The previous implementation had only 60% alignment and suffered from immersive mode overlap and lack of motion.

## 2. Core Directives (The "Never Again" Rules)

### 2.1 Complete MD3 Immersion & WindowInsets
**Rule:** NEVER use hardcoded padding (e.g., `padding(top = 48.dp)`) to avoid the status bar.
**Solution:**
- Your root composable MUST be a `Scaffold`.
- You MUST pass the `PaddingValues` from the `Scaffold` to your inner `LazyColumn` or `Column`.
- For screens that need to draw *behind* the status bar (e.g., Welcome Screen or Maps), use `WindowInsets` correctly: `Modifier.windowInsetsPadding(WindowInsets.systemBars)`.

### 2.2 Material Motion & Animations (Mandatory)
**Rule:** NO abrupt state changes. Everything must animate.
**Solution:**
- **Navigation:** Use Compose Navigation's built-in `AnimatedContentTransitionScope` (e.g., `slideIntoContainer`, `slideOutOfContainer`) for all screen transitions.
- **State Changes:** Use `AnimatedVisibility` for items appearing/disappearing (e.g., expanding a RAG folder).
- **Size Changes:** Use `Modifier.animateContentSize()` on containers that change height (e.g., when an AI is typing out a long thought process).
- **Interactions:** Ensure all clickable elements use the default MD3 Ripple effect. DO NOT disable the ripple unless explicitly demanded by a specific Edge-Case Glassmorphic component.

### 2.3 The "Glassmorphism" Constraint
**Rule:** Mica/Glass is an *accent*, not a hammer.
**Solution:**
- Read `.stitch/design_system/global_theme_specs.md` carefully.
- The base background of the app is **Zinc-950 (#131315)**. It is solid, not transparent.
- Only use the 20px Gaussian Blur + 0.5px Hairline border on specific elevated elements:
  - Floating Bottom Navigation Bars
  - Floating Chat Input fields
  - RAG Context Cards / Floating Dialogs

## 3. Implementation Phasing

### Phase 3.1: The Foundation (Do this BEFORE any screens)
1. **`Color.kt` & `Theme.kt`**: Map every single color from `global_theme_specs.md` into a custom Compose `ColorScheme` or extended properties.
2. **`Type.kt`**: Setup Inter (Body) and Manrope (Headings). Define exact line heights (e.g., body-md: 15px/25px).
3. **`Shape.kt`**: Define the standard 18px (Chat Bubbles) and 16px (Cards) radii.

### Phase 3.2: Framework & Navigation
1. Rebuild `NavGraph.kt` with full Enter/Exit Transition animations.
2. Rebuild `MainTabScaffold.kt`. Ensure the BottomBar floats above the content with proper `WindowInsets.navigationBars` padding and Mica blur.

### Phase 3.3: The Chat Experience (The Hero Screen)
1. Implement `ChatScreen.kt` using a proper `Scaffold`.
2. The TopBar must blur content scrolling beneath it.
3. Chat Bubbles must use the 15px/25px Inter typography rule. Assistant messages have NO container (transparent background), only structured typography.
4. The Input Bar must be a floating pill (24px radius) with an inner shadow and 0.5px border.

### Phase 3.4: Management & Settings
1. Rebuild Agent Hub (List/Sessions/Editor) with `AnimatedVisibility` for list item interactions.
2. Rebuild RAG screens and Settings screens using standard MD3 Lists and Dividers.

## 4. Verification Checkpoints
For every screen implemented, verify:
- [ ] Does it overlap with the notch/status bar on a real device? (If yes -> Fail)
- [ ] Does it instantly snap into place without animation? (If yes -> Fail)
- [ ] Does the assistant chat bubble have a bounding box? (If yes -> Fail, should be transparent per Stitch spec).
