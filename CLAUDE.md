# TC Colony Bridge — instruções para o Claude Code

Addon **NeoForge 1.21.1** (Java 21) que liga uma rede ME do **Applied Energistics 2** ao sistema de
pedidos do **MineColonies**. Alvo: modpack **ATM10**. Autor: Jocian (tiny-core.org).

Estado: MVP funcional e testado — o bloco lê os pedidos em aberto da colônia, entrega da rede ME
para os racks do armazém e agenda autocrafting para pedidos de item exato.

Roadmap e ideias de melhoria: `docs/ROADMAP.md` (ler **só** quando a tarefa for sobre planejamento
ou uma feature nova). Guia de modelos: `docs/GUIA-BLOCKBENCH.md`.

---

## 1. Sobre o autor (importante para o estilo de resposta)

- Programa em C#, JavaScript e Lua. **Sabe pouco de Java e de modding NeoForge.**
- Idioma: **português do Brasil** em respostas, comentários, Javadoc e documentação.
  Identificadores no código (classes, métodos, variáveis) ficam em **inglês**.
- Quer postura analítica: se um pedido dele for inviável, arriscado ou fugir do objetivo, **diga isso,
  explique o motivo e proponha alternativa** antes de implementar. Não concorde por concordar.
- Quando usar um conceito de Java que não existe (ou é diferente) em C#, explique numa linha,
  comparando com C# quando ajudar. Ex.: "`record` em Java ≈ `record` em C#: classe imutável de dados".

## 2. Comandos

```bash
./gradlew build        # gera build/libs/tccolonybridge-<versão>.jar
./gradlew runClient    # cliente de desenvolvimento
./gradlew runServer    # servidor de desenvolvimento
```
O teste real é feito pelo autor no ATM10. Depois de mudanças em lógica de jogo, diga **o que ele
deve testar em jogo** (passos curtos e o resultado esperado).

Dependências: a API do AE2 vem do Maven Central; MineColonies, Structurize, BlockUI, Domum
Ornamentum, AE2 completo e GuideMe vêm de `libs/` (jars copiados do ATM10). Versões em
`gradle.properties` devem casar com as do ATM10.

## 3. Mapa do projeto

```
src/main/java/org/tinycore/colonybridge/
├── ColonyBridgeMod.java        # entrada do mod: registros, config, capabilities
├── Config.java                 # config de servidor (ModConfigSpec)
├── registry/ModRegistries.java # blocos, itens, block entities (DeferredRegister)
├── block/                      # bloco da ponte + block entity (nó da grid AE2)
├── logic/                      # ciclo de pedidos, entrega, crafting, status
└── integration/                # ÚNICO lugar que toca na API do MineColonies
src/main/resources/             # assets (modelos, texturas, lang) e data (loot, tags)
src/main/templates/META-INF/    # neoforge.mods.toml (com placeholders do Gradle)
```

Fluxo de um ciclo (a cada `cycleTicks`): `ColonyBridgeBlockEntity.serverTick()` →
`BridgeLogic.runCycle()` → `ColonyAccess.openRequests()` → entrega (`deliver`) ou
`CraftingTracker.tryStart()` → `ColonyAccess.reassign()`.

## 4. Regras de arquitetura (sem monolitos)

- **Uma responsabilidade por classe.** Alvo < 250 linhas por arquivo; acima de ~400, proponha dividir.
- **Isolamento de APIs externas:** MineColonies só em `integration/`. Ao crescer o uso do AE2 fora do
  nó da grid, crie `integration/ae2/`. Assim uma atualização de mod quebra um pacote só.
- **Lógica de jogo sem dependência de cliente.** Nada de `net.minecraft.client.*` fora de um pacote
  `client/`, registrado só no lado cliente (`@EventBusSubscriber(value = Dist.CLIENT)`), senão o
  servidor dedicado crasha.
- Pacotes novos previstos: `client/` (renderização, telas), `network/` (pacotes), `menu/` (containers
  de GUI), `multiblock/` (formação e validação de estruturas), `datagen/`.
- Registros sempre via `DeferredRegister` em `registry/` (um arquivo por tipo quando crescer:
  `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenus`, `ModCreativeTabs`).
- Preferir composição a herança profunda. Nada de classes "Utils" genéricas que viram depósito.

## 5. Boas práticas de código

- Nomes claros; métodos curtos; retorno antecipado em vez de `if` aninhado.
- `@Nullable` / `@NotNull` (org.jetbrains) nas fronteiras públicas.
- Nunca engolir exceção em silêncio: logar em `debug` (esperado) ou `warn` (inesperado) via
  `ColonyBridgeMod.LOG`. Nada de `printStackTrace` ou `System.out`.
- Valores ajustáveis vão para `Config`, não ficam fixos no código ("números mágicos").
- Todo texto visível ao jogador via `Component.translatable` + chaves em `en_us.json`, `pt_br.json`
  e `pt_pt.json`.
- Não usar reflection nem mixins em classes internas do AE2/MineColonies sem discutir antes: é o
  que mais quebra com atualizações do ATM10.

## 6. Segurança (servidor multiplayer)

- **Todo pacote vindo do cliente é hostil:** validar no servidor posição (chunk carregado, distância
  do jogador ≤ 8 blocos), permissões e valores (limites, listas). O cliente nunca decide o que é
  extraído, craftado ou entregue.
- Checar permissão da colônia antes de ligar a ponte a ela (o jogador que colocou deve ter
  permissão na colônia) e respeitar a segurança do AE2 (`setOwningPlayer` já é chamado).
- Proteger contra duplicação de itens: extração sempre `SIMULATE` antes de `MODULATE`, e o que não
  couber volta para a rede. Qualquer mudança em `deliver()` precisa manter essa garantia.
- Limitar tamanho de dados sincronizados para o cliente (listas, strings) para evitar pacotes
  gigantes.

## 7. Performance

- Nada pesado a cada tick: trabalho acontece a cada `cycleTicks` e com teto
  (`maxRequestsPerCycle`, `maxCraftPerRequest`).
- Não varrer o mundo nem carregar chunks. Checar `level.isLoaded(pos)` antes de acessar blocos.
- Usar `getCachedInventory()` do AE2 para leitura; evitar alocar objetos em laços quentes.
- Sincronização cliente↔servidor só quando o dado **mudar**, com limite de frequência.
- Renderização (monitores, telas): nada de lógica de jogo no render; cachear geometria/texturas e
  só reconstruir quando os dados mudarem.
- Futuros trabalhos pesados (ex.: estatísticas) devem ser incrementais, nunca recalcular tudo por ciclo.

## 8. Documentação (obrigatória, em PT-BR)

- Toda classe tem Javadoc explicando **o que faz, por que existe e como se conecta** com as outras.
- Métodos públicos e trechos não óbvios têm comentário explicando a intenção, não repetindo o código.
- Explicar conceitos de Java/NeoForge na primeira vez que aparecem no arquivo (ex.: capabilities,
  block entity, ticker, `DeferredRegister`, generics como `IToken<?>`).
- Os comentários atuais estão parcialmente em português europeu — ao editar um arquivo, converter
  para PT-BR.
- Ao criar/alterar uma feature, atualizar o `README.md` (uso) e, se for decisão de arquitetura,
  registrar em `docs/DECISOES.md` (data, decisão, motivo, alternativas descartadas).

## 9. Economia de tokens

- **Não ler:** `build/`, `run/`, `.gradle/`, `libs/*.jar`, `gradle/wrapper/`. Já bloqueados em
  `.claude/settings.json`.
- Para entender uma API do AE2/MineColonies, procurar a assinatura exata (grep no código-fonte
  clonado ou no jar descompilado **só da classe necessária**), não ler pacotes inteiros.
- Usar `grep`/busca antes de abrir arquivos; abrir só as linhas relevantes de arquivos grandes.
- Editar com trocas pontuais; não reescrever arquivos inteiros para mudar poucas linhas.
- Respostas objetivas: resumo do que mudou + o que testar. Sem repetir código que já está no
  arquivo nem colar logs longos.
- Tarefas grandes: primeiro um plano curto (arquivos afetados, riscos) e esperar o "ok" do autor.

## 10. Git

- Commits pequenos e temáticos, mensagem em Ingles no formato `tipo: descrição`
  (`feat`, `fix`, `refactor`, `docs`, `chore`). Ex.: `feat: dedicated tab in creative mode`.
- Nunca commitar jars de `libs/`, `run/` nem `build/`.
