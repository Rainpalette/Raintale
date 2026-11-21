# Third-Party Licenses / 第三方许可

> Rain Tales / 雨中逸事 uses the following third-party components. Each is governed by **its own license**, not by this project's MIT `LICENSE`.
>
> 本项目使用下列第三方组件，各自遵循**其自身许可**，不适用本项目的 MIT `LICENSE`。

---

## Bundled in this repository / 已随仓库分发

**无。** 本仓库不包含任何第三方二进制或模型文件——所有第三方组件都由 Gradle 在构建时下载。

> 历史：2026-11-20 之前曾 vendor 过 sherpa-onnx AAR（~55MB，Apache-2.0）与 vits-icefall-zh-aishell3 语音模型（~32MB）用于端上神经语音。该功能已下架、文件已从仓库移除，因此相关合规项不再适用。

## Declared dependencies / 声明的依赖（构建时拉取，不在仓库内）

| Component | License | Notes |
|---|---|---|
| AndroidX、Jetpack Compose、CameraX、Lifecycle、Activity、DataStore、Room | Apache-2.0 | Google / AOSP 生态。 |
| **ML Kit** — Selfie Segmentation (`com.google.mlkit`) | **专有（Google，闭源）**；受 Google APIs 服务条款约束 | 免费、离线可用（模型与推理库随 APK 打包，不需要 Google Play 服务），但**不是开源软件**。本项目用它做人像分割。见下方说明。 |
| Coil | Apache-2.0 | 图片加载。 |
| Kotlin 标准库 / Coroutines | Apache-2.0 | JetBrains。 |
| `org.json` | 随 Android SDK | 平台内置，非外部依赖。 |

> 依赖的精确版本见 `gradle/libs.versions.toml` 与 `app/build.gradle.kts`。

### 关于 ML Kit 是「专有」这件事

**这不影响本项目开源，也不影响你使用、修改、分发本项目的代码。** 依赖一个专有库不会"传染"——本项目代码仍然是 MIT，ML Kit 只是构建时拉取的一个二进制依赖，和你在任何 app 里用 Google Play 服务是一回事。它免费、离线运行（模型与推理库随 APK 打包，不需要 Google Play 服务），使用受 Google APIs 服务条款约束。

它带来的实际影响只有两条：

1. **本 app 不是 100% 自由软件（FOSS）**，因此**无法进入 F-Droid 主仓库**——那个仓库的收录政策要求整个应用及其依赖都是自由软件、且能由他们的服务器从源码构建。这不是"ML Kit 不许用于开源项目"，而是"F-Droid 只收纯 FOSS"。分发走 GitHub Releases 即可；IzzyOnDroid 这类第三方仓库接受含专有依赖的应用（会标注 anti-feature 提示用户）。
2. **ML Kit 内部包含 Apache-2.0 的开源组件**（如 TensorFlow Lite 系的推理栈）。这类嵌入式开源组件的署名义务随 **APK** 一起走，所以本文件在发布 APK 时才真正变成必需品——这也是把 ML Kit 列在这里的理由之一。

> 在意纯 FOSS 的用户关心的是第 1 条，所以这件事写明比藏着好。想彻底摆脱这个依赖，就要把人像分割换成自行打包的开源模型（如 MediaPipe / 自训练的 ONNX），代价是体积与精度的重新评估——目前没有这个计划。

---

## Maintenance / 维护

- 新增第三方库 / 模型 / 美术资源时，在此登记其许可与来源。
- **凡是把二进制或模型文件放进仓库**（而非由 Gradle 下载），都要在「Bundled」一节登记——那才是真正触发再分发义务的情形。
- 日后若接入角色专属音色模型（云端或端上），务必核对其训练数据来源与授权后再登记。

> **为什么这份文件要留着**：仓库现在只分发源码，Apache-2.0 的随附许可义务严格说尚未触发；但**一旦在 GitHub Releases 挂 APK**，APK 里就嵌入了上表这些库的字节码＝再分发二进制，届时必须随附许可与署名。另外 ML Kit 是闭源专有依赖，这件事只在本文件写明。
