<p align="center">
  <img src="docs/logo.png" alt="BigNotes Logo" width="128" height="128" />
</p>

# BigNotes

A fast, smooth, infinite canvas note-taking app engineered for Android tablets and active styluses.

---

### Download

[![Download APK](https://img.shields.io/badge/Download-v1.3_Release_APK-blue?style=for-the-badge&logo=android)](https://github.com/anshumangaurav13/app.goodboy13.bignotes/releases/latest)

**[Download BigNotes v1.3 (APK)](https://github.com/anshumangaurav13/app.goodboy13.bignotes/releases/download/v1.3/app-release.apk)**

---

## Why BigNotes?

BigNotes gives you the freedom of a physical notebook without boundaries. Whether taking lecture notes, sketching ideas, or solving math problems, everything feels immediate, organic, and fast.

### Key Highlights

- **Physical Wet Fountain Pen Inking:** Real-time subtractive dye absorption (`GL_DST_COLOR`) where strokes overlap and cross, paired with capillary fiber bleed, meniscus surface tension (coffee-ring effect), and chromatographic dye separation.
- **Natural Graphite Pencil:** Procedural paper tooth grain noise dual-sampling and authentic lead abrasion that feels distinctly dry and tactile.
- **Ultra-Low Latency Inking:** Direct front-buffered OpenGL ES 3.0 pipeline backed by an ARM64 NEON SIMD vectorized Rust core for instant, sub-5ms stylus response.
- **Infinite Canvas:** Never run out of space. Smoothly pan, rotate, and zoom with zero stutter across an unbounded workspace.
- **Handwritten Math Solver:** Write out complex math equations naturally, and BigNotes calculates and formats answers inline directly on the canvas.
- **Search Your Handwriting:** Fast, 100% on-device handwriting recognition to search handwritten words across all your notebooks.
- **Vector Lasso Editing:** Select, translate, rescale, duplicate, or recolor strokes dynamically.
- **Smart Organization:** Clean hierarchical folders with automatic top-note focus upon navigation.
- **High-Resolution Vector/Bitmap PDF Export:** Multi-page paginated exports with 300 DPI tiled GPU rasterization ready to share or print.
- **100% Offline & Private:** Zero telemetry, no account required, no cloud dependencies. Your data stays entirely on your local storage.

---

## Requirements

- Android 8.0 (Oreo / API 26) or newer (fully tested on Android 15 with 16 KB page alignment)
- Recommended: Tablet with active stylus / pen support (Lenovo Precision Pen, Samsung S Pen, active USI, or passive capacitive)
