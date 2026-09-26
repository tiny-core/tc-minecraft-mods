# Registro de decisões

| Data | Decisão | Motivo | Alternativas descartadas |
|---|---|---|---|
| 2026-09 | Mod próprio em vez de contribuir com o ColonyLink | Controle total e modo automático | PR no ColonyLink; scripts CC:Tweaked |
| 2026-09 | MVP alimenta o armazém e reatribui o pedido, sem resolver próprio | Pouco invasivo, resiste a updates do MineColonies | Registrar `IRequestResolver` próprio (fase futura, se necessário) |
| 2026-09 | Crafting sem `ICraftingRequester` (resultado entra na rede ME) | Não há links para persistir; mais simples | `ICraftingRequester` (Fase 6) |
| 2026-09 | Dependências do MineColonies e AE2 via jars do ATM10 em `libs/` | Mesmas versões do modpack alvo | Maven da LDTTeam / Curse Maven |
| 2026-09-26 | Registro de entregas compartilhado por mundo (`DeliveryLedger`, um `SavedData` no overworld) | Um só mecanismo resolve o cooldown que sumia no reinício e a coordenação entre várias pontes na mesma colônia | Cooldown no NBT de cada ponte (não coordena pontes); limitar a uma ponte por colônia (exige eleição de "líder" e troca quando o chunk descarrega) |
| 2026-09-26 | Permissão da colônia exigida na colocação **e** conferida a cada ciclo (`Action.ACCESS_HUTS`) | Cobre permissão retirada depois e colônia que cresce até uma ponte colocada fora dela | Checar só na colocação |
| 2026-09-26 | Blockstate `status` com 4 estados visuais (`BridgeVisualState`), separado do `BridgeStatus` | Poucos modelos para manter; o blockstate só muda (e só gera pacote) quando o visual muda | Um estado por `BridgeStatus` (7 modelos); cor via `BlockEntityRenderer` (mais custo de render) |
| 2026-09-27 | Conexão ME só por baixo e só com cabo comum (checado pelo tipo `AECableType`, não por canais); sem cabo válido o nó não expõe nenhum lado | Pedido do autor para casar com o modelo (porta embaixo); checar o tipo resiste à config de canais do AE2 | Deixar conectar e só bloquear a lógica (o cabo apareceria ligado sem funcionar); checar contagem de canais |
