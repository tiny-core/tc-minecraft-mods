# TC Minecraft Mods — instruções gerais para o Claude Code

Workspace dos mods **TC** (tiny-core) para **NeoForge 1.21.1** (Java 21), alvo o modpack **ATM10**.
Autor: Jocian (tiny-core.org). Cada mod tem o próprio `CLAUDE.md` com o que é específico dele; este
arquivo vale para **todos**.

```
tc_minecraft_mods/
├── settings.gradle / build.gradle / gradle.properties   # build multiprojeto e versões comuns
├── gradle/tc-mod.gradle     # configuração que todo mod TC repete (NeoForge, mods.toml, testes)
├── mods/
│   ├── core/                # TC Core (tccore): biblioteca comum — mod separado em jogo
│   ├── colony-bridge/       # TC Colony Bridge (tccolonybridge): AE2 ↔ MineColonies
│   └── cloud-storage/       # TC Cloud Storage (tccloud): itens na nuvem do TCMine via AE2 (em desenvolvimento)
```
Planejado: mod de reatores do Mekanism, no mesmo estilo (ver README.md, "Criar um mod novo").

---

## 1. Sobre o autor (importante para o estilo de resposta)

- Programa em C#, JavaScript e Lua. **Sabe pouco de Java e de modding NeoForge.**
- Idioma: **português do Brasil** em respostas, comentários, Javadoc e documentação.
  Identificadores no código (classes, métodos, variáveis) ficam em **inglês**.
- Quer postura analítica: se um pedido dele for inviável, arriscado ou fugir do objetivo, **diga isso,
  explique o motivo e proponha alternativa** antes de implementar. Não concorde por concordar.
- Quando usar um conceito de Java que não existe (ou é diferente) em C#, explique numa linha,
  comparando com C# quando ajudar. Ex.: "`record` em Java ≈ `record` em C#: classe imutável de dados".
- Telas usam sempre as **cores da marca** (tokens do core, `UiColors`). "Use o mod X como referência"
  significa **estrutura/layout**, não cores.

## 2. Comandos (na raiz)

```bash
./gradlew build                      # compila e testa todos os mods; jars em build/libs/ (todos juntos)
./gradlew test --rerun               # testes JUnit de todos os mods (resultado de cada um no terminal)
./gradlew :colony-bridge:runClient   # cliente de desenvolvimento de um mod (o core roda junto)
./gradlew :colony-bridge:runServer   # servidor de desenvolvimento
```
O teste real é feito pelo autor no ATM10 (instalando o jar do mod **e** o `tccore-<versão>.jar`).
Depois de mudanças em lógica de jogo, diga **o que ele deve testar em jogo** (passos curtos e o
resultado esperado). Regras puras (sem item/mundo/rede) ganham teste JUnit em `src/test/java`; itens do
Minecraft não podem ser criados nos testes (o NeoForge exige o carregador de mods), então isole a regra
em código genérico (ex.: `CraftOrdering` no Colony Bridge).

## 3. O que vai para o core

- Vai para `mods/core/` só o que **mais de um mod usa ou vai usar** e **não depende** de mod externo
  (MineColonies, AE2, Mekanism): design system das telas, ghost slots, redstone, estatísticas.
- O core **nunca** importa nada de um mod TC. Um mod depende do core, nunca o contrário.
- Mudança no core pode quebrar todos os mods: rode `./gradlew build` na raiz (compila e testa tudo).
- Candidato à próxima leva: a infraestrutura dos monitores (multibloco, desenho no mundo) quando o mod
  de reatores precisar dela.

## 4. Regras de arquitetura (sem monolitos)

- **Uma responsabilidade por classe.** Alvo < 250 linhas por arquivo; acima de ~400, proponha dividir.
- **Isolamento de APIs externas:** cada mod fala com o mod externo num pacote `integration/` (e
  subpacotes, ex.: `integration/ae2/`). Uma atualização do mod externo quebra um pacote só.
- **Lógica de jogo sem dependência de cliente.** Nada de `net.minecraft.client.*` fora de um pacote
  `client/`, registrado só no lado cliente (`@EventBusSubscriber(value = Dist.CLIENT)` ou
  `@Mod(dist = Dist.CLIENT)`), senão o servidor dedicado crasha.
- Registros sempre via `DeferredRegister` em `registry/` (um arquivo por tipo).
- Código compartilhado dentro de um mod fica na raiz da camada e **não** importa subpacotes de bloco.
- Preferir composição a herança profunda. Nada de classes "Utils" genéricas que viram depósito.

## 5. Boas práticas de código

- Nomes claros; métodos curtos; retorno antecipado em vez de `if` aninhado.
- `@Nullable` / `@NotNull` (org.jetbrains) nas fronteiras públicas.
- Nunca engolir exceção em silêncio: logar em `debug` (esperado) ou `warn` (inesperado) no `LOG` do mod.
  Nada de `printStackTrace` ou `System.out`.
- Valores ajustáveis vão para a `Config` do mod, não ficam fixos no código ("números mágicos").
- Todo texto visível ao jogador via `Component.translatable` + chaves em `en_us.json`, `pt_br.json`
  e `pt_pt.json` (do mod dono do texto; o core tem as dele em `assets/tccore/lang`).
- Nomes de blocos/itens começam com **"TC "**.
- Não usar reflection nem mixins em classes internas de outros mods sem discutir antes: é o que mais
  quebra com atualizações do ATM10.

## 6. Segurança (servidor multiplayer)

- **Todo pacote vindo do cliente é hostil:** validar no servidor posição (chunk carregado, distância
  do jogador ≤ 8 blocos), permissões e valores (limites, listas). O cliente nunca decide o que é
  extraído, craftado ou entregue.
- Proteger contra duplicação de itens: extração sempre `SIMULATE` antes de `MODULATE`, e o que não
  couber volta para a origem.
- Limitar tamanho de dados sincronizados para o cliente (listas, strings) para evitar pacotes gigantes.

## 7. Performance

- Nada pesado a cada tick: trabalho a cada N ticks (config) e com teto por ciclo.
- Não varrer o mundo nem carregar chunks. Checar `level.isLoaded(pos)` antes de acessar blocos.
- Evitar alocar objetos em laços quentes (render, varredura de inventários).
- Sincronização cliente↔servidor só quando o dado **mudar**, com limite de frequência.
- Renderização: nada de lógica de jogo no render; cachear e só reconstruir quando os dados mudarem.
- Trabalhos pesados (ex.: estatísticas) incrementais, nunca recalcular tudo por ciclo.

## 8. Documentação (obrigatória, em PT-BR)

- Toda classe tem Javadoc explicando **o que faz, por que existe e como se conecta** com as outras.
- Métodos públicos e trechos não óbvios têm comentário explicando a intenção, não repetindo o código.
- Explicar conceitos de Java/NeoForge na primeira vez que aparecem no arquivo.
- Comentários ainda em português europeu: ao editar o arquivo, converter para PT-BR.
- Ao criar/alterar uma feature, atualizar o `README.md` do mod (uso) e, se for decisão de arquitetura,
  registrar no `docs/DECISOES.md` do mod (data, decisão, motivo, alternativas descartadas). Decisões do
  workspace (build, core) vão em `docs/DECISOES.md` da raiz.

## 9. Economia de tokens

- **Não ler:** `build/`, `run/`, `.gradle/`, `libs/*.jar`, `gradle/wrapper/` (em qualquer mod). Já
  bloqueados em `.claude/settings.json`.
- Para entender a API de outro mod, procurar a assinatura exata (grep, ou `javap` **só da classe
  necessária** no jar), não ler pacotes inteiros.
- Usar `grep`/busca antes de abrir arquivos; abrir só as linhas relevantes de arquivos grandes.
- Editar com trocas pontuais; não reescrever arquivos inteiros para mudar poucas linhas.
- Respostas objetivas: resumo do que mudou + o que testar. Sem repetir código nem colar logs longos.
- Tarefas grandes: primeiro um plano curto (arquivos afetados, riscos) e esperar o "ok" do autor.

## 10. Git

- Um repositório para o workspace inteiro. Commits pequenos e temáticos, mensagem em inglês no formato
  `tipo: descrição` (`feat`, `fix`, `refactor`, `docs`, `chore`, `test`); quando o commit é de um mod só,
  prefixe o escopo: `feat(colony-bridge): ...`, `refactor(core): ...`.
- Nunca commitar jars de `libs/`, `run/` nem `build/`.
