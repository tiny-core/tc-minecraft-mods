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
2. **O mundo e a nuvem andam juntos.** Uma mudança na nuvem só é confirmada quando o mundo que a
   causou foi salvo em disco (ver §5). É isso que resolve crash, rollback e restauração de backup.
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

## 5. Diário alinhado ao save do mundo (o anti-duplicação de verdade)

**O problema clássico** de qualquer armazenamento externo: o jogador deposita 64 diamantes, a nuvem
confirma, o servidor crasha antes de salvar o mundo, e o mundo volta ao último save **com os
diamantes ainda no inventário**. Resultado: 128 diamantes.

**A solução:** a nuvem só fica sabendo de uma mudança depois que o mundo foi salvo.
1. Toda operação mexe só no **cache em memória** (o lease garante exclusividade) e entra no
   **lote aberto**.
2. No **save do mundo** (autosave ou `save-all`), o lote aberto é **selado**: ganha número de
   sequência e é gravado no diário, dentro da pasta do mundo, no mesmo save.
3. Depois de gravado, o lote é enviado ao TCMine (assíncrono, idempotente por época + seq). Se o envio
   falhar, ele é reenviado; se o servidor cair, é reenviado no próximo boot.

Cenários de crash:

| Situação | Mundo após o crash | Nuvem | Resultado |
|---|---|---|---|
| Depositou e crashou antes do save | volta: itens no inventário | lote não selado = não existe | ✅ consistente |
| Retirou e crashou antes do save | volta: itens não estão com o jogador | não foi debitada | ✅ consistente |
| Crash depois do save, antes do envio | salvo | diário reenvia no boot | ✅ consistente |
| Logout e crash logo depois (§5.1) | inventário já salvo pelo vanilla | débitos selados no logout | ⚠️ perda possível, nunca duplicação |

### 5.1 O caso do logout
O vanilla grava o inventário do jogador (`playerdata`) **na hora do logout**, fora do ciclo de save
do mundo. Por isso, no logout:
- **Débitos** (retiradas) são selados **imediatamente**. Se o servidor crashar em seguida, o pior
  caso é perder o que foi retirado para um baú do mundo ainda não salvo. É perda, nunca duplicação.
- **Créditos** (depósitos) esperam um **save forçado**, coalescido: no máximo 1 a cada N segundos,
  mesmo que vários jogadores saiam juntos. Só depois disso o lease é liberado.
- Retirar um item que tinha sido depositado ainda no lote aberto **anula** o crédito pendente antes
  de virar débito (netting). Isso evita o lote mandar "−64" de algo que o banco ainda não viu
  "+64". É regra pura, com teste JUnit.

### 5.2 Mundo que volta no tempo (backup restaurado, cópia manual)
- O `checkpoint.json` (dentro do mundo) guarda o `worldId` (aleatório, criado uma vez) e, por
  jogador, a última época/seq **selada**.
- No `hello` de boot, o mod envia o checkpoint. Se o TCMine já aplicou lotes **além** do
  checkpoint, o mundo voltou no tempo. O TCMine abre um **incidente de rollback** e o mod deixa a
  nuvem **somente leitura** naquele servidor até o dono decidir no painel:
  - **Reverter**: estorna os lotes posteriores ao checkpoint (recomendado, com prévia do efeito).
  - **Aceitar**: mantém os saldos, assumindo a duplicação.
- Como o TCMine é quem faz backup e restauração, o fluxo de restauração dele já abre esse incidente
  sozinho, com prévia.

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

Cada lote leva também os **saldos esperados** dos itens que tocou. Se o TCMine calcular outra coisa,
há divergência: o lote vai para a quarentena e o canal é congelado. Isso detecta bug ou trapaça cedo.

## 9. Fases

| Fase | Entrega | Critério de pronto |
|---|---|---|
| **0. Spike de riscos** (1–2 dias) | Provas mínimas: (a) a ordem do evento de save permite selar o lote dentro do mesmo save; (b) montar um `MEStorage` próprio numa grade AE2; (c) decodificar item de mod ausente dá erro tratável, não crash | três respostas documentadas em `docs/DECISOES.md` do mod |
| **1. Núcleo puro** | `cloud/` + `cloud/journal/` + `TransferGuard` (parte pura) com JUnit: netting, sequência, época, lote idempotente, cotas, crash simulado | testes verdes |
| **2. Mod com `FileCloudBackend`** | Bloco, AE2, tela (grade de itens extraída para o `tccore`), filtro compatível/incompatível, lease simulado | autor testa em jogo sem TCMine |
| **3. TCMine: tabelas + API** | Ver documento do TCMine, fases A–B | testes de contrato no TCMine |
| **4. Integração real** | `HttpCloudBackend`, chave por variável de ambiente, `hello`/checkpoint | dois servidores do mesmo dono trocando itens |
| **5. Painel** | Políticas, suspeitos, quarentena, incidentes, auditoria (fases C–D do TCMine) | dono resolve tudo sem SQL |
| **6. Testes de caos** | `kill -9` no meio do jogo, logout + kill, TCMine fora do ar, dois servidores ao mesmo tempo, restaurar backup, cópia manual de mundo | nenhum cenário duplica; perdas listadas e reversíveis pelo painel |
| Depois | Fluidos/químicos, canal compartilhado com amigos, ver canais no launcher | — |

## 10. Riscos em aberto
- **Ordem do save (fase 0):** se o evento não permitir selar dentro do mesmo save, a alternativa é
  selar no evento do save anterior ao `DimensionDataStorage`. Isso precisa ser medido, não deduzido.
- **Crash durante o save:** um mundo salvo pela metade é raro e já corrompe o próprio mundo. Fica
  fora do escopo e entra como risco aceito.
- **Heurística de UUID com falso positivo:** itens com dono "soulbound" vão para a fila de suspeitos.
  Custo: um clique do dono por item, uma vez.
- **Monetização:** vender capacidade de armazenamento pode conflitar com as regras de uso comercial
  da Mojang. Verificar antes de criar planos pagos.
