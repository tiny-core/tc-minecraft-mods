# Registro de decisões

| Data | Decisão | Motivo | Alternativas descartadas |
|---|---|---|---|
| 2026-10-01 | Nuvem por dono (no TCMine), não global | Pedido do autor; isola cada admin e limita o estrago de uma chave vazada à nuvem do próprio dono | Nuvem global da plataforma; banco central fora do TCMine |
| 2026-10-01 | "Débito cedo, crédito tarde" em vez de confirmar tudo no save | Fase 0: o Minecraft grava chunks a qualquer momento (até 20/tick e ao descarregar), então o disco após um crash é uma mistura de momentos; só assim nenhum cenário duplica | Confirmar no `LevelEvent.Save` (duplica via gravação ansiosa de chunk); confirmar na hora (duplica no rollback) |
| 2026-10-01 | Netting assimétrico (débito consome crédito pendente; crédito nunca anula débito) | O chunk com o item retirado pode ir para a fila de IO entre as duas operações; anular o débito duplicaria | Netting nos dois sentidos |
| 2026-10-01 | Diário próprio em `<mundo>/tccloud/` com quadros CRC, não `SavedData` | O `SavedData` é gravado antes do evento de save (ficaria um ciclo atrasado); dentro do mundo, o backup leva o diário junto | `SavedData`; diário fora da pasta do mundo |
| 2026-10-01 | Perdas possíveis viram "operações em dúvida" decididas pelo dono | Depois de um crash o mod não sabe se o mundo gravou o item; devolver sozinho duplicaria | Devolver automaticamente; ignorar |
| 2026-10-01 | Impressão digital = SHA-256 do NBT canônico (chaves ordenadas) | O `CompoundTag` usa `HashMap`; o NBT comum do mesmo item muda de bytes | Hash do NBT comum; ID + hash de `toString()` |
| 2026-10-01 | Política de itens: mais específico vence (item > tag > mod > modo); empate → bloquear | Permite "bloquear o mod, liberar um item"; na dúvida, bloquear | Primeira regra que casar (depende da ordem no painel) |
