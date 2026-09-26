# Registro de decisões

| Data | Decisão | Motivo | Alternativas descartadas |
|---|---|---|---|
| 2026-09 | Mod próprio em vez de contribuir com o ColonyLink | Controle total e modo automático | PR no ColonyLink; scripts CC:Tweaked |
| 2026-09 | MVP alimenta o armazém e reatribui o pedido, sem resolver próprio | Pouco invasivo, resiste a updates do MineColonies | Registrar `IRequestResolver` próprio (fase futura, se necessário) |
| 2026-09 | Crafting sem `ICraftingRequester` (resultado entra na rede ME) | Não há links para persistir; mais simples | `ICraftingRequester` (Fase 6) |
| 2026-09 | Dependências do MineColonies e AE2 via jars do ATM10 em `libs/` | Mesmas versões do modpack alvo | Maven da LDTTeam / Curse Maven |
| 2026-09-26 | Registro de entregas compartilhado por mundo (`DeliveryLedger`, um `SavedData` no overworld) | Um só mecanismo resolve o cooldown que sumia no reinício e a coordenação entre várias pontes na mesma colônia | Cooldown no NBT de cada ponte (não coordena pontes); limitar a uma ponte por colônia (exige eleição de "líder" e troca quando o chunk descarrega) |
| 2026-09-26 | Permissão da colônia exigida na colocação **e** conferida a cada ciclo (`Action.ACCESS_HUTS`) | Cobre permissão retirada depois e colônia que cresce até uma ponte colocada fora dela | Checar só na colocação |
