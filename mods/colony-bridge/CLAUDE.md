# TC Colony Bridge — instruções específicas

As regras gerais (autor, idioma, arquitetura, segurança, desempenho, documentação, git) estão no
`CLAUDE.md` da raiz do workspace. Aqui, só o que é deste mod.

Addon que liga uma rede ME do **Applied Energistics 2** ao sistema de pedidos do **MineColonies**.
Depende do **TC Core** (`tccore`, mod separado): design system das telas, ghost slots, redstone e
estatísticas vêm de lá (`org.tinycore.core.*`).

Estado: Ponte (entrega/craft dos pedidos, craft por tag com preferências por ponte, `ICraftingRequester`),
Abastecedor (manter estoque / devolver excedente), Monitores (multibloco mostrando Ponte ou Abastecedor) e
Terminal do Armazém (ver/tirar/guardar itens dos racks, sem rede ME).

Roadmap: `docs/ROADMAP.md` (ler **só** quando a tarefa for sobre planejamento ou feature nova).
Responsabilidade de cada classe: `docs/ARQUITETURA.md`. Guia de modelos: `docs/GUIA-BLOCKBENCH.md`.

## Comandos (na raiz do workspace)

```bash
./gradlew :colony-bridge:build       # jar em build/libs/ da raiz (instale junto com o do core)
./gradlew :colony-bridge:test --rerun
./gradlew :colony-bridge:runClient
```
Dependências: a API do AE2 vem do Maven Central; MineColonies, Structurize, BlockUI, Domum Ornamentum,
AE2 completo, GuideMe e JEI vêm de `mods/colony-bridge/libs/` (jars copiados do ATM10; `-Plibs_dir=...` aponta
outra pasta). Versões em `mods/colony-bridge/gradle.properties` (AE2) e na raiz (NeoForge) casam com o ATM10.

## Mapa

```
src/main/java/org/tinycore/colonybridge/
├── ColonyBridgeMod.java        # entrada do mod: registros, config, capabilities
├── Config.java                 # config de servidor (ModConfigSpec)
├── registry/                   # ModBlocks, ModItems, ModBlockEntities, ModMenus, ModCreativeTabs
├── block/                      # bases compartilhadas + bridge/, supply/, monitor/, terminal/
├── logic/                      # BridgeStatus + bridge/, supply/, crafting/, warehouse/, terminal/
├── menu/  client/              # bridge/, supply/, terminal/ (client/ também render/, ui/, jei/)
├── network/  stats/  multiblock/  item/
└── integration/                # ÚNICO lugar que toca na API do MineColonies (ae2/ para regras do AE2)
```
Fluxo de um ciclo da Ponte (a cada `cycleTicks`): `AbstractBridgeBlockEntity.serverTick()` →
`BridgeLogic.runCycle()` → `ColonyAccess.openRequests()` → desconta armazém (`WarehouseSnapshot`) →
entrega (`RackDelivery`) ou `RequestCrafter` → `ColonyAccess.reassign()`.

## Regras próprias

- MineColonies só em `integration/`. AE2 além do nó da grid: `integration/ae2/` ou `logic/crafting/`.
- Checar permissão da colônia antes de ligar um bloco a ela e respeitar a segurança do AE2
  (`setOwningPlayer` já é chamado).
- Qualquer mudança em `RackDelivery.deliver()`/`WarehouseStock` precisa manter SIMULATE antes de MODULATE
  e devolver o que não couber.
- O requester de craft (`CraftLinks`) deve sempre aceitar tudo que o AE2 entrega (sobra → rede ME), senão
  o job trava na CPU.
- Usar `getCachedInventory()` do AE2 para leitura; limites por ciclo em `Config`
  (`maxRequestsPerCycle`, `maxCraftPerRequest`...).
