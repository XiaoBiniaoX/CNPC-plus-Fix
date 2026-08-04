# CNPCPlus
<img width="1792" height="592" alt="Gemini_Generated_Image_e6ndu4e6ndu4e6nd" src="https://github.com/user-attachments/assets/c7f9b41e-a743-42c0-b0c6-6cfb2d993d5b" />

[English](#english) | [简体中文](#简体中文)

---

# English

## What is CNPCPlus?

CNPCPlus is a community-maintained extension for **CustomNPCs** that focuses on stability, compatibility, quality-of-life improvements, and long-term maintenance.

Unlike a traditional fork, CNPCPlus serves as an experimental and community-driven branch. Mature and well-tested features may eventually be merged into the upstream CustomNPCs project, while compatibility-sensitive features continue to be maintained here.

> **Current Minecraft Version**
>
> - Forge **1.20.1**

---

## Philosophy

- Improve CustomNPCs without breaking existing worlds whenever possible.
- Prioritize compatibility over unnecessary redesigns.
- Long-term community maintenance.
- Contribute mature features back to the upstream project whenever appropriate.

---

# Features Merged into Upstream

The following features were originally implemented in CNPCPlus and have already been merged into the latest upstream CustomNPCs project.

| Status | Feature |
|:------:|---------|
| ✅ | NPC maximum health can reach the integer limit without AttributeFix. |
| ✅ | Added multiple NPC item drop modes (Nearby / Scatter / Drop at Death Position). |
| ✅ | Added a Save button for Global Recipes. |
| ✅ | NPC names now support '&' as an alternative color code character. |
| ✅ | Removed the 32-block limitation for NPC aggro distance. |
| ✅ | Merchant fuzzy matching and name-only matching options. |
| ✅ | Expanded attribute limits for melee, ranged and projectile combat. |

---

# Current CNPCPlus Features

These features are currently exclusive to CNPCPlus.

| Status | Feature |
|:------:|---------|
| 🚀 | Enhanced Parts system (0%–1000% scaling, supports held items). |
| 🚀 | Enhanced Puppet system supporting every equipment slot independently. |
| 🚀 | Completely rewritten 4×4 recipe compression logic. |
| 🚀 | Recipe browser with auto-fill for Workbench and Carpenter Bench. |
| 🚀 | Shift-click armor equipping for NPCs. |
| 🚀 | Non-armor items can be equipped into helmet slot with proper rendering. |
| 🚀 | Shift-click support for Container NPCs. |
| 🚀 | Recipe names now fully support Chinese characters. |
| 🚀 | Various compatibility improvements and quality-of-life enhancements. |

---

# Building

```bash
git clone https://github.com/<YOUR_NAME>/CNPCPlus.git
cd CNPCPlus
./gradlew build
```

Compiled JARs can be found in:

```
build/libs/
```

---

# Contributing

Issues and Pull Requests are welcome.

If you discover bugs or have ideas for improving CustomNPCs, feel free to open an Issue.

---

# Credits

## Project Maintainer

- XiaoBiniaoX

## Contributors

- @驿站忆行

## Special Thanks

- GoodBird
- The CustomNPCs Community

---

# License

See the LICENSE file in this repository.

---

# 简体中文

## 什么是 CNPCPlus？

CNPCPlus 是一个由社区长期维护的 **CustomNPCs** 扩展项目，致力于提升稳定性、兼容性、功能性以及使用体验。

与传统 Fork 不同，CNPCPlus 更像是一个社区实验分支。成熟、稳定的功能会尽可能提交到上游 CustomNPCs，而暂时不适合合并、需要长期兼容性验证的功能则继续由 CNPCPlus 维护。

> **当前支持版本**
>
> - Forge **1.20.1**

---

## 项目理念

- 尽可能在不破坏旧存档的前提下改进 CustomNPCs。
- 优先保证兼容性，而不是推翻重做。
- 长期维护社区所需功能。
- 成熟功能尽可能回馈上游项目。

---

# 已合并至上游项目

以下功能最初由 CNPCPlus 开发，目前已经被最新版 CustomNPCs 合并。

| 状态 | 功能 |
|:---:|------|
| ✅ | NPC 最大生命值无需 AttributeFix 即可达到 int 上限。 |
| ✅ | 新增多种 NPC 掉落模式（附近掉落 / 四散掉落 / 原地掉落）。 |
| ✅ | 全局配方新增保存按钮。 |
| ✅ | NPC 名称支持使用 '&' 代替颜色符号。 |
| ✅ | NPC 仇恨距离突破 32 格限制。 |
| ✅ | 商人新增模糊匹配和仅名称匹配功能。 |
| ✅ | 扩展近战、远程、投射物属性范围。 |

---

# 当前 CNPCPlus 独有功能

以下功能目前仍由 CNPCPlus 独立维护。

| 状态 | 功能 |
|:---:|------|
| 🚀 | 全新的部件系统（0%~1000% 缩放，并支持主副手物品）。 |
| 🚀 | 全新的木偶系统，可独立控制所有装备栏。 |
| 🚀 | 重写 4×4 合成表压缩逻辑。 |
| 🚀 | 工作台 / 木工台自动放入配方浏览器。 |
| 🚀 | Shift + 左键快速给 NPC 穿戴装备。 |
| 🚀 | 允许头盔槽佩戴非盔甲物品并正常渲染。 |
| 🚀 | 储存者支持 Shift + 左键操作。 |
| 🚀 | 配方名称完整支持中文。 |
| 🚀 | 其它兼容性优化及大量体验改进。 |

---

# 参与贡献

欢迎提交 Issue 与 Pull Request。

如果发现 Bug 或有新的想法，欢迎参与项目建设。

