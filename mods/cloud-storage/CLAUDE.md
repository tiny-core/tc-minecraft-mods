# TC Cloud Storage — instruções específicas

As regras gerais (autor, idioma, arquitetura, segurança, desempenho, documentação, git) estão no
`CLAUDE.md` da raiz do workspace. Aqui, só o que é deste mod.

Armazena itens do jogador **fora do mundo**, no banco do TCMine (repositório `tiny-core/TCMine`), e
expõe isso à rede do AE2. Plano completo: `docs/planos/tc-cloud-storage.md` (raiz do workspace); lado
do TCMine: `docs/planos/tc-cloud-storage-tcmine.md`. **Ler o plano antes de mudar qualquer regra de
`cloud/`.**

Estado: **fase 4** — fala com o TCMine de verdade (`HttpCloudBackend`, API `/api/cloud/v1`); sem o TCMine,
a **nuvem local** (`LocalCloudBackend`, arquivo deste computador com trava de uma instância; `localCloud=true`, padrão)
é a nuvem do singleplayer e de servidores próprios — é um recurso, não só teste. Vários canais por jogador:
cada Link escolhe o seu; criar/renomear pela tela (`PlayerChannels`; no TCMine depende dos endpoints de canal).

Dev: `./gradlew :cloud-storage:runClient -Plibs_dir=<pasta com o jar do AE2 e do GuideMe>` (padrão:
`mods/colony-bridge/libs`). A nuvem local já vem ligada (`run/tccloud-local.json`).

## Regras que não podem ser quebradas

- **Débito cedo, crédito tarde** (`PendingChanges`). Débito durável no fim do MESMO tick; crédito só
  depois que o mundo sem o item está em disco. Netting só no sentido débito-consome-crédito. Qualquer
  mudança aqui exige o `CrashSimulationTest` verde **e** um teste novo do cenário.
- O lote vai para o diário (`JournalFile.append(..., force=true)`) **antes** de ser enviado ao TCMine.
- O diário e o checkpoint moram na pasta do mundo (`<mundo>/tccloud/`), nunca em `SavedData`
  (o `SavedData` é gravado antes do evento de save — fase 0, plano §11).
- Operações em dúvida nunca são devolvidas automaticamente.
- Decodificar item: **qualquer** erro do `DataResult` = incompatível; nunca usar o resultado parcial.
- Impressão digital sempre por `ItemFingerprint` (NBT canônico), nunca pelo NBT comum.
- Todo item que entra passa pelo `TransferGuard` (via `CloudInventory`); o AE2 e a tela nunca chamam a
  `PlayerCloudSession` direto.
- Config é `COMMON`, nunca `SERVER`.
- **AE2 opcional:** nenhuma classe fora de `integration/ae2/` importa o AE2; o resto fala com `block/LinkNetwork` e
  `integration/Ae2Compat`. Testar sem o AE2: `./gradlew :cloud-storage:runServer -Plibs_dir=<pasta vazia>`.
- URL e chave do TCMine: variáveis de ambiente (`TCMINE_CLOUD_URL`, `TCMINE_CLOUD_KEY`) ou o
  `tccloud-server.json` que o TCMine grava na pasta do servidor a cada start (`CloudCredentials`). Nunca em
  config do mod. A chave nunca vai para log (`CloudCredentials.toString` mostra só o prefixo).
- Com o TCMine, a nuvem só liga em `online-mode=true` (`CloudBackends`).
- Formato do JSON em `CloudApiDto`, espelhando o TCMine; mudança quebrada = subir `HttpCloudBackend.PROTOCOL`
  junto com o `CloudProtocol` do TCMine.

## Pacotes

| Pacote | O quê |
|---|---|
| `cloud/` | `PlayerCloudSession` (fachada por jogador), `ChannelBalances` (saldos locais + cota), `ChannelNames`, `PendingChanges`, `BatchSequencer`, `Batch`/`CloudOp`/`BalanceKey` |
| `cloud/journal/` | `JournalFile` (quadros com CRC), `JournalCodec`, `JournalReplay` (outbox, em dúvida, compactação), `Checkpoint` (JSON) |
| `item/` | `ItemCodec` (bytes ↔ `ItemStack`), `ItemCatalog` (caches), `TransferGuard` + probes, `CanonicalNbt`, `ItemFingerprint`, `WorldReferenceDetector`, `CloudItemTags` |
| `item/policy/` | `ItemPolicy`, `ItemRule` |
| `server/` | `CloudService` (ciclo de vida, diário, saves, política), `PlayerLeases`, `PlayerChannels` (criar/renomear), `JournalWriter`, `BatchOutbox`, `CloudReports` + `DoubtfulOutbox` + `SuspectCollector` (relatórios ao dono), `CloudInventory` (guardar/retirar/listar), `ChannelMounts`, `CloudServerEvents`, `CloudCommands` |
| `integration/tcmine/` | `CloudBackend` (interface), `HttpCloudBackend` + `CloudApiDto` + `CloudCredentials` (TCMine), `LocalCloudBackend` + `LocalCloudState` (desenvolvimento) |
| `integration/` | `Ae2Compat`: diz se o AE2 está instalado e só então chama o `Ae2Bridge` |
| `integration/ae2/` | **Opcional.** `Ae2Bridge` (entrada única), `CloudLinkNode` (nó + `IStorageProvider`, é a `LinkNetwork` do Link), `CloudMEStorage`, `Ae2CellProbe` |
| `block/`, `menu/`, `network/`, `client/`, `registry/` | TC Cloud Link, tela (grade do core), pacotes, registros |
