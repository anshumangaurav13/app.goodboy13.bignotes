# BigNotes

> Minimalist, high-performance infinite canvas note-taking app for Android with low-latency stylus inking and a native Rust geometry core.

---

### 📥 Download

[![Download APK](https://img.shields.io/badge/Download-v1.0_Release_APK-blue?style=for-the-badge&logo=android)](https://github.com/anshumangaurav13/app.goodboy13.bignotes/releases/tag/v1.0)

👉 **[Download the Latest BigNotes v1.0 Release APK](https://github.com/anshumangaurav13/app.goodboy13.bignotes/releases/download/v1.0/BigNotes-v1.0.apk)**

---

## ✨ Features

- **⚡ Ultra-Low Latency Stylus Inking:** Powered by Android Jetpack's front-buffered OpenGL ES 3.0 pipeline (`GLFrontBufferedRenderer`), bypassing SurfaceFlinger composition queues for ~8ms touch-to-glass response.
- **🦀 Native Rust Computational Geometry:** Critical vertex tessellation, centripetal Catmull-Rom spline interpolation, and equidistant arc-length dab generation execute in compiled native Rust (`bignotes_core`) with zero JVM garbage collection pauses.
- **🌲 Dynamic AABB BVH Spatial Index:** Scale-independent Bounding Volume Hierarchy with Surface Area Heuristic (SAH) and AVL balancing for instantaneous lasso selection and precision circle-clipping eraser queries across tens of thousands of strokes.
- **📐 Offline Handwriting Math & OCR:** Spatial 2D parser for fractions and superscripts, offline expression evaluation using Dijkstra's Shunting-Yard algorithm, and handwriting search indexing via on-device ML Kit Digital Ink Recognition.
- **🎨 Natural Inking Shaders:** Custom GPU shaders simulating organic graphite pencil texture, variable pressure fountain pens with ink bleeding, and semi-transparent highlighters.
- **📄 Vector PDF & Binary Document Storage:** Export multi-page notes to crisp vector PDFs with strict page-break overlap handling, backed by a compact 16-bit delta-quantized and deflated binary format (`.bignote`).

---

## 🏛️ Architecture

```
app.goodboy13.bignotes/
├── app/
│   ├── src/main/java/app/goodboy13/bignotes/
│   │   ├── ui/               # 100% Jetpack Compose Material 3 UI shell
│   │   ├── core/gl/          # OpenGL ES 3.0 chunked VBO renderer & shaders
│   │   ├── core/spatial/     # Dynamic AABB BVH tree (O(log N) queries)
│   │   ├── core/math/        # 2D spatial math parser & expression evaluator
│   │   ├── core/search/      # Stroke clustering & ML Kit ink recognition
│   │   └── core/storage/     # Note storage manager & .bignote binary codec
│   └── src/main/jniLibs/     # Precompiled Rust shared libraries (.so)
│       ├── arm64-v8a/
│       └── x86_64/
└── rust/bignotes_core/       # Pure Rust computational geometry kernel
    ├── src/spline.rs         # Catmull-Rom spline interpolation
    ├── src/dabs.rs           # Equidistant arc-length dab generator
    ├── src/tessellator.rs    # Disc-quad mesh tessellation & vertex packing
    └── src/lib.rs            # JNI export bridge
```

---

## 🛠️ Building from Source

### Prerequisites
- **JDK:** Version 21
- **Android SDK:** Compile SDK 35, Min SDK 26
- **Android NDK:** Version 28+ (or 27+)
- **Rust Toolchain:** Stable Rust with `aarch64-linux-android` target installed
  ```bash
  rustup target add aarch64-linux-android x86_64-linux-android
  cargo install cargo-ndk
  ```

### Build Commands
1. **Compile Rust Core:**
   ```bash
   ./gradlew buildRustCore
   ```
2. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
3. **Build Release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

Built APKs are automatically archived to `archives/apks/`.

---

## 📄 License

BigNotes is open source software.
