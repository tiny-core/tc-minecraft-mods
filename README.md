# TC Minecraft Mods

Workspace dos mods **TC** (tiny-core) para **NeoForge 1.21.1**, feitos para o modpack **ATM10**.

| Pasta | Mod | O que faz |
|---|---|---|
| `core/` | **TC Core** (`tccore`) | Biblioteca comum: design system das telas, ghost slots, redstone, estatísticas. Obrigatório em jogo para os outros mods TC. |
| `colony-bridge/` | **TC Colony Bridge** (`tccolonybridge`) | Liga uma rede ME do AE2 aos pedidos do MineColonies (Ponte, Abastecedor, Monitores). |

Planejado: mod para gerenciar os reatores do Mekanism, no mesmo estilo.

## Build

Precisa só de um JDK 21+ para rodar o Gradle (o Gradle baixa sozinho o JDK 21 do projeto).

```bash
./gradlew build                      # compila e testa tudo; jars em <mod>/build/libs/
./gradlew test --rerun               # só os testes (resultado de cada um no terminal)
./gradlew :colony-bridge:runClient   # Minecraft de desenvolvimento com o mod (e o core)
```
Para jogar/testar no ATM10: copie para a pasta `mods/` o jar do mod **e** o `core/build/libs/tccore-<versão>.jar`.
Cada mod tem o seu README (uso) e `libs/` (jars do ATM10 que ele precisa para compilar, fora do git).

## VS Code

Abra **esta pasta** (a raiz) e aceite as extensões recomendadas (Java Extension Pack e Gradle).
- **Tarefas** (Terminal → Executar Tarefa; `Ctrl+Shift+B` = build): build, testes, runClient, runServer, clean
  e atualizar dependências. Funcionam no Windows e no Linux, inclusive por pasta de rede.
- **Executar/depurar**: as configurações de cada mod são geradas pelo ModDevGradle quando o VS Code importa o
  projeto (`.vscode/launch.json`, fora do git). Se não aparecerem: "Java: Clean Java Language Server Workspace".
- Formato dos arquivos (UTF-8, LF, 4 espaços) vem do `.editorconfig`.

## Criar um mod novo

1. Pasta nova na raiz (ex.: `reactor/`) com:
   - `build.gradle`: `apply from: rootProject.file('gradle/tc-mod.gradle')` + dependências do mod
     (`implementation project(':core')` e, nas execuções de dev, `mods { tccore { sourceSet(project(':core').sourceSets.main) } }`
     — ver `colony-bridge/build.gradle`);
   - `gradle.properties`: `mod_id`, `mod_name` ("TC ..."), `mod_version`, `mod_group_id`, `tccore_version_range`;
   - `src/main/templates/META-INF/neoforge.mods.toml` com a dependência `tccore`;
   - `CLAUDE.md` com o que é específico do mod (as regras gerais estão no `CLAUDE.md` da raiz).
2. Uma linha `include 'reactor'` no `settings.gradle`.

Decisões do workspace: `docs/DECISOES.md`.
