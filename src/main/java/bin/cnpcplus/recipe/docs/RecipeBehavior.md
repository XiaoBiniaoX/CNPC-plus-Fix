# Recipe Behavior Baseline

**Principle: Behavior Restoration, not Source Restoration.**

## Open ManageRecipes UI

- **Input:** `EnumGuiType.ManageRecipes` + grid size in `BlockPos.x` (`3` global / `4` anvil)
- **Output:** `ContainerManageRecipes` + `GuiNpcManageRecipes` opened
- **Side effects:** none on disk
- **Layers / packets:** `SPacketGuiOpen` → `getType` → `container_managerecipes`
- **Invariant:** `getType(ManageRecipes)` never null; buffer int is grid size, not entity id

## List recipes

- **Input:** width `3` or `4`
- **Output:** scroll map `displayName → syncId`
- **Side effects:** none
- **Packets:** `SPacketRecipesGet` → scroll data
- **Invariant:** only recipes of that grid family; names unique in map keys

## Select recipe

- **Input:** `syncId`
- **Output:** container grid filled; GUI fields enabled
- **Side effects:** none on disk
- **Packets:** `SPacketRecipeGet` → `PacketGuiData`
- **Invariant:** unknown id → no container mutation

## Save recipe

- **Input:** recipe NBT (from container `saveRecipe` + `writeNBT`)
- **Output:** same recipe with stable identity
- **Side effects:** atomic write `recipes.dat`; index update
- **Packets:** `SPacketRecipeSave` → list refresh + setRecipeGui
- **Invariant:** Ids unique; crash-safe atomic write; GUI never writes disk

## Delete recipe

- **Input:** `syncId`
- **Output:** recipe removed from memory
- **Side effects:** atomic write; index drop
- **Packets:** `SPacketRecipeRemove` → list refresh
- **Invariant:** after delete, `lookup(syncId)` is null

## World reload

- **Input:** global `CustomNpcs.Dir` (+ world dir migrate once) + `HolderLookup.Provider`
- **Output:** anvil + global maps filled
- **Side effects:** read `recipes.dat` / `_old` from global; migrate from world if global empty
- **Invariant:** corrupt main file falls back to `_old` when possible; recipes shared across worlds

## findMatching (anvil)

- **Input:** `CraftingInput` (4×4 carpentry)
- **Output:** `MatchResult` (recipe or miss)
- **Side effects:** **none**
- **Invariant:** never mutates Domain / Ids / disk / network

## assemble

- **Input:** matched recipe + input + registries
- **Output:** result `ItemStack`
- **Side effects:** none on recipe storage
- **Invariant:** empty if no match / availability fails (availability checked by container)
