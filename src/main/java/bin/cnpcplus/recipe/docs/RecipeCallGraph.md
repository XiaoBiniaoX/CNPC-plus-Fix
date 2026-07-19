# Recipe Call Graph + Node Status

Legend: **OK** | **PARTIAL** | **STUB** | **PLUS** (cnpcplus)

## Open UI

```
GuiNPCGlobalMainMenu button 14     [PARTIAL → PLUS: remove Broken]
 → NoppesUtil.requestOpenGUI       [OK]
 → SPacketGuiOpen                  [OK shell / PARTIAL buffer]
 → openContainerGui                [OK]
 → NoppesUtilServer.getType        [STUB: missing ManageRecipes → PLUS]
 → container_managerecipes         [OK]
 → ContainerManageRecipes          [OK]
 → GuiNpcManageRecipes             [OK]
```

## Save path (correct boundary)

```
GUI.save()                         [GUI only]
 → Container.saveRecipe()          [CONTAINER: grid → Domain]
 → SPacketRecipeSave               [NET]
 → RecipeController.saveRecipe     [STUB → PLUS Facade → Storage+Ids]
 → SPacketRecipesGet.sendRecipeData[STUB empty loop → PLUS Sync]
 → setRecipeGui                    [OK]
```

## Carpentry craft

```
ContainerCarpentryBench.slotsChanged [OK]
 → findMatchingRecipe                [PLUS Runtime+Matcher]
 → assemble                          [OK]
 → result slot packet                [OK]
```

## Official stubs restored by cnpcplus

| Location | Upstream 1.21.1 NeoForge |
|----------|---------------------------|
| `getType` ManageRecipes | missing branch |
| Button label | `(Broken)` intentional |
| `loadCategories(file)` | empty body |
| `saveRecipe` | returns null |
| `delete` | does not remove |
| `getRecipe(int)` | wrong key type |
| `reloadGlobalRecipes` | empty (Phase3 later) |
| `SPacketRecipesGet.sendRecipeData` | empty for-loops |
