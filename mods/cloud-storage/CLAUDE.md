# TC Cloud Storage — instruções específicas

As regras gerais (autor, idioma, arquitetura, segurança, desempenho, documentação, git) estão no
`CLAUDE.md` da raiz do workspace. Aqui, só o que é deste mod.

Armazena itens do jogador **fora do mundo**, no banco do TCMine (repositório `tiny-core/TCMine`), e
expõe isso à rede do AE2. Plano completo: `docs/planos/tc-cloud-storage.md` (raiz do workspace); lado
do TCMine: `docs/planos/tc-cloud-storage-tcmine.md`. **Ler o plano antes de mudar qualquer regra de
`cloud/`.**

Estado: **fase 1** — só o núcleo puro com testes (`cloud/`, `cloud/journal/`, `item/`, `item/policy/`).
Sem bloco, sem tela, sem rede.

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
- URL e chave do TCMine só por variável de ambiente (`TCMINE_CLOUD_URL`, `TCMINE_CLOUD_KEY`); nunca em
  config do tipo `SERVER` (é sincronizada para os clientes).

## Pacotes

| Pacote | O quê |
|---|---|
| `cloud/` | `PlayerCloudSession` (fachada por jogador), `ChannelBalances` (saldos locais + cota), `PendingChanges`, `BatchSequencer`, `Batch`/`CloudOp`/`BalanceKey` |
| `cloud/journal/` | `JournalFile` (quadros com CRC), `JournalCodec`, `JournalReplay` (outbox, em dúvida, compactação), `Checkpoint` (JSON) |
| `item/` | `CanonicalNbt`, `ItemFingerprint`, `WorldReferenceDetector` |
| `item/policy/` | `ItemPolicy`, `ItemRule` |
