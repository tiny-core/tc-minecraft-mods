# Arquitetura — quem faz o quê

Mapa das responsabilidades de cada classe, agrupado por **bloco do mod**. Regra geral: cada camada
(`block`, `logic`, `menu`, `client`) tem um subpacote por bloco (`bridge/`, `supply/`, `monitor/`) e a
raiz da camada guarda só o que é **compartilhado**. Código compartilhado nunca importa nada de um
subpacote específico, então mexer na Ponte não quebra o Abastecedor (e vice-versa).

Caminhos relativos a `src/main/java/org/tinycore/colonybridge/`.

**Vem do TC Core** (`mods/core/`, pacote `org.tinycore.core`): design system das telas (`UiColors`, `ScreenStyle`,
`IconButton`, `SideToolbar`, `BarChart`, `Painter`, `UiFormat`, `RedstoneIcons`), ghost slots
(`AbstractGhostMenu`, `GhostContainer`, `GhostSlot`, `TabSlot`, `JoinedList`), `RedstoneMode` e as
estatísticas genéricas (`MetricRing`, `MetricSeries`). Ver `mods/core/README.md`.

---

## Compartilhado entre Ponte e Abastecedor

| Classe | Responsabilidade |
|---|---|
| `block/AbstractBridgeBlock` | Bloco no mundo: permissão para colocar, dono, estado visual (`STATUS`), ticker, vizinho mudou, clique direito abre a tela. |
| `block/AbstractBridgeBlockEntity` | Nó da grid do AE2 e seu ciclo de vida, regra do cabo, redstone, dono/permissão, chama `runCycle` a cada `cycleTicks`. |
| `block/BridgeVisualState` | Os 4 visuais do blockstate (offline / erro / ocioso / trabalhando). |
| `logic/BridgeStatus` | Estado mostrado ao jogador (sem colônia, sem permissão, trabalhando...). |
| `block/ColonySlots` | Regra "um de cada tipo por colônia" no mundo: confere o bloco registrado (sem carregar chunk) e descarta registro velho. |
| `logic/colony/ColonyBlockRegistry` | `SavedData`: posição do bloco de cada tipo em cada colônia (também servirá ao Tablet). |
| `logic/colony/ColonySlotRule`, `ColonyBlockType` | Regra pura (testada) de quem fica com a vaga; tipos com vaga única. |
| `logic/warehouse/RackDelivery` | Rede ME → racks do armazém, com SIMULATE antes de MODULATE (anti-duplicação). |
| `logic/warehouse/WarehouseStock` | Conta itens nos racks; racks → rede ME (devolve o que a rede recusar). |
| `logic/warehouse/WarehouseSnapshot` | Conteúdo dos racks lido uma vez por ciclo da ponte (+ entregas do ciclo). |
| `integration/ColonyAccess`, `OpenRequest` | **Único** ponto que fala com o MineColonies: colônia, permissões, pedidos em aberto, racks, reatribuir. |
| `integration/ae2/CableRules` | Só conecta por baixo e só com cabo comum. |

## Ponte ME da Colônia (`bridge/`) — atende pedidos da colônia

| Classe | Responsabilidade |
|---|---|
| `block/bridge/ColonyBridgeBlock` | Liga o bloco ao block entity e à tela da ponte (o resto vem da base). |
| `block/bridge/ColonyBridgeBlockEntity` | Dados da ponte (configurações, filtro, estatísticas) e ponte com a tela e os monitores. |
| `block/bridge/BridgeSettings`, `ItemFilter`, `FilterMode` | Configuração salva no NBT: crafting on/off, redstone, filtro permitir/bloquear. |
| `block/bridge/CraftSettings`, `PreferredItems` | Preferências de craft por tag da ponte (preferência, modo de mods, mods marcados, 18 itens preferidos), salvas no NBT. |
| `logic/bridge/BridgeLogic` | **Ciclo de pedidos:** para cada pedido decide *entregar, craftar ou esperar*, só com o que falta (pedido − armazém − rede). |
| `logic/bridge/StockSearch` | Acha na rede os itens que servem para o pedido (pode juntar vários de uma tag). |
| `logic/bridge/BridgeCycle` | Dados de um ciclo (grid, colônia, racks, estoque, o que já saiu neste ciclo). |
| `logic/bridge/RequestCrafter` | *Craftar o quê e quanto:* item exato ou escolhido para pedido por tag; reserva no ledger. |
| `logic/bridge/DeliveryLedger` | Registro salvo no mundo: pedido já entregue (cooldown) ou em craft, e por qual ponte. |
| `logic/bridge/CycleReport`, `RequestLine`, `RequestOutcome` | Resultado de cada pedido no último ciclo (vai para a tela e os monitores). |
| `logic/crafting/CraftingTracker` | Cálculo do plano no AE2 e envio do job com a ponte como dona; espera após falha, blacklist. |
| `logic/crafting/CraftLinks` | `ICraftingRequester` da ponte: vínculos dos crafts com os pedidos (salvos no NBT) e recebimento do resultado. |
| `logic/bridge/CraftDelivery` | Coloca o resultado do craft nos racks (sobra → rede ME), registra "craft concluído" e libera pedido cancelado. |
| `logic/crafting/CraftCandidates` | Escolhe o item a craftar para pedidos por tag (preferência, só vanilla, limite de candidatos). |
| `logic/crafting/CraftOrdering` | Regras de ordem dos candidatos (mods preferidos, preferência, desempate), genéricas para teste. |
| `logic/crafting/CraftCost` | Custo estimado de um item pelas receitas do AE2 (cache por ciclo). |
| `logic/crafting/CraftPreference` | `CHEAPEST` / `MOST_EXPENSIVE` / `LIST`. |
| `logic/crafting/CraftRules`, `ModFilterMode` | Regras prontas (ponte + config do servidor) que o `CraftCandidates` recebe; filtro/prioridade por mod. |
| `logic/crafting/CraftableMods` | Mods com item craftável na rede, para a aba "Mods". |
| `logic/bridge/RequestCounts` | Resumo do ciclo (abertos, atendidos, craftando) para a aba "Geral". |
| `menu/bridge/ColonyBridgeMenu`, `BridgeSnapshot`, `BridgeTab` | Container da tela (ghost slots do filtro e dos preferidos), a "foto" enviada ao cliente e as abas. |
| `client/bridge/ColonyBridgeScreen`, `ModListView`, `BridgeIcons` | Tela: barra lateral de ajustes, abas Geral, Filtro, Preferidos e Mods. Lista de pedidos e estatísticas ficam só no monitor. |
| `stats/BridgeStats`, `StatsSummary`, `TopItems` | Estatísticas da ponte (entregas, crafts, ranking de itens). A regra do ranking fica em `TopRanking` (genérico, testado). |

## Abastecedor da Colônia (`supply/`) — mantém o armazém abastecido

| Classe | Responsabilidade |
|---|---|
| `block/supply/ColonySupplyBlock` | Liga o bloco ao block entity e à tela do abastecedor. |
| `block/supply/ColonySupplyBlockEntity` | Dados do abastecedor (listas, redstone) e ponte com a tela. |
| `block/supply/StockList` | As duas listas: slots 0-17 "manter no armazém", 18-35 "excedente para o ME", com quantidade alvo; migra o formato antigo (9 + 9). |
| `logic/supply/SupplyLineStatus` | Situação de cada linha em palavras (Abastecido, Falta na rede ME, Retido...) e gravidade para a cor do monitor (regra pura, testada). |
| `logic/supply/SupplyLogic` | **Ciclo:** repõe o que falta da rede; devolve o excedente, exceto itens em pedido aberto (anti vaivém). Quantidades decididas pela `SupplyRule` (regra pura, testada). |
| `menu/supply/ColonySupplyMenu`, `SupplySnapshot` | Container da tela e a "foto" enviada ao cliente. |
| `client/supply/ColonySupplyScreen` | Tela com as duas listas e o botão de redstone. |
| `stats/SupplyStats`, `SupplySummary` | Estatísticas do Abastecedor (itens repostos / devolvidos ao ME). |

## Terminal do Armazém (`terminal/`) — ver, tirar e guardar itens do armazém

| Classe | Responsabilidade |
|---|---|
| `block/terminal/WarehouseTerminalBlock`, `WarehouseTerminalBlockEntity` | Bloco na base comum (rede ME, dono, frente); o block entity confere colônia, permissão e a Ponte da rede (`TerminalLink`) e cobra energia por item. |
| `integration/ae2/BridgeNetwork` | Conta/acha as Pontes de uma rede ME (`getMachines`): uma Ponte por rede e a Ponte do terminal. |
| `logic/terminal/TerminalLink` | Regra pura (testada): uma Ponte por rede; terminal só com Ponte ativa da mesma colônia. |
| `menu/terminal/WarehouseTerminalMenu` | Container: inventário do jogador (slots reais) + grade virtual do armazém; shift-clique guarda no armazém. |
| `menu/terminal/WarehouseSync` | Servidor: lê os racks a cada `terminalSyncTicks` e manda só as mudanças (`CountDiff`), em pacotes de até 256. |
| `menu/terminal/WarehouseView`, `WarehouseEntry` | Cliente: cópia local do armazém montada pelas mudanças; entrada = item + quantidade. |
| `menu/terminal/TerminalCrafting`, `TerminalResultSlot` | Bancada 3×3: resultado (receita vanilla), reposição pelo armazém depois de cada craft, devolver a grade, montar receita do JEI. |
| `menu/terminal/TerminalActions` | Servidor: executa o clique (tirar para cursor/inventário, guardar) a partir do que existe nos racks. |
| `logic/terminal/CountDiff`, `ItemListing`, `TerminalAction` | Regras puras (testadas): diferença entre contagens, busca/ordem, ações possíveis. |
| `logic/warehouse/WarehouseItems` | Somar racks por tipo, tirar e guardar com `ItemStack` (sem tipos do AE2). |
| `network/WarehouseContentsPayload`, `WarehouseActionPayload`, `TerminalRecipePayload`, `TerminalPackets` | Pacotes do terminal e validação no servidor (menu, distância, permissão). |
| `client/terminal/WarehouseTerminalScreen`, `WarehouseGrid` | Tela (busca, ordem, cliques, bancada) e a grade desenhada com rolagem. |
| `client/jei/TerminalRecipeTransfer` | "+" do JEI: confere se os ingredientes existem e manda a receita ao servidor. |

## Monitor da Colônia (`monitor/`) — mostra dados de uma Ponte

| Classe | Responsabilidade |
|---|---|
| `block/monitor/MonitorSource` | O que um bloco precisa para aparecer no monitor (Ponte e Abastecedor implementam). |
| `block/monitor/MonitorBlock`, `MonitorBlockEntity` | Bloco e posição dentro da tela; o mestre guarda a ligação com o bloco mostrado. |
| `block/monitor/MonitorData`, `MonitorContent` (`BridgeContent`, `SupplyContent`), `MonitorLine`, `StockLine` | Dados sincronizados 1×/s, só quando mudam; o conteúdo depende do tipo de bloco ligado. |
| `multiblock/MonitorFormation` | Junta monitores vizinhos num retângulo e elege o mestre. A conta do retângulo fica em `MonitorShape` (regra pura, testada). |
| `item/LinkCardItem` | Cartão de ligação monitor → Ponte ou Abastecedor (valida dimensão, distância, permissão). |
| `client/render/*` | Desenho no mundo: renderer, canvas, painel da Ponte (`MonitorPanels`) e do Abastecedor (`SupplyPanel`), lista paginada, animação. |

## Infraestrutura

| Classe | Responsabilidade |
|---|---|
| `ColonyBridgeMod` | Entrada do mod: registros, config, capabilities. |
| `Config` | Config do servidor (ciclo, limites, crafting, craft por tag, estatísticas, monitores). |
| `registry/*` | `DeferredRegister` de blocos, itens, block entities, menus e aba do criativo. |
| `network/*` | Pacotes cliente↔servidor; tudo que vem do cliente é validado no servidor (`ModNetwork`). |
| `client/ColonyBridgeClient` | Entrada só do cliente: liga o botão "Config" da lista de mods à tela de config do NeoForge. |
| `client/ClientSetup`, `ClientPayloadHandler` | Registro das telas/renderers e tratamento dos pacotes no cliente. |
| `client/ui/StatusColors` | Cor de cada estado da ponte e resultado de pedido (telas e monitores). O resto do design system está no TC Core. |
| `client/jei/*` | Arrastar itens do JEI para o filtro (JEI opcional). |
