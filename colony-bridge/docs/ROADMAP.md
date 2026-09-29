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
6. Testes automáticos — **feito em parte:** JUnit das regras sem jogo (`./gradlew test`, 37 testes).
   **Falta:** GitHub Actions (exige obter os jars do MineColonies fora do git, ex.: Maven da LDTTeam) e
   GameTests em jogo para entrega/craft (pesado: sobe MineColonies e AE2).
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

O mod passou a morar em `tc_minecraft_mods/colony-bridge/`, ao lado do **TC Core** (biblioteca comum,
mod separado). Próximo candidato a ir para o core: a infraestrutura dos monitores, quando o mod de
reatores do Mekanism precisar dela.
