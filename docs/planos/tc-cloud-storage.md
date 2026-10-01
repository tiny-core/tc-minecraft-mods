# Plano — TC Cloud Storage (`tccloud`)

> Estado: **proposta**, nada implementado. Documento irmão (lado do servidor TCMine):
> [`tc-cloud-storage-tcmine.md`](tc-cloud-storage-tcmine.md).

Mod NeoForge 1.21.1 que guarda itens do jogador **fora do mundo**, no banco do TCMine. A rede do
AE2 enxerga esse armazenamento como se fosse um disco. O jogador muda de servidor (dentro da nuvem
do mesmo dono), coloca o bloco no mundo novo e encontra os itens lá.

---

## 1. Princípios (valem mais que qualquer detalhe abaixo)

1. **Na dúvida, perder é melhor que duplicar.** Uma duplicação se espalha pela economia do servidor
   e não tem como ser desfeita. Uma perda é pontual e tem conserto: o histórico permite devolver os
   itens pelo painel.
2. **Débito cedo, crédito tarde.** O que sai da nuvem é gravado antes de poder chegar ao disco no
   mundo; o que entra só é gravado quando o mundo sem o item já está em disco (§5). É isso que faz
   crash, rollback e restauração de backup nunca duplicarem.
3. **Só um servidor por vez mexe no canal de um jogador** (lease com época, ver §4).
4. **O servidor de jogo é confiável; o cliente, nunca.** O cliente só pede ("retirar 64 de X") e o
   servidor valida tudo. O cliente nunca fala com o TCMine.
5. **O isolamento por dono é garantido pelo TCMine, não pelo mod.** A chave do servidor só abre a
   nuvem do dono daquele servidor. O mod não tem como pedir a nuvem de outro dono.
6. **O item que não pode viajar com segurança nem entra na nuvem** (§6). Barrar na entrada é muito
   mais simples que consertar depois.

## 2. Modelo de dados (visão do mod)

```
Dono (OwnerId no TCMine)
 └─ Nuvem (CloudVault) ── servidores do dono ligados a ela (GameServer.CloudVaultId)
     └─ Jogador (UUID Minecraft)
         └─ Canal (nome escolhido pelo jogador, ex.: "Minérios") ── saldos: item → quantidade (long)
```

- Um servidor pertence a **no máximo uma nuvem**. Sem nuvem, o bloco mostra "nuvem desligada neste
  servidor". Isso serve para servidores de teste.
- Um dono pode ter mais de uma nuvem, por exemplo para separar um pack "fácil" de um "difícil".
  Por padrão, cada dono tem uma.
- **Item = impressão digital (SHA-256)** da codificação canônica do `ItemStack` (ID + componentes,
  com quantidade 1). A codificação vai junto como blob opaco. O TCMine nunca decodifica o item: ele
  guarda e devolve.

## 3. Arquitetura do mod

Novo mod em `mods/cloud-storage/` (modid `tccloud`), dependendo do `tccore` e do AE2.

| Pacote | Responsabilidade | Testável em JUnit? |
|---|---|---|
| `cloud/` | Regras puras: cache do canal, lotes, sequência, lease, cotas, netting de deltas | **Sim** (núcleo) |
| `cloud/journal/` | Diário em disco (`<mundo>/tccloud/journal.dat` + `checkpoint.json`), gravação atômica (tmp + rename) | Sim (com pasta temporária) |
| `integration/tcmine/` | Cliente HTTP (`java.net.http.HttpClient`, assíncrono), DTOs, autenticação, `CloudBackend` (interface) | Sim (com backend falso) |
| `integration/ae2/` | Nó da grade e `MEStorage` do canal; verificação de célula AE2 vazia | Não (precisa do jogo) |
| `item/` | `TransferGuard`: decide se um item pode entrar ou sair (§6); codificação/impressão digital | Parte pura sim |
| `block/` | Bloco **TC Cloud Link**: dono, canal escolhido, modo de acesso da rede | Não |
| `menu/`, `network/` | Menu, pacotes cliente→servidor (sempre validados) | Validação sim |
| `client/` | Tela do terminal (reaproveitando a grade de itens que vai para o `tccore`) | Não |
| `registry/`, `Config` | Registros e config | — |

**Interface `CloudBackend`** (≈ `interface` do C#) com duas implementações: `HttpCloudBackend` (TCMine
real) e `FileCloudBackend` (arquivo local, para desenvolver e testar sem o TCMine). Todo o resto do
mod só conhece a interface.

**Sem chave, sem nuvem.** A URL e a chave chegam por **variáveis de ambiente** do container
(`TCMINE_CLOUD_URL`, `TCMINE_CLOUD_KEY`), injetadas pelo TCMine ao criar o servidor. Elas **não**
ficam em arquivo de config. Motivos:
- Uma config do tipo `SERVER` do NeoForge é **sincronizada para os clientes**: a chave vazaria para
  todo jogador.
- Um arquivo de config acaba copiado para dentro do modpack ou de backups compartilhados.

O mod também se recusa a ligar a nuvem se o servidor estiver em `online-mode=false`, porque aí o UUID
do jogador não é verificado pela Mojang. Em singleplayer, a nuvem fica desligada.

## 4. Lease: um servidor por vez

```
login ─► acquire(jogador) ─► recebe época N + snapshot dos canais ─► canal montado
           │ 409 "em uso no servidor X"  ─► canal aparece bloqueado, com o motivo
heartbeat a cada 60 s (todos os leases do servidor numa chamada só)
logout ─► sela débitos ─► save forçado (coalescido) ─► envia lotes ─► release(época N, últimaSeq)
```

- **Época (fencing token):** cada `acquire` aumenta a época. Lote com época antiga é recusado e vai
  para a **quarentena** (o dono decide no painel). Assim, um servidor que "volta dos mortos" não
  sobrescreve o que outro fez.
- **TTL:** sem heartbeat por 30 min (configurável por nuvem), o lease expira. Antes disso, o jogador
  vê "seus itens estão sincronizando no servidor X". O dono pode **forçar a liberação** no painel.
  Quando o próprio TCMine fica fora do ar, ao voltar ele **estende** todos os leases ativos, para não
  expirar leases por culpa dele.
- **O canal só fica montado na rede AE2 enquanto o dono está online naquele servidor.** Isso amarra
  o lease à presença e evita automação mexendo no canal de quem não está lá.
- O mesmo canal só pode ser montado por **um Cloud Link por servidor**. Dois Links no mesmo canal
  fariam o AE2 contar os itens em dobro, o que quebra os cálculos do autocraft.

## 5. Diário: "débito cedo, crédito tarde" (o anti-duplicação de verdade)

**O problema clássico** de qualquer armazenamento externo: o jogador deposita 64 diamantes, a nuvem
confirma, o servidor crasha e o disco ainda tem os diamantes no baú. Resultado: 128 diamantes.

**O que a fase 0 descobriu** (ver §11): o mundo em disco **não** é uma foto tirada no autosave. O
vanilla grava até 20 chunks alterados **por tick**, sempre que sobra tempo, e grava cada chunk ao
descarregá-lo. O inventário do jogador é gravado no logout. Depois de um crash, o disco é uma
**mistura** de momentos. Por isso a ideia anterior ("confirmar tudo no save") não basta: um item
retirado da nuvem para um baú pode chegar ao disco antes do save, e com a retirada ainda não
registrada, o item duplica.

**A regra:** cada lado do movimento só fica durável quando o outro lado já não tem como duplicar.

- **Débito (saiu da nuvem) → gravado no diário ao fim do tick em que aconteceu**, antes que o item
  possa chegar ao disco no mundo. A gravação dos chunks é assíncrona (thread de IO) e a do diário é
  síncrona no fim do tick, então o diário chega primeiro.
- **Crédito (entrou na nuvem) → gravado no diário só depois que o mundo SEM o item está
  garantidamente em disco.** Os créditos de antes de um autosave ficam "aguardando IO" e só são
  gravados no autosave seguinte, quando a escrita dos chunks daquele save já terminou há minutos. Em
  save com flush (logout, `/tccloud checkpoint`), são gravados assim que a espera de IO termina.
- O lote só é enviado ao TCMine **depois** de gravado no diário (idempotente por época + seq).
  Diário: arquivo próprio em `<mundo>/tccloud/`, append + gravação atômica do checkpoint. **Não** usa
  `SavedData`: o `SavedData` é gravado no início do save, antes do evento, e ficaria um ciclo atrasado.
- **Netting:** retirar um item com crédito ainda pendente consome primeiro o crédito pendente, e só o
  excedente vira débito. Assim o lote nunca manda "−64" de algo que o banco não viu "+64". É regra
  pura, com teste JUnit.

Cenários de crash (o disco pode estar em qualquer ponto entre "antes" e "depois" da operação):

| Situação | Nuvem após o crash | Mundo em disco | Resultado |
|---|---|---|---|
| Retirou (débito gravado no fim do tick) | debitada | item gravado ou não | ✅ ou perda; **nunca duplicação** |
| Depositou (crédito ainda pendente) | sem o crédito | item ainda lá ou já não | ✅ ou perda; **nunca duplicação** |
| Crédito já durável | creditada | mundo sem o item com certeza | ✅ consistente |
| Crash entre a gravação e o envio | diário reenvia no boot | — | ✅ consistente |

**Perdas possíveis ficam visíveis.** Os créditos pendentes e os débitos dos últimos minutos também vão
para o diário, marcados como "não confirmados". No boot depois de um desligamento não limpo, o mod
os reporta como **operações em dúvida**. O dono vê a lista no painel (jogador, item, quantidade,
horário) e decide se devolve. Nada é devolvido automaticamente, porque devolver às cegas seria
duplicar quando o mundo já tinha gravado o item.

### 5.1 Logout
O vanilla grava o inventário no logout. O mod então dispara um **save com flush, coalescido** (no
máximo 1 a cada N segundos, configurável; vários jogadores saindo juntos geram um save só). Depois
da espera de IO, ele grava os créditos, envia os lotes e libera o lease. Até lá, o jogador vê nos
outros servidores "sincronizando no servidor X". O custo é um `save-all flush` por janela de N
segundos, e é por isso que é coalescido e configurável.

### 5.2 Mundo que volta no tempo (backup restaurado, cópia manual)
- O `checkpoint.json` (dentro do mundo) guarda o `worldId` (aleatório, criado uma vez) e, por
  jogador, a última época/seq **gravada**.
- No `hello` de boot, o mod envia o checkpoint. Se o TCMine já aplicou lotes **além** dele, o mundo
  voltou no tempo. O TCMine abre um **incidente de rollback** e o mod deixa a nuvem **somente
  leitura** naquele servidor até o dono decidir no painel: **reverter** os lotes posteriores
  (recomendado, com prévia) ou **aceitar**.
- O backup a quente do TCMine (`save-off` → `save-all flush` → copia) ganha um passo: depois do flush,
  ele roda `/tccloud checkpoint` pelo RCON, que grava os créditos pendentes e o checkpoint. Assim, o
  zip sai com o diário coerente com os chunks.

### 5.3 TCMine fora do ar
Com o lease já em mãos, o jogo continua normalmente: o diário acumula. Passando de um teto (ex.:
5.000 operações ou 2 h), o canal vira **somente leitura** até reconectar. Sem lease (o jogador
entrou durante a queda), o canal fica indisponível. Nada de "modo otimista".

## 6. Itens que não podem viajar — `TransferGuard`

Roda **no servidor**, na entrada (depósito) e de novo na saída, porque a política pode ter mudado.
As regras vão em ordem e a primeira que recusa decide:

| # | Regra | Por quê | Mensagem ao jogador |
|---|---|---|---|
| 1 | Codificação ≤ 8 KB (config do TCMine) | livros gigantes, NBT inflado, pacotes enormes | "Item com dados grandes demais" |
| 2 | **Política do dono** (painel): bloquear/permitir por item, mod ou tag; modo lista negra (padrão) ou **lista branca** | o dono manda no equilíbrio da nuvem dele | "Bloqueado pelas regras da nuvem" |
| 3 | Tag `#tccloud:never_transfer` (o mod traz uma lista inicial; o dono amplia por datapack) | itens sabidamente presos ao mundo | "Este item não pode sair do mundo" |
| 4 | **Conteúdo interno não vazio:** capability de itens (`Capabilities.ItemHandler.ITEM`) com algum slot ocupado; célula do AE2 com itens (API `StorageCells`) | mochilas, shulkers, células, bins: um item carregando milhares e furando a cota | "Esvazie o item antes" |
| 5 | **Heurística de referência ao mundo:** componentes que, convertidos em NBT, têm UUID (int[4] ou texto no formato UUID) ou chaves do tipo `uuid`, `storage_id`, `frequency` | o conteúdo mora no save do mundo, não no item | "Item vinculado ao mundo — aguardando aprovação do dono" |

**Como isso resolve os itens que guardam dados no mundo:**
- O caso perigoso (o item leva só um ID e o conteúdo fica no mundo antigo) é pego pela **regra 4**
  quando o mod expõe o conteúdo por capability: cheio, não entra; vazio, viajar não duplica nada.
- Quando o mod **não** expõe o conteúdo (ex.: discos de outros mods de armazenamento), a **regra 5**
  pega o UUID e recusa por padrão. O servidor também **reporta** o item ao TCMine como suspeito. O
  dono vê a lista no painel e decide uma vez: **liberar** aquele item (vira regra 2 de permissão)
  ou **bloquear de vez**.
- A **regra 3** é a lista curada que cresce com os testes no ATM10. Candidatos a verificar um a um
  (não presumir): discos do Refined Storage, Sophisticated Backpacks/Storage, itens do Ender
  Storage, drives QIO do Mekanism, Create (toolbox), Iron Chests (upgrades), dank/mochilas em geral.
- O **modo lista branca** ("só vanilla + mods aprovados") é a opção mais segura para quem não quer
  ter trabalho, e o painel oferece isso na criação da nuvem.

### 6.1 Compatibilidade na saída (o que o jogador vê como "incompatível")
Ao montar o snapshot, cada item é **decodificado com o registro do servidor atual** (`RegistryOps`,
porque encantamentos e outros dados são registros dinâmicos no 1.21). O resultado vira um status
cacheado por item:

| Status | Causa | Retirável / visível ao AE2 |
|---|---|---|
| `OK` | decodificou e o guard aprova | sim |
| `MOD_MISSING` | namespace do ID não está carregado | não |
| `ITEM_MISSING` | mod presente, item não existe nessa versão | não |
| `DATA_INVALID` | componente desconhecido ou inválido (versão diferente do mod, encantamento ausente) | não |
| `BLOCKED` | política ou guard recusa agora | não |

Item que não está `OK` **nunca entra no `MEStorage`**: o AE2 não o vê, então autocraft, terminais e
exportadores não têm como usá-lo. Na tela, ele só aparece com o filtro "mostrar incompatíveis"
ligado: em cinza, com o motivo no tooltip.

## 7. Bloco TC Cloud Link e a rede AE2

- Ao ser colocado, o bloco fica **vinculado ao UUID de quem colocou**. Só o dono abre a tela (checagem
  no servidor, distância ≤ 8 blocos). Quebrar o bloco **não derruba itens**.
- **Modo de acesso da rede** (o AE2 1.21 não tem mais o Security Terminal; quem acessa a grade acessa
  tudo):
  - **Somente terminal**: a rede não vê o canal.
  - **Somente depósito**: a rede insere, mas não retira.
  - **Completo** (padrão): a rede insere e retira, com prioridade baixa configurável.
  - A tela avisa: "quem tem acesso a esta rede AE2 pode usar este canal enquanto você está online".
- `MEStorage` (≈ interface com `insert`, `extract`, `getAvailableStacks`):
  - opera só sobre o cache em memória; `SIMULATE` não altera nada; `MODULATE` altera o cache e o
    lote aberto;
  - `insert` passa pelo `TransferGuard` e pela cota (tipos e quantidade total) antes de aceitar;
  - `getAvailableStacks` lista só itens `OK`.
- **Pacotes do cliente** (retirar/depositar pela tela): o servidor valida dono, distância, chunk
  carregado, lease, quantidade ≤ saldo e espaço no inventário. Sempre `SIMULATE` antes de
  `MODULATE`, e o que não couber volta para a nuvem. A lista sincronizada para a tela tem teto de
  tamanho, é paginada e só é reenviada quando muda.

## 8. Protocolo com o TCMine (resumo)

Detalhes e tabelas estão no documento do TCMine. HTTPS (ou a rede interna do Docker), JSON, versão de
protocolo no `hello`. Tudo assíncrono, fora da thread do servidor. O resultado volta para a thread
principal via `server.execute(...)`.

| Chamada | Quando |
|---|---|
| `POST /api/cloud/v1/hello` | boot: versão do mod/protocolo, `worldId`, checkpoint, `online-mode` → config da nuvem, versão da política, incidentes |
| `GET /api/cloud/v1/policy?since=v` | boot e quando o heartbeat avisar que a política mudou |
| `POST /api/cloud/v1/leases/acquire` | login do jogador → época + snapshot |
| `POST /api/cloud/v1/leases/heartbeat` | a cada 60 s, todos os leases → comandos (congelar, liberar, política nova) |
| `POST /api/cloud/v1/batches` | após cada save, os lotes selados (época, seq, operações, saldos esperados) |
| `POST /api/cloud/v1/leases/release` | após o logout + save + envio |
| `POST /api/cloud/v1/channels` (+ renomear/apagar vazio) | jogador cria/gerencia canais na tela |
| `POST /api/cloud/v1/reports/suspects` | itens recusados pela heurística (agregados) |
| `POST /api/cloud/v1/reports/doubtful` | boot após desligamento não limpo: operações "não confirmadas" do diário (§5) |

Cada lote leva também os **saldos esperados** dos itens que tocou. Se o TCMine calcular outra coisa,
há divergência: o lote vai para a quarentena e o canal é congelado. Isso detecta bug ou trapaça cedo.

## 9. Fases

| Fase | Entrega | Critério de pronto |
|---|---|---|
| **0. Spike de riscos** ✅ | Respostas lendo o código do NeoForge 21.1.252 e do AE2 19.2.17 (§11) | feito em 2026-10-01 |
| **1. Núcleo puro** | `cloud/` + `cloud/journal/` + `TransferGuard` (parte pura) com JUnit: netting, sequência, época, lote idempotente, cotas, crash simulado | testes verdes |
| **2. Mod com `FileCloudBackend`** | Bloco, AE2, tela (grade de itens extraída para o `tccore`), filtro compatível/incompatível, lease simulado | autor testa em jogo sem TCMine |
| **3. TCMine: tabelas + API** | Ver documento do TCMine, fases A–B | testes de contrato no TCMine |
| **4. Integração real** | `HttpCloudBackend`, chave por variável de ambiente, `hello`/checkpoint | dois servidores do mesmo dono trocando itens |
| **5. Painel** | Políticas, suspeitos, quarentena, incidentes, auditoria (fases C–D do TCMine) | dono resolve tudo sem SQL |
| **6. Testes de caos** | `kill -9` no meio do jogo, logout + kill, TCMine fora do ar, dois servidores ao mesmo tempo, restaurar backup, cópia manual de mundo | nenhum cenário duplica; perdas listadas e reversíveis pelo painel |
| Depois | Fluidos/químicos, canal compartilhado com amigos, ver canais no launcher | — |

## 10. Riscos em aberto
- **Janela do débito:** se a thread de IO gravar o chunk com o item retirado **antes** do fim do
  tick e o processo morrer nesses milissegundos, há duplicação. A chance é mínima e é o risco
  aceito. A fase 6 tenta provocá-lo de propósito.
- **Custo do save no logout:** um `save-all flush` coalescido. Em servidor grande, pode ser preciso
  aumentar a janela; isso precisa ser medido na fase 6.
- **Crash durante o save:** um mundo salvo pela metade já corrompe o próprio mundo. Fica fora do
  escopo.
- **Heurística de UUID com falso positivo:** itens com dono "soulbound" vão para a fila de suspeitos.
  Custo: um clique do dono por item, uma vez.
- **Monetização:** vender capacidade de armazenamento pode conflitar com as regras de uso comercial
  da Mojang. Verificar antes de criar planos pagos.

## 11. Resultados da fase 0 (2026-10-01)

Fontes: `neoforge-21.1.252-sources.jar` (Minecraft com patches do NeoForge) e
`appliedenergistics2-19.2.17-api.jar` (`javap`).

**(a) Ordem do save — o plano original NÃO funcionava; §5 foi refeita.**
- `ServerLevel.save`: `saveLevelData()` (grava o `DimensionDataStorage`, ou seja, todo
  `SavedData`) → `chunkSource.save(flush)` → entidades → **evento `LevelEvent.Save`** → (só com
  flush) `IOUtilities.waitUntilIOWorkerComplete()`. Consequências:
  - o evento chega **depois** que o `SavedData` já foi gravado, então o diário em `SavedData`
    ficaria um ciclo atrasado. Daí o arquivo próprio;
  - no autosave (sem flush) a escrita dos chunks ainda está em curso quando o evento chega.
- `ChunkMap.tick`: grava **até 20 chunks alterados por tick** quando sobra tempo, e grava cada chunk
  ao descarregá-lo (`scheduleUnload`).
- `PlayerList.remove` grava o inventário do jogador no logout.
- Conclusão: o disco é atualizado continuamente, e o único desenho seguro é "débito cedo, crédito
  tarde".

**(b) Montar armazenamento próprio no AE2 — confirmado pela API.**
- `IStorageProvider` (serviço de nó): `mountInventories(IStorageMounts)` →
  `mounts.mount(MEStorage, prioridade)`. Para remontar quando o dono entra/sai ou troca de canal:
  `IStorageProvider.requestUpdate(managedNode)`.
- Registro: `managedNode().addService(IStorageProvider.class, ...)` antes de criar o nó, o mesmo
  padrão que o Colony Bridge já usa com `ICraftingRequester` (`AbstractBridgeBlockEntity`).
- `MEStorage`: `insert`/`extract(AEKey, long, Actionable, IActionSource)`,
  `getAvailableStacks(KeyCounter)`, `getDescription()`. O `IActionSource` permite saber se quem pede
  é um jogador ou automação (útil para o modo "somente depósito").
- Célula do AE2 com conteúdo: `StorageCells.getCellInventory(stack, null)` → `getStatus()`
  (`CellState`) diferente de `EMPTY` → recusar.
- A prova em jogo (montar e ver no terminal) fica para a fase 2.

**(c) Item de mod ausente — erro tratável, mas com duas armadilhas.**
- `ItemStack.CODEC`: ID desconhecido → `holderByNameCodec` devolve `DataResult.error`.
  Componente desconhecido (`DataComponentPatch.PatchKey.CODEC`) → `DataResult.error("No component
  with type …")`. Nenhum dos dois lança exceção.
- **Armadilha 1 — resultado parcial:** o mapa de componentes é um `dispatchedMap` do DFU, que
  devolve erro **com resultado parcial** (o item sem o componente que falhou). Regra: **qualquer
  erro = item incompatível; nunca usar o parcial.** Usar o parcial entregaria um item "limpo" (perda
  de dados ou exploit).
- **Armadilha 2 — impressão digital:** o `CompoundTag` guarda as chaves num `HashMap`, então a ordem
  na serialização não é estável. A impressão digital precisa de um **serializador canônico** (chaves
  ordenadas recursivamente) sobre o NBT de `ItemStack.CODEC` com quantidade 1. Sem isso, o mesmo item
  viraria duas linhas na nuvem.
- Decodificar sempre com `RegistryOps` do servidor (`registryAccess()`): encantamentos são registro
  dinâmico no 1.21, e um encantamento de datapack ausente também cai na armadilha 1.
