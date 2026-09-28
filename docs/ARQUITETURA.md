# Arquitetura — quem faz o quê

Mapa das responsabilidades de cada classe, agrupado por **bloco do mod**. Regra geral: cada camada
(`block`, `logic`, `menu`, `client`) tem um subpacote por bloco (`bridge/`, `supply/`, `monitor/`) e a
raiz da camada guarda só o que é **compartilhado**. Código compartilhado nunca importa nada de um
subpacote específico, então mexer na Ponte não quebra o Abastecedor (e vice-versa).

Caminhos relativos a `src/main/java/org/tinycore/colonybridge/`.

---

## Compartilhado entre Ponte e Abastecedor

| Classe | Responsabilidade |
|---|---|
| `block/AbstractBridgeBlock` | Bloco no mundo: permissão para colocar, dono, estado visual (`STATUS`), ticker, vizinho mudou, clique direito abre a tela. |
| `block/AbstractBridgeBlockEntity` | Nó da grid do AE2 e seu ciclo de vida, regra do cabo, redstone, dono/permissão, chama `runCycle` a cada `cycleTicks`. |
| `block/BridgeVisualState` | Os 4 visuais do blockstate (offline / erro / ocioso / trabalhando). |
| `block/RedstoneMode` | Ignorar / só com sinal / só sem sinal. |
| `logic/BridgeStatus` | Estado mostrado ao jogador (sem colônia, sem permissão, trabalhando...). |
| `logic/warehouse/RackDelivery` | Rede ME → racks do armazém, com SIMULATE antes de MODULATE (anti-duplicação). |
| `logic/warehouse/WarehouseStock` | Conta itens nos racks; racks → rede ME (devolve o que a rede recusar). |
| `menu/AbstractGhostMenu`, `GhostContainer`, `GhostSlot`, `TabSlot`, `JoinedList` | Ghost slots: mostram um item-modelo, nunca guardam nem entregam itens. |
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
| `logic/bridge/DeliveryLedger` | Registro salvo no mundo: pedido já entregue (cooldown) ou em craft, e por qual ponte. Coordena várias pontes. |
| `logic/bridge/CycleReport`, `RequestLine`, `RequestOutcome` | Resultado de cada pedido no último ciclo (vai para a tela e os monitores). |
| `logic/crafting/CraftingTracker` | Cálculos/jobs no AE2, espera após falha, blacklist, item em craft por pedido. |
| `logic/crafting/CraftCandidates` | Escolhe o item a craftar para pedidos por tag (preferência, só vanilla, limite de candidatos). |
| `logic/crafting/CraftCost` | Custo estimado de um item pelas receitas do AE2 (cache por ciclo). |
| `logic/crafting/CraftPreference` | `CHEAPEST` / `MOST_EXPENSIVE` / `LIST`. |
| `logic/crafting/CraftRules`, `ModFilterMode` | Regras prontas (ponte + config do servidor) que o `CraftCandidates` recebe; filtro/prioridade por mod. |
| `logic/crafting/CraftableMods` | Mods com item craftável na rede, para a aba "Mods". |
| `logic/bridge/RequestCounts` | Resumo do ciclo (abertos, atendidos, craftando) para a aba "Geral". |
| `menu/bridge/ColonyBridgeMenu`, `BridgeSnapshot`, `BridgeTab` | Container da tela (ghost slots do filtro e dos preferidos), a "foto" enviada ao cliente e as abas. |
| `client/bridge/ColonyBridgeScreen`, `ModListView` | Tela: abas Geral, Filtro, Preferidos e Mods. Lista de pedidos e estatísticas ficam só no monitor. |
| `stats/*` | Estatísticas da ponte em ring buffer (entregas, crafts, ranking de itens). |

## Abastecedor da Colônia (`supply/`) — mantém o armazém abastecido

| Classe | Responsabilidade |
|---|---|
| `block/supply/ColonySupplyBlock` | Liga o bloco ao block entity e à tela do abastecedor. |
| `block/supply/ColonySupplyBlockEntity` | Dados do abastecedor (listas, redstone) e ponte com a tela. |
| `block/supply/StockList` | As duas listas: slots 0-8 "manter no armazém", 9-17 "excedente para o ME", com quantidade alvo. |
| `logic/supply/SupplyLogic` | **Ciclo:** repõe o que falta da rede; devolve o excedente, exceto itens em pedido aberto (anti vaivém). |
| `menu/supply/ColonySupplyMenu`, `SupplySnapshot` | Container da tela e a "foto" enviada ao cliente. |
| `client/supply/ColonySupplyScreen` | Tela com as duas listas e o botão de redstone. |

## Monitor da Colônia (`monitor/`) — mostra dados de uma Ponte

| Classe | Responsabilidade |
|---|---|
| `block/monitor/MonitorBlock`, `MonitorBlockEntity` | Bloco e posição dentro da tela; o mestre guarda a ligação com a ponte. |
| `block/monitor/MonitorData`, `MonitorLine` | Dados sincronizados 1×/s, só quando mudam. |
| `multiblock/MonitorFormation` | Junta monitores vizinhos num retângulo e elege o mestre. |
| `item/LinkCardItem` | Cartão de ligação monitor → ponte (valida dimensão, distância, permissão). |
| `client/render/*` | Desenho no mundo: renderer, canvas, painéis, lista paginada, animação. |

## Infraestrutura

| Classe | Responsabilidade |
|---|---|
| `ColonyBridgeMod` | Entrada do mod: registros, config, capabilities. |
| `Config` | Config do servidor (ciclo, limites, crafting, craft por tag, estatísticas, monitores). |
| `registry/*` | `DeferredRegister` de blocos, itens, block entities, menus e aba do criativo. |
| `network/*` | Pacotes cliente↔servidor; tudo que vem do cliente é validado no servidor (`ModNetwork`). |
| `client/ColonyBridgeClient` | Entrada só do cliente: liga o botão "Config" da lista de mods à tela de config do NeoForge. |
| `client/ClientSetup`, `ClientPayloadHandler` | Registro das telas/renderers e tratamento dos pacotes no cliente. |
| `client/ui/*` | Design system: `ScreenStyle` (telas no visual do AE2), `UiColors` (monitores, tema escuro), botões, gráfico de barras, `Painter`. |
| `client/jei/*` | Arrastar itens do JEI para o filtro (JEI opcional). |
