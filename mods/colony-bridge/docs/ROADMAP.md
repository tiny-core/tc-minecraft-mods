# Roadmap — TC Colony Bridge

Ordem pensada para cada fase entregar algo jogável e reduzir risco antes da parte mais difícil
(os monitores). Cada fase termina com teste no ATM10.

---

## Fase 2 — Identidade do mod

**Objetivo:** o mod deixa de parecer protótipo.

- Modelo e textura próprios da ponte (Blockbench — ver `GUIA-BLOCKBENCH.md`).
  Sugestão visual: moldura escura estilo AE2 + detalhe com cores do MineColonies, com
  "estado" visível no bloco (blockstate `status`: offline / idle / working → textura muda).
- **Feito (código):** aba própria no criativo, tooltip, receita (` E `/`IRI`/` E ` — processador de
  engenharia, interface ME, rack do MineColonies) e blockstate `status` com 4 estados
  (offline / error / idle / working). **Falta:** modelo e texturas no Blockbench e conferir a receita
  no JEI/EMI do ATM10 (KubeJS pode ter alterado itens).
- **Aba própria no modo criativo** (`registry/ModCreativeTabs.java`, `DeferredRegister` de
  `Registries.CREATIVE_MODE_TAB`), com ícone da ponte. Remover o item da aba "Functional Blocks".
- Receita de craft usando itens do AE2 e do MineColonies. Confirmar os IDs no ATM10 com JEI/EMI
  antes de escrever o JSON; considerar receita configurável via datapack (já é, por ser JSON).
- Tooltip no item explicando uso (texto traduzível).

## Fase 3 — Interface da ponte (GUI)

> **3a feita:** tela com estado, colônia, lista de pedidos com resultado, crafting on/off e modo redstone.
> **3b feita:** filtro de itens (permitir/bloquear, comparação exata ou por item, ghost slots, arrastar do JEI).

**Objetivo:** configurar e ver o estado sem olhar logs.

- Menu (container) + tela: estado, colônia ligada, pedidos pendentes, crafts em andamento.
- Configuração **por bloco** (salva em NBT): ligar/desligar crafting, filtro de itens, controle por
  redstone.
- Pacotes em `network/` usando `CustomPacketPayload` + `RegisterPayloadHandlersEvent`.
  Validação completa no servidor (ver regras de segurança no `CLAUDE.md`).

## Fase 4 — Estatísticas

> **Feita:** por ponte, em `stats/` — contadores em ring buffer (288 × 5 min), ranking aproximado
> (24 grupos × 16 itens), NBT do block entity, aba "Estatísticas" e `BarChart` em `client/ui/`.
> "Crafts concluídos" medido desde a Fase 6b (com `ICraftingRequester`); aparece no monitor com 4+ blocos de largura.

**Objetivo:** dados que depois alimentam os monitores.

- Coleta no servidor: itens entregues, crafts feitos/falhos, pedidos atendidos por hora, top itens.
- Guardar em *ring buffer* (janela fixa, ex.: últimas 24h em blocos de 5 min) → memória constante.
- Persistência em NBT do block entity (ou `SavedData` se for por colônia).

## Fase 5 — Monitor multibloco (a feature grande)

### O que se quer
Parede de blocos de monitor que forma uma tela única (como o monitor do CC:Tweaked), mas sem a
limitação de grade de caracteres: gráficos, ícones de itens, barras, texto em qualquer tamanho,
visual moderno.

### Análise honesta
É de longe a parte mais complexa do projeto: envolve renderização no cliente (OpenGL via API do
Minecraft), formação de multibloco, sincronização de rede e compatibilidade com shaders.
Recomendo só começar depois das fases 3 e 4, porque elas criam os dados e a infraestrutura de
pacotes que o monitor precisa.

### Arquitetura proposta
- **Formação:** blocos `monitor` adjacentes, mesma direção, formando retângulo (limite configurável,
  ex.: 8×6). Um bloco vira **mestre** (canto inferior esquerdo) e guarda dados/estado; os outros só
  apontam para ele. Revalidar ao colocar/quebrar um bloco, nunca por tick.
- **Fonte de dados:** o monitor é ligado a uma ponte (adjacência, ou item "cartão de ligação" com
  a posição gravada, validado no servidor por distância/dimensão).
- **Sincronização:** o servidor monta um *snapshot* compacto (só números e IDs) e envia aos jogadores
  próximos **só quando muda**, no máximo 1×/s. O cliente nunca pede dados arbitrários.
- **Renderização — duas opções:**
  1. **Desenhar direto no mundo com `BlockEntityRenderer`** (quads + `Font`), recomendada para
     começar: nítida em qualquer tamanho, sem framebuffer extra, menos problemas com shaders.
     Gráficos viram retângulos/linhas; ícones via `ItemRenderer`.
  2. **Render-to-texture** (desenhar a UI num framebuffer e aplicar como textura): permite reutilizar
     código de GUI e efeitos mais ricos, mas custa memória de GPU, é mais complexo e costuma dar
     problemas com mods de shader (Iris). Deixar como evolução, se a opção 1 não bastar.
- **Brilho:** tela renderizada com luz máxima (`LightTexture.FULL_BRIGHT`) para parecer um display.
- **Culling:** não renderizar se o jogador estiver longe (ex.: > 32 blocos) ou atrás da tela.

### Interface "moderna e bonita"
- Criar um mini *design system* em `client/ui/`: tokens de cor, espaçamentos, tipografia, e
  componentes (card, gráfico de linha, barra, lista de itens). Os monitores montam telas a partir
  desses componentes — nada de coordenadas soltas espalhadas.
- Paleta sugerida: reaproveitar a do TCMine (laranja `#F97316`, ciano `#22B8E8`, cinzas escuros) para
  manter identidade visual entre os projetos.
- Layout responsivo ao tamanho do multibloco (2×2 mostra um resumo; 6×4 mostra painel completo).
- Animações leves (transição de valores, gráfico deslizando) interpoladas no cliente, sem pacotes extras.

### Divisão em etapas

> **5.1 feita:** bloco de monitor, formação de retângulo (`multiblock/MonitorFormation`, mestre no canto
> inferior esquerdo), `MonitorRenderer` + `MonitorCanvas` (desenho no mundo, luz máxima, culling por
> distância e por trás). Modelo ainda é placeholder.
>
> **5.2 feita:** `LinkCardItem` (validação de dimensão, distância, permissão), ligação guardada no mestre e
> preservada quando a tela muda de forma, `MonitorData` sincronizado 1×/s só quando muda, `MonitorPanels`
> com layout por tamanho, `BarChart` desenhando via `Painter` (interface e mundo).
>
> **5.3 feita:** lista de pedidos paginada, faixa de itens mais entregues (4+ blocos de altura) e
> animação dos números e barras (`MonitorAnimator`, suavização por tempo real).
>
1. Monitor 1×1 mostrando texto fixo via BER (valida renderização).
2. Formação do multibloco + tela única.
3. Pacote de snapshot + dados reais da ponte.
4. Componentes de UI e gráficos.
5. Polimento visual e configurações do monitor.

## Fase 6 — Crafting avançado

> **6a feita:** crafting para pedidos por **tag/ferramenta/comida** (`logic/crafting/CraftCandidates`):
> percorre os craftáveis do AE2, testa `deliverable.matches` e ordena por custo estimado (`CraftCost`),
> custo invertido ou lista da config; opção "só vanilla". Falha de material → tenta o próximo candidato.
> **Feito:** preferência por ponte na tela (abas Geral, Preferidos e Mods), com a config do servidor como padrão.

- ~~Trocar "craft sem requester" por `ICraftingRequester`~~ — **feito (6b):** `CraftLinks` + `CraftDelivery`,
  vínculos salvos no NBT, métrica "crafts concluídos" no monitor.

---

## Dicas de melhoria no código atual (prioridade alta → baixa)

1. ~~Checar permissão da colônia~~ — **feito** (colocação + checagem a cada ciclo).
2. ~~Persistir `lastDelivery`~~ — **feito** via `DeliveryLedger` (`SavedData`).
3. ~~Várias pontes na mesma colônia~~ — **feito**: registro compartilhado; crafts reservados por ponte
   e renovados enquanto estão ativos.
4. ~~Indexar o estoque~~ — **feito** de forma mais simples: `AEItemKey.getReadOnlyStack()` (cache do AE2)
   elimina a alocação por item. Se o custo de `matches()` pesar em redes enormes, aí sim indexar.
5. ~~Simulação de inserção mais precisa~~ — **feito** em `RackDelivery.capacity()` (soma por slot).
6. Testes automáticos — **feito o que dá sem o jogo:** JUnit das regras puras (`./gradlew test`: 62 no
   Colony Bridge, 13 no core). **Fora de escopo por decisão do autor (2026-09-29):** GitHub Actions e
   GameTests; entrega e craft reais são testados à mão no ATM10.
7. ~~Separar `ModRegistries` em arquivos por tipo~~ — **feito** (`ModBlocks`, `ModItems`, `ModBlockEntities`).
8. Publicação futura (CurseForge/Modrinth): definir licença final, página em PT/EN e ícone.

---

## Fase 7 — Abastecedor da Colônia (feito)

Bloco de mão dupla entre o armazém e a rede ME, com base compartilhada (`AbstractBridgeBlockEntity`)
e menu base com os ghost slots (`AbstractGhostMenu`).

- **Feito:** manter estoque no armazém, excedente do armazém para o ME, tela com as duas listas,
  quantidade por linha, modo de redstone e guarda contra vaivém (não tira o que está em pedido aberto).
- **Pendente:** alimentar craft do AE2 com o que falta usando os materiais do armazém
  (depende de confirmar se o plano de craft do AE2 expõe a lista do que faltou).
- **Feito (7b):** estatísticas do Abastecedor (repostos / devolvidos) e painel próprio nos monitores
  (cartão de ligação aceita Ponte e Abastecedor via `MonitorSource`).

---

## Workspace e TC Core (feito em 2026-09-29)

O mod passou a morar em `tc_minecraft_mods/colony-bridge/` (depois `mods/colony-bridge/`), ao lado do **TC Core** (biblioteca comum,
mod separado). Próximo candidato a ir para o core: a infraestrutura dos monitores, quando o mod de
reatores do Mekanism precisar dela.

---

# Próximas fases (proposta de 2026-09-30)

Ordem recomendada. A **8** vem antes das listas porque o tablet e o chunk loader dependem dela.
Nenhuma fase começa sem o plano curto aprovado pelo autor.

## Fase 8 — Um bloco de cada tipo por colônia

> **Feita (2026-09-30):** `ColonyBlockRegistry` + `ColonySlots` + `ColonySlotRule` (testada); recusa na
> colocação, estado "Duplicado na colônia", vaga solta no `onRemove`, registro velho descartado.

**Objetivo:** Ponte, Abastecedor e Terminal passam a ser **únicos por colônia** (o Chunk Loader da
Fase 11 e o Pattern Encoder da Fase 12 também). A regra atual (uma Ponte por rede ME, Terminal exige a Ponte na mesma rede) continua.

- Registro por colônia em `SavedData` (como o `DeliveryLedger`): `colonyKey + tipo → dimensão + posição`.
- Colocação: recusar com mensagem se a colônia já tem um bloco daquele tipo (checar no servidor).
- Segunda cópia que exista mesmo assim (colônia mudou de borda, mundo editado, registro perdido):
  fica em estado `DUPLICATE` e não trabalha. Entrada velha (bloco quebrado sem evento) é descartada
  quando a posição está carregada e não tem mais o bloco.
- **Muda uma decisão antiga:** hoje várias Pontes por colônia são permitidas (dica 3). Registrar em
  `DECISOES.md`. O `DeliveryLedger` fica (também guarda as entregas); só a reserva entre pontes perde uso.

## Fase 9 — Listas de itens e tags (Abastecedor e filtros)

**Objetivo:** trocar as grades de ghost slots por **listas de linhas**, como o Mekanism: cada linha tem
um ghost slot, uma caixa de texto e a quantidade.

Etapas: **1 feita** (modelo `logic/target/`, parser, regras por lista, migração, `listMaxLines`, testes) ·
2 lógica do Abastecedor e do filtro · 3 widget de lista, telas, pacotes e JEI · 4 monitores · 5 documentação.

Decidido com o autor (2026-09-30): até 32 linhas por lista (config, teto 64); `@mod` só no Excedente e no
filtro; aba "Preferidos" continua em grade; botão **"Adicionar linha" em cima da lista**; ícone é um slot
virtual (não um `Slot` do menu) e o clique manda um pacote — o servidor lê o item do cursor ele mesmo;
clique direito no ícone alterna entre o item e as tags dele. Quantidade 0 = linha desligada (como hoje).

- Linha = `alvo` + `quantidade` (ou "tudo"). Alvo aceita:
  - **item**: soltar item/JEI no slot preenche a caixa com o id (`minecraft:iron_ingot`), ou digitar o id;
  - **tag**: digitar `#c:ingots/iron`; o slot fica alternando os ícones dos itens da tag (só no cliente);
  - **mod** (só nos filtros): `@mekanism`, mesma sintaxe da busca do Terminal.
- Validação no servidor: id/tag existe no registro, tamanho do texto limitado, número máximo de linhas
  (config), quantidade limitada. Texto inválido: linha fica vermelha e é ignorada.
- **Abastecedor:** duas abas, "Manter no armazém" e "Excedente para o ME", cada uma com sua lista.
  Regras a decidir para tag: "manter 64 de `#c:ingots/iron`" conta a soma dos itens da tag no armazém e
  repõe com o item que a rede ME tiver mais; o excedente devolve primeiro o item com mais sobra.
  "Tudo" só faz sentido no excedente (= meta 0); em "manter" esvaziaria a rede ME no armazém.
- **Filtros da Ponte:** a mesma lista (sem quantidade), aceitando item, tag e `@mod`.
- Tags resolvidas uma vez e cacheadas (invalidar no reload de tags/datapack), nada de resolver por item
  a cada ciclo.
- Migração: `StockList` (layout 2) e `ItemFilter` (18 slots) viram listas no primeiro load.
- **Monitores:** `SupplyContent`/`SupplyPanel` passam a mostrar linhas de tag (nome da tag, ícone
  alternando) e o número de linhas deixa de ser fixo; limitar o que vai no pacote.
- Regra pura (parser de alvo, soma por tag, escolha do item a repor) com JUnit.

## Fase 10 — TC Colony Tablet

**Objetivo:** extensão portátil dos blocos da colônia, com **as mesmas funcionalidades** das telas deles.

Decidido com o autor (2026-09-30):
- **Abas:** Terminal (principal), Ponte, Abastecedor, Chunk Loader e abas de **painéis** com as
  informações que os monitores mostram (Ponte, Abastecedor). Aba de bloco que não existe no mundo, está
  em chunk descarregado ou em outra dimensão fica desativada (com dica).
- **Chunk Loader no tablet:** chunks carregados, energia gasta por tick e botão de ligar/desligar.
- **Bateria** própria (FE). **Carrega num slot novo da Ponte**, usando energia da rede ME.
- **Alcance ilimitado** por enquanto (mesma dimensão ou não: a decidir no plano). Addons de alcance
  ficam para o futuro.
- **Ligação (escolha do Claude):** shift + clique direito na Ponte grava a **colônia** (id + dimensão)
  no tablet, não a posição. Os blocos são achados pelo registro da Fase 8, então trocar a Ponte de
  lugar não quebra o tablet. Colocar o tablet no slot de carga também liga.

Como fazer:
- Reaproveitar os menus e telas dos blocos: separar "de onde vêm os dados" (bloco ou tablet) do menu,
  para não duplicar tela. Hoje os menus são de bloco (`stillValid` por distância): criar a variante
  "aberta pelo tablet" (válida enquanto o tablet está na mão, tem bateria e a ligação vale).
- Painéis dos monitores: desenhar os mesmos componentes do `MonitorPanels` numa tela de GUI (o `Painter`
  já desenha em interface e no mundo). Dados pelo mesmo snapshot, só enquanto a aba está aberta.
- Segurança: a cada pacote, o servidor confere tablet na mão, bateria, ligação, permissão na colônia e
  bloco carregado. **Nunca carregar chunk** para abrir a tela.
- Gasto de bateria: por abertura e/ou por ação (config).

## Fase 11 — TC Colony Chunk Loader

**Objetivo:** manter carregados os chunks reivindicados pela colônia, pagando energia por chunk.

- API oficial do NeoForge: `TicketController` (`RegisterTicketControllersEvent`), com callback de
  validação ao reiniciar o servidor. Sem mixin.
- Chunks = os reivindicados pela colônia (via `integration/`), atualizados quando a colônia cresce.
- Energia AE da rede ME por chunk por tick (config); sem energia → solta os chunks.
- Liga/desliga na tela e por redstone; um por colônia (Fase 8).
- Config de servidor: liga/desliga o bloco no servidor todo, máximo de chunks por loader, custo por
  chunk, opção "só com um membro da colônia online".
- Cuidado: o ATM10 tem FTB Chunks com limite de chunks forçados por jogador; este bloco passa por
  cima desse limite. Por isso o limite próprio e a opção do admin.
- Monitor e tablet mostram chunks carregados e consumo; tablet também liga/desliga.

## Fase 12 — TC Pattern Encoder (codificador de padrões)

**Objetivo:** bloco que pega os pedidos da colônia que **não têm craft no AE2** e gera os padrões.

- Tela: slot de **Blank Pattern** (do AE2), lista dos pedidos sem padrão com a receita encontrada e botão
  "criar padrões", que consome 1 Blank Pattern por padrão. Slots de saída para o jogador levar os padrões
  ao Pattern Provider / Molecular Assembler.
- **Viabilidade confirmada na API do AE2** (`PatternDetailsHelper.encodeCraftingPattern`, e também
  `encodeStonecuttingPattern` / `encodeSmithingTablePattern` / `encodeProcessingPattern`). Sem mixin.
- Receita: procurar no `RecipeManager` receitas de bancada (`RecipeType.CRAFTING`) que produzam o item;
  com várias, a mais barata (reaproveitar `CraftCost`). Pedido por tag: um candidato, como na Fase 6a.
- **Excluir:** itens do Domum Ornamentum e da bancada do arquiteto (framed, blocos com textura de
  material) — namespace `domum_ornamentum` e itens com componente de textura. Receitas de bancadas
  especiais do MineColonies não são `CRAFTING`, então já ficam de fora.
- Mesmas restrições dos outros blocos: um por colônia (Fase 8), na rede ME, permissão da colônia.
- Riscos: receitas com NBT/componentes (ferramentas encantadas, poções) e receitas de mods com tipo
  próprio não viram padrão de bancada; ficam listadas como "sem receita suportada".
- Fase 1 só bancada; processamento (fornalha) fica para depois.

## Sugestões ainda sem fase

- **Abastecedor com auto-craft:** "manter" pede craft ao AE2 do que falta (pendência da Fase 7).
- **Lista de compras:** aba na Ponte/Tablet com o que a colônia pede e a rede não tem nem sabe craftar.
- **Copiar configuração** entre blocos (como o Memory Card do AE2) ou exportar/importar lista como texto.
- **Autocompletar** na caixa de texto: ids e tags do item colocado no slot (clique alterna as tags dele).
- Busca do Terminal com `#tag` além de `@mod`.
- Fase 2 pendente: modelos e texturas no Blockbench.
