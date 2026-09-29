# TC Core

Biblioteca comum dos mods **TC** (tiny-core) para NeoForge 1.21.1. Sozinho não adiciona nada ao jogo:
é um **mod separado** que os outros mods TC exigem (o jogador instala o `tccore-<versão>.jar` junto).

## O que oferece (`org.tinycore.core`)

| Pacote | Conteúdo |
|---|---|
| `client.ui` | Design system das telas: `UiColors` (cores da marca TCMine), `ScreenStyle` (janela com relevo, slots, painéis, texto que corta com "…"), `IconButton` + `SideToolbar` (barra lateral de botões com ícone, estilo terminal do AE2), `BarChart` + `Painter` (gráfico que desenha em tela ou no mundo), `UiFormat`, `RedstoneIcons`. |
| `menu` | Ghost slots: `AbstractGhostMenu` (clique copia 1 unidade, nunca move itens — proteção contra duplicação), `GhostContainer`, `GhostSlot`, `TabSlot` (slot visível só numa aba), `JoinedList`. |
| `block` | `RedstoneMode` (ignorar / com sinal / sem sinal), com traduções próprias em `assets/tccore/lang`. |
| `stats` | `MetricRing` (ring buffer de contadores por enum de métricas) e `MetricSeries` (janela de tempo de jogo, NBT, gráfico). |

## Regras

- O core **não depende** de nenhum mod externo nem de mod TC. Um mod TC depende do core, nunca o contrário.
- Mudou algo aqui? Rode `./gradlew build` na raiz: compila e testa todos os mods contra a mudança.
- Testes: `./gradlew :core:test --rerun`.
