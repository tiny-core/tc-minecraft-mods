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
| `logic/target/TargetSpec`, `TargetKind` | Alvo de uma linha lido do texto (`id`, `#tag`, `@mod`); só sintaxe (regra pura, testada). |
| `logic/target/TargetListKind` | Regras de cada lista (Manter / Excedente / Filtro): tipos aceitos, quantidade, "tudo" (testada). |
| `logic/target/TargetLine`, `TargetList`, `ListEdits` | Linha (alvo + item modelo + quantidade) e lista com NBT, limites, versão (para sincronizar) e migração das grades antigas (`importSlots`). |
| `logic/target/TargetResolver`, `TargetMatcher` | Alvo ↔ registros (existe? itens da tag, mod do item) e "o item passa nesta linha?" (filtro e Abastecedor). |
| `logic/target/TargetListHost` | Interface dos blocos com listas (Abastecedor, Ponte), para edição e sincronização genéricas. |
| `menu/TargetListEditor` | Servidor: aplica uma edição de lista (`TargetEditPayload`), validando alvo, existência, índice e limite; item do cursor lido do jogador. |
| `menu/TargetListSync`, `TargetLineView`, `TargetListMenu` | Envio das listas à tela só quando mudam (versão) e cópia no cliente; menus com listas. |
| `client/list/TargetListWidget` (estado e entrada), `TargetListPainter` (desenho e dicas), `TargetRowEditor`, `TargetRowLayout`, `TargetEditSender`, `TargetIcons` | Lista na tela: linhas com ícone (slot virtual), caixa de texto validada na hora, quantidade, "∞", "x", "+" em cima, rascunho só no cliente, clique direito alterna tags; ícone alternando para tag/mod (também no monitor). |
| `logic/colony/ColonySlotRule`, `ColonyBlockType` | Regra pura (testada) de quem fica com a vaga; tipos com vaga única. |
| `logic/warehouse/RackDelivery` | Rede ME → racks do armazém, com SIMULATE antes de MODULATE (anti-duplicação). |
| `logic/warehouse/WarehouseStock` | Conta itens nos racks; racks → rede ME (devolve o que a rede recusar). |
| `logic/warehouse/WarehouseSnapshot` | Conteúdo dos racks lido uma vez por ciclo da ponte (+ entregas do ciclo). |
| `integration/ColonyAccess`, `OpenRequest`, `ColonyRef` | **Único** ponto que fala com o MineColonies: colônia, permissões, pedidos em aberto, racks, reatribuir. `ColonyRef` e `OpenRequest` escondem os tipos do MineColonies (o resto do mod usa `colonyAt` e `accepts`). |
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
| `menu/bridge/ColonyBridgeMenu`, `BridgeSnapshot`, `BridgeTab` | Container da tela (ghost slots dos preferidos; o filtro é lista), a "foto" enviada ao cliente e as abas. |
| `block/bridge/ItemFilter` | Formato antigo do filtro (grade de 18): só lido para migrar para a lista. |
| `client/bridge/ColonyBridgeScreen`, `ModListView`, `BridgeIcons` | Tela: barra lateral de ajustes, abas Geral, Filtro, Preferidos e Mods. Lista de pedidos e estatísticas ficam só no monitor. |
| `stats/BridgeStats`, `StatsSummary`, `TopItems` | Estatísticas da ponte (entregas, crafts, ranking de itens). A regra do ranking fica em `TopRanking` (genérico, testado). |

## Abastecedor da Colônia (`supply/`) — mantém o armazém abastecido

| Classe | Responsabilidade |
|---|---|
| `block/supply/ColonySupplyBlock` | Liga o bloco ao block entity e à tela do abastecedor. |
| `block/supply/ColonySupplyBlockEntity` | Dados do abastecedor (listas Manter e Excedente, redstone) e ponte com a tela. |
| `block/supply/StockList` | Formato antigo (grade 18 + 18, e antes 9 + 9): só lido para migrar para as listas. |
| `logic/supply/SupplyLineStatus` | Situação de cada linha em palavras (Abastecido, Falta na rede ME, Retido...) e gravidade para a cor do monitor (regra pura, testada). |
| `logic/supply/SupplyLogic` | **Ciclo:** lê o armazém uma vez, repõe o que falta da rede (tag: soma, item com mais estoque primeiro); devolve o excedente (tag/mod: item com mais unidades primeiro), exceto itens em pedido aberto (anti vaivém). Quantidades e divisão pela `SupplyRule` (regra pura, testada). |
| `logic/supply/SupplyCrafter` | Auto-craft das linhas "manter": `CraftingTracker` sem dono (resultado na rede ME), um job por linha, só com a rede sem o item (`SupplyRule.craft`). |
| `logic/supply/SupplyLineResults` | Armazém, rede e situação de cada linha no último ciclo (tela e monitor). |
| `menu/supply/ColonySupplyMenu`, `SupplySnapshot`, `SupplyLineStat` | Container (só inventário; shift-clique vira linha da aba aberta) e a "foto" enviada ao cliente. |
| `client/supply/ColonySupplyScreen` | Tela com as abas Manter / Excedente, uma lista em cada, e o botão de redstone. |
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
| `logic/terminal/TerminalAction` | Regra pura (testada): ações possíveis. A diferença entre contagens (`CountDiff`), a busca/ordem (`ItemListing`) e a grade (`ItemGrid`) vêm do core. |
| `logic/warehouse/WarehouseItems` | Somar racks por tipo, tirar e guardar com `ItemStack` (sem tipos do AE2). |
| `network/WarehouseContentsPayload`, `WarehouseActionPayload`, `TerminalRecipePayload`, `TerminalPackets` | Pacotes do terminal e validação no servidor (menu, distância, permissão). |
| `client/terminal/WarehouseTerminalScreen`, `WarehouseEntryAdapter` | Tela (busca, ordem, cliques, bancada); a grade com rolagem é a `ItemGrid` do core, lida pelo adapter. |
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

## TC Tablet da Colônia (`tablet/`) — acesso remoto aos blocos

| Classe | Responsabilidade |
|---|---|
| `item/ColonyTabletItem` | Item: shift + clique na Ponte liga à colônia; dica, barra de bateria; (10b) abre as telas. |
| `item/TabletLink` | Colônia ligada (chave + nome), guardada como data component. |
| `item/TabletEnergy` | Bateria no data component `TABLET_ENERGY` via `ComponentEnergyStorage` (também capability de energia). |
| `registry/ModDataComponents` | Registro dos data components do mod (energia e ligação do tablet). |
| `block/bridge/TabletCharger` | Slot do carregador na Ponte: só aceita o tablet, carrega a cada 10 ticks com energia da rede ME, liga à colônia, cai ao quebrar. |
| `menu/bridge/ChargerSlot` | Slot do carregador na aba "Geral" da tela da Ponte. |
| `integration/ae2/GridPower` | Tira energia da rede ME em FE (conversão e multiplicador do AE2). |
| `logic/tablet/TabletCharge` | Regra pura (testada): quanto carregar por passo e largura da barra. |
| `logic/tablet/TabletTab`, `TabletTabs` | Abas do tablet e regras puras (testadas): máscara de disponíveis, qual abrir, gasto por passo. |
| `menu/access/MenuAccess`, `BlockAccess`, `TabletAccess` | "Por onde" a tela foi aberta e quando continua válida: perto do bloco, ou tablet na mão + ligação + bateria + bloco carregado + permissão. Todo pacote de tela passa por aqui (`stillValid`). |
| `menu/tablet/TabletOpener` | Servidor: acha os blocos da colônia (registro da Fase 8, sem carregar chunk, qualquer dimensão), escolhe a aba e abre a tela do bloco com `TabletAccess`. |
| `menu/tablet/TabletView`, `TabletMenu` | Abas disponíveis e aba aberta, enviadas com a abertura da tela; menus que aceitam o tablet. |
| `network/TabletOpenPayload` | Pedido de troca de aba (só o número; o servidor refaz as checagens). |
| `client/tablet/TabletTabBar` | Barra de abas acima da janela das telas abertas pelo tablet. |
| `menu/tablet/TabletPanelMenu`, `network/TabletPanelPayload` | Aba de painel: menu sem slots que manda o `monitorData()` do bloco 1×/s, só quando muda. |
| `client/tablet/TabletPanelScreen`, `client/render/MonitorGui` | Tela do painel: desenha `MonitorPanels` numa interface (mesmo `MonitorCanvas`, sem inverter o y dos itens), com paginação. |

## TC Chunk Loader da Colônia (`loader/`) — mantém a área da colônia carregada

| Classe | Responsabilidade |
|---|---|
| `block/loader/ColonyChunkLoaderBlock`, `ColonyChunkLoaderBlockEntity` | Bloco e lógica: a cada 1 s vê membros online, decide a situação, força/solta só a diferença de chunks e ajusta o consumo parado do nó ME (energia por chunk). |
| `logic/loader/LoaderRule`, `LoaderState` | Regra pura (testada): situação (carregando, contagem, sem energia, dormindo, desligado) e tempo restante em tempo real. |
| `logic/loader/ChunkSelection` | Regra pura (testada): os N chunks mais perto do centro da colônia, em ordem estável. |
| `logic/loader/ChunkTickets` | `TicketController` do NeoForge: força/solta chunks; ao iniciar o servidor descarta tickets de loaders que não estão no registro. |
| `logic/loader/LoaderEvents` | Jogador entrou/saiu → acorda os loaders registrados para conferir membros na hora. |
| `menu/loader/ChunkLoaderMenu`, `ChunkLoaderSnapshot`, `network/ChunkLoader*Payload` | Tela sem slots: foto 1×/s quando muda; botões liga/desliga e redstone. |
| `client/loader/ChunkLoaderScreen` | Tela: situação, cartões (chunks, energia, "solta em"), barra lateral. |
| `integration/ColonyAccess` (`claimedChunksAt`, `colonyCenterAt`, `memberOnlineAt`) | Reivindicações da colônia (`IColonyManager.getClaimData`), centro e membros online. |

## TC Pattern Encoder (`encoder/`) — padrões para o que a colônia pede e o AE2 não crafta

| Classe | Responsabilidade |
|---|---|
| `block/encoder/PatternEncoderBlock`, `PatternEncoderBlockEntity` | Bloco e lógica: confere colônia/permissão a cada ciclo; com a tela aberta, guarda a última varredura; `encode` refaz a varredura e codifica. |
| `block/encoder/EncoderInventory` | Slot de Blank Pattern e 9 de saída; `store` simula antes de gastar o Blank Pattern; derruba tudo ao quebrar. |
| `logic/encoder/EncoderScanner` | Pedidos da colônia sem padrão no AE2 → linhas (exato ou exemplos da tag, soma por item, "na saída"). |
| `logic/encoder/RecipeRanking` | Regra pura (testada): receita com menos ingredientes faltando, depois menos ingredientes por item. |
| `logic/encoder/EncoderState` | Situação da linha: pronta, na saída, sem receita, excluída. |
| `integration/ae2/PatternEncoding` | Único lugar que monta padrões: acha a receita, monta a grade 3×3, confere `matches` e chama `PatternDetailsHelper.encodeCraftingPattern`. |
| `integration/ae2/CraftingRecipeIndex` | Índice item → receitas de bancada, refeito após `/reload` (`OnDatapackSyncEvent`) e ao parar o servidor. |
| `menu/encoder/PatternEncoderMenu`, `EncoderSnapshot`, `EncoderLine`, `network/Encoder*Payload` | Slots, foto 1×/s quando muda (comparação por conteúdo), pacote "codificar" com só o item da linha. |
| `client/encoder/PatternEncoderScreen` | Tela: lista com rolagem, tooltip com ingredientes, slots e botão "codificar todos". |

## Infraestrutura

| Classe | Responsabilidade |
|---|---|
| `ColonyBridgeMod` | Entrada do mod: registros, config, capabilities. |
| `Config` | Config do servidor (ciclo, limites, crafting, craft por tag, estatísticas, monitores). |
| `registry/*` | `DeferredRegister` de blocos, itens, block entities, menus e aba do criativo. |
| `network/*` | Pacotes cliente↔servidor; tudo que vem do cliente é validado no servidor (`ModNetwork`). |
| `client/ColonyBridgeClient` | Entrada só do cliente: liga o botão "Config" da lista de mods à tela de config do NeoForge. |
| `client/ClientSetup`, `ClientPayloadHandler` | Registro das telas/renderers e tratamento dos pacotes no cliente. |
| `client/ui/StatusColors` | Cor de cada estado da ponte, resultado de pedido e linha do Abastecedor (telas e monitores). O resto do design system está no TC Core. |
| `client/jei/*` | Arrastar itens do JEI para as listas e os preferidos (JEI opcional). |
