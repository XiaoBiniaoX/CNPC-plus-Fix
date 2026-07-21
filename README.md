# CNPCPlus NeoForge
<img width="1792" height="592" alt="Gemini_Generated_Image_e6ndu4e6ndu4e6nd" src="https://github.com/user-attachments/assets/3aef7b9b-7862-4603-aebd-75a46d524cb2" />

[English](#english) | [简体中文](#简体中文)

---

# English

## What is CNPCPlus NeoForge?

CNPCPlus NeoForge is a community-maintained extension for **CustomNPCs** on **Minecraft 1.21.1 NeoForge**.

Unlike the Forge 1.20.1 branch, this project is not only a collection of improvements and bug fixes—it also restores and completes several unfinished systems left in the original NeoForge port while introducing new quality-of-life features for both players and map creators.

> **Current Minecraft Version**
>
> - NeoForge **1.21.1**

---

## Philosophy

CNPCPlus follows a simple philosophy:

- Improve CustomNPCs without breaking existing worlds whenever possible.
- Preserve compatibility before adding new features.
- Complete unfinished systems instead of simply patching around them.
- Provide long-term community maintenance.

---

# Fixed Issues

The following issues present in the original NeoForge version have been resolved.

| Status | Description |
|:------:|-------------|
| ✅ | Fixed NPC position offset after toggling visibility. |
| ✅ | Fixed incorrect faction name colors after visibility synchronization. |
| ✅ | Fixed duplicated scoreboard packets causing "Network Protocol Error" disconnects. |
| ✅ | Fixed scoreboard listener crashes caused by NullPointerException. |
| ✅ | Improved scoreboard synchronization stability. |

---

# Current CNPCPlus Features

## Recipe Editor (Completely Reworked)

The original NeoForge implementation only displayed a recipe button. The interface itself was never implemented.

CNPCPlus completely rebuilds the entire visual recipe editing system.

Features include:

- 🚀 Visual recipe editor for 3×3 Workbench.
- 🚀 Visual recipe editor for 4×4 Carpenter Bench.
- 🚀 One-click recipe editing.
- 🚀 Independent recipe configuration.
- 🚀 Recipe browser sidebar.
- 🚀 Expandable recipe categories.
- 🚀 Detailed recipe preview.
- 🚀 One-click recipe autofill.
- 🚀 Green indicators showing available recipes.
- 🚀 Completely rewritten recipe save logic.
- 🚀 Recipes no longer shift when saved.
- 🚀 Fuzzy Matching support.
- 🚀 ID & Name Matching support.
- 🚀 Individual matching configuration per recipe.
- 🚀 Approximately **4× faster** than the original CustomNPCs Forge 1.20.1 implementation during recipe operations.

---

## Dialogue Customization

- 🚀 Added global configuration for NPC dialogue fonts.
- 🚀 Added global configuration for dialogue option fonts.
- 🚀 Supports hexadecimal color values.

---

## Additional Improvements

- Better compatibility.
- Community-driven bug fixes.
- Continuous long-term maintenance.
- Performance optimizations.

---

# Performance

The visual recipe system has been almost entirely rewritten.

Except for a small portion of reused interface assets, nearly the entire implementation was redesigned from scratch.

Compared with the original CustomNPCs recipe implementation (Forge 1.20.1), recipe-related operations are approximately **4× faster**.

---

# Building

```bash
git clone https://github.com/<YOUR_NAME>/CNPCPlus-NeoForge.git
cd CNPCPlus-NeoForge
./gradlew build
```

Compiled JARs can be found in:

```
build/libs/
```

---

# Contributing

Issues and Pull Requests are always welcome.

Whether you are fixing bugs, improving compatibility, or adding new functionality, every contribution helps the project grow.

---

# Credits

## Project Maintainer

- XiaoBiniaoX

## Contributors

- [@detahdomin](https://github.com/detahdomin)
  - Bug fixes and compatibility improvements for the NeoForge branch.
- [@postyizhan](https://github.com/postyizhan)（驿站忆行）
  - Community feature contributions.

## Special Thanks

- GoodBird
- The CustomNPCs Community

---

# License

See the LICENSE file in this repository.

---

# 简体中文

## 什么是 CNPCPlus NeoForge？

CNPCPlus NeoForge 是一个面向 **Minecraft 1.21.1 NeoForge** 的 **CustomNPCs** 社区维护扩展项目。

与 Forge 1.20.1 分支不同，本项目不仅持续修复 Bug、提升兼容性，还补全了官方 NeoForge 移植过程中遗留的大量未完成功能，并持续加入社区所需的新特性。

> **当前支持版本**
>
> - NeoForge **1.21.1**

---

## 项目理念

CNPCPlus 坚持以下理念：

- 尽可能在不破坏旧存档的前提下改进 CustomNPCs。
- 优先保证兼容性。
- 对官方未完成的系统进行真正的补全，而不是简单修补。
- 长期维护社区版本。

---

# 已修复的问题

以下问题均已在 CNPCPlus NeoForge 中修复。

| 状态 | 内容 |
|:---:|------|
| ✅ | 修复 NPC 可见性切换后位置偏移的问题。 |
| ✅ | 修复阵营名称颜色同步错误。 |
| ✅ | 修复重复发送计分板数据包导致的“网络协议错误”断开连接。 |
| ✅ | 修复计分板监听器导致的 NullPointerException 崩溃。 |
| ✅ | 提升计分板同步稳定性。 |

---

# 当前 CNPCPlus 功能

## 可视化配方系统（完全重构）

官方 NeoForge 版本虽然保留了配方按钮，但整个可视化配方系统实际上并未完成。

CNPCPlus 对整个系统进行了重新实现。

目前支持：

- 🚀 工作台（3×3）可视化编辑。
- 🚀 木工台（4×4）可视化编辑。
- 🚀 一键编辑配方。
- 🚀 配方独立配置。
- 🚀 配方侧边栏浏览器。
- 🚀 支持折叠分类。
- 🚀 点击查看完整配方。
- 🚀 一键自动填充配方。
- 🚀 绿色提示可合成配方。
- 🚀 全新配方保存逻辑。
- 🚀 保存配方时物品不会发生偏移。
- 🚀 支持配置模糊化。
- 🚀 支持仅名字检查（ID + 名称）。
- 🚀 每个配方均可独立配置匹配方式。
- 🚀 配方系统性能相比原版 CustomNPCs（Forge 1.20.1）提升约 **4 倍**。

---

## 对话字体配置

新增全局配置项：

- 🚀 NPC 对话字体。
- 🚀 NPC 对话选项字体。
- 🚀 支持十六进制颜色配置。

---

## 其它改进

- 更好的 Mod 兼容性。
- 多项社区 Bug 修复。
- 持续长期维护。
- 性能优化。

---

# 性能

可视化配方系统几乎进行了完全重写。

除少量界面资源沿用外，大部分逻辑均重新设计实现。

相比原版 CustomNPCs（Forge 1.20.1）的实现，配方相关操作性能提升约 **4 倍**。

---

# 编译

```bash
git clone https://github.com/<YOUR_NAME>/CNPCPlus-NeoForge.git
cd CNPCPlus-NeoForge
./gradlew build
```

编译完成后生成：

```
build/libs/
```

---

# 参与贡献

欢迎提交 Issue 与 Pull Request。

无论是修复 Bug、提升兼容性还是新增功能，都欢迎参与项目建设。

---

# 致谢

## 项目维护

- XiaoBiniaoX

## 代码贡献

- [@detahdomin](https://github.com/detahdomin)
  - NeoForge 分支 Bug 修复与兼容性改进。
- [@postyizhan](https://github.com/postyizhan)（驿站忆行）
  - 社区功能开发与代码贡献。

## 特别感谢

- GoodBird
- CustomNPCs 社区

---

# License

请参阅仓库中的 LICENSE 文件。
