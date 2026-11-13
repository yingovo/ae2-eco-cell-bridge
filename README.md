# AE2 ECO Cell Bridge

> 作者 / Author: **YingOvO**

[简体中文](#简体中文) | [English](#english)

---

## 简体中文

让「按 **AE2 标准 ICellHandler** 注册」的存储元件可以插进 **Neo ECO AE Extension** 的存储矩阵驱动器（`eco_drive`）。

### 背景

- Neo ECO AE Extension 的驱动器插槽校验走它自己的注册表（`ECOStorageCells.isCellHandled`），只认它自己注册的元件；
- 而 AE Tetra 这类模组的存储元件注册在 AE2 的标准体系（`appeng.api.storage.StorageCells`）里；
- 结果就是：普通 AE2 元件、AE Tetra 的模块化磁盘都插不进 ECO 驱动器。

本模组把 AE2 的 cell handler 包一层，注册成 ECO 认识的 `IECOCellHandler`，于是两边都能认。

### 功能

- 任意 **AE2 标准存储元件** 都能插进 ECO 驱动器；
- 容量（总字节 / 每类型开销 / 类型数）优先从元件物品本身读取，ECO 界面显示真实数值；
- **按 key 类型归类**：物品盘归物品类、流体盘归流体质，尽量沿用 ECO 自己的元件分类与等级；
- 找不到对应类型时回退到 ECO 标准物品类型（不会出现自定义的怪异条目）。

### 依赖

| 模组 | 必需 |
|---|---|
| Minecraft 1.20.1 + Forge 47.x | ✅ |
| Applied Energistics 2 | ✅ |
| Neo ECO AE Extension (neoecoae) | ✅ |

### 安装

放进 `mods/`（客户端与服务端都需要），无需配置。

### 已知限制

Neo ECO AE Extension 自身只有「物品 / 流体」两类元件，因此魔力 / FE / 空气 / 全类型盘在 ECO 主机里会归类为物品类——**功能不受影响**（存取、容量都正常），只是分类显示如此。

### 许可

MIT

---

## English

Allows storage cells registered through **AE2's standard `ICellHandler`** to be inserted into **Neo ECO AE Extension**'s storage matrix drive (`eco_drive`).

### How it works

Neo ECO AE Extension validates drive slots against its own registry (`ECOStorageCells.isCellHandled`), while mods like AE Tetra register their cells in AE2's `StorageCells`. This mod wraps AE2's cell handler into an ECO-compatible `IECOCellHandler`, so both systems accept the cell.

### Features

- Any AE2 standard storage cell works inside an ECO drive
- Capacity is read from the cell item itself (real numbers in the ECO GUI)
- Cells are classified by AE key type (items / fluids / others when available)
- Falls back to ECO's standard item cell type instead of introducing a custom category

### Requirements

| Mod | Required |
|---|---|
| Minecraft 1.20.1 + Forge 47.x | ✅ |
| Applied Energistics 2 | ✅ |
| Neo ECO AE Extension | ✅ |

### Known limitation

Neo ECO AE Extension only ships item and fluid cell types, so mana / FE / air / universal cells are shown as item-type cells in the ECO host. Storage still works correctly.

### License

MIT
