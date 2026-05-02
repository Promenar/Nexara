---
name: Premium AI Interface
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1c1b1d'
  surface-container: '#201f22'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e5e1e4'
  on-surface-variant: '#c7c4d7'
  inverse-surface: '#e5e1e4'
  inverse-on-surface: '#313032'
  outline: '#908fa0'
  outline-variant: '#464554'
  surface-tint: '#c0c1ff'
  primary: '#c0c1ff'
  on-primary: '#1000a9'
  primary-container: '#8083ff'
  on-primary-container: '#0d0096'
  inverse-primary: '#494bd6'
  secondary: '#c8c5ca'
  on-secondary: '#303033'
  secondary-container: '#47464a'
  on-secondary-container: '#b6b4b8'
  tertiary: '#ffb783'
  on-tertiary: '#4f2500'
  tertiary-container: '#d97721'
  on-tertiary-container: '#452000'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#e4e1e6'
  secondary-fixed-dim: '#c8c5ca'
  on-secondary-fixed: '#1b1b1e'
  on-secondary-fixed-variant: '#47464a'
  tertiary-fixed: '#ffdcc5'
  tertiary-fixed-dim: '#ffb783'
  on-tertiary-fixed: '#301400'
  on-tertiary-fixed-variant: '#703700'
  background: '#131315'
  on-background: '#e5e1e4'
  surface-variant: '#353437'
typography:
  h1:
    fontFamily: manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  h2:
    fontFamily: manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 25px
  label-md:
    fontFamily: inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.02em
  code:
    fontFamily: spaceGrotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  safe-margin: 20px
---

## Brand & Style

The design system is engineered to evoke a sense of high-functioning intelligence and professional clarity. It targets power users and professionals who require a sophisticated, distraction-free environment for AI interaction. 

The aesthetic is rooted in **Glassmorphism**, leveraging depth through transparency and diffusion. This is not purely decorative; it serves to create a layered hierarchy where the AI’s generative content feels like it is surfacing from a deep, intelligent substrate. High-fidelity details—such as 0.5px "hairline" borders and 20px+ Gaussian blurs—communicate precision and premium craftsmanship. The interface feels responsive, light, and airy, emphasizing the speed of thought and the fluidity of conversation.

## Colors

The palette is anchored by **Indigo-500 (#6366f1)**, which serves as the primary engine for action and brand identity. This color should be used sparingly for high-intent actions, progress indicators, and active states.

- **Backgrounds**: In dark mode, use Zinc-950 for the base canvas and Zinc-900 for persistent UI elements like headers and navigation bars. In light mode, utilize pure White (#ffffff) and Zinc-100.
- **Glass Surfaces**: Use semi-transparent variants of the background colors (e.g., `rgba(24, 24, 27, 0.7)`) to allow background content to bleed through under the 20px blur.
- **Semantics**: Success, Error, Warning, and Info colors are used at full saturation for status indicators, ensuring high glanceability against the neutral zinc backgrounds.

## Typography

This design system prioritizes legibility during long-form reading. **Inter** is utilized for the body and UI labels due to its exceptional x-height and technical clarity on mobile screens. **Manrope** provides a slightly more geometric and "premium" touch for headings.

- **The Gold Standard**: The base chat bubble size is **15px with a 25px line height**. This generous leading (1.6x+) prevents visual fatigue during complex AI explanations.
- **Monospaced Content**: Code blocks and technical data should use **Space Grotesk** to maintain a futuristic, geometric edge that aligns with the AI's "thought process."
- **Hierarchy**: Use weight (500-700) rather than scale to differentiate hierarchy, keeping font sizes tight to maintain a professional density.

## Layout & Spacing

The layout follows a **fluid grid** model optimized for Android handheld devices. 

- **Margins**: A horizontal safe margin of 20px ensures that content does not crowd the edges of the screen, providing room for the glassmorphic blurs to be visible at the periphery.
- **Rhythm**: An 8px linear scale is the primary driver for spacing. Use 16px (md) for standard padding between chat bubbles and 24px (lg) to separate distinct logic blocks (e.g., between the chat list and the input area).
- **Verticality**: Chat messages are stacked vertically with an 8px gap between messages from the same sender and a 16px gap between different senders.

## Elevation & Depth

Depth is established through **optical layers** rather than traditional drop shadows.

1.  **Level 0 (Canvas)**: The base background (Zinc-950).
2.  **Level 1 (Surface)**: Elements like user bubbles or navigation bars. These use a slightly lighter background (Zinc-900) or a glassmorphic blur.
3.  **Level 2 (Cards/Modals)**: Glassmorphic layers with a 20px+ backdrop blur and a `0.5px` border (white/white-20% in dark mode).
4.  **Inner Shadows**: Use very subtle, 2px-blur inner shadows (inset) on cards to give them a "carved" or "hollow" glass feel, enhancing the tactile premium quality.

## Shapes

The shape language is sophisticated and modern. A **rounded (0.5rem - 1.5rem)** approach is used to soften the technical nature of the AI.

- **Chat Bubbles**: Use an 18px radius. User bubbles are right-aligned with a slightly sharper corner (4px) on the bottom right to indicate the tail.
- **Cards**: A consistent 16px radius is applied to all RAG and Tool cards.
- **Inputs**: The primary chat input uses a higher 24px (pill-style) radius to invite interaction.
- **Borders**: All borders must be **0.5px** to maintain the high-fidelity, delicate feel of the glass components.

## Components

### Chat Bubbles
- **User**: Right-aligned. Background: Zinc-800/900. Border: 0.5px Zinc-700. Text: White.
- **Assistant**: Left-aligned. Background: Transparent. Text: Zinc-100. No container, focusing on structured typography.

### Cards (RAG, Tools, Task)
- **Styling**: Background: `rgba(255, 255, 255, 0.03)` with 20px blur. 0.5px border (white at 10% opacity).
- **Interactive States**: On tap, the inner shadow deepens, and the border opacity increases to 30%.

### Buttons
- **Primary**: Indigo-500 background. White text. No glass effect for maximum contrast.
- **Secondary**: Glassmorphic background with a subtle border. Indigo-400 text.

### Input Fields
- Floating glassmorphic input area. Includes a 0.5px border and a subtle inner shadow. Placeholder text in Zinc-500.

### Chips & Tags
- Used for suggested prompts. Pill-shaped, semi-transparent backgrounds with Indigo-400 text and 0.5px Indigo borders.