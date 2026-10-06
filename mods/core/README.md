# TC Core

Biblioteca comum dos mods **TC** (tiny-core) para NeoForge 1.21.1. Sozinho não adiciona nada ao jogo:
é um **mod separado** que os outros mods TC exigem (o jogador instala o `tccore-<versão>.jar` junto).

## O que oferece (`org.tinycore.core`)

| Pacote | Conteúdo |
|---|---|
| `client.ui` | Design system das telas: `UiColors` (cores da marca TCMine), `ScreenStyle` (janela com relevo, slots, painéis, texto que corta com "…"), `IconButton` + `SideToolbar` (barra lateral de botões com ícone, estilo terminal do AE2), `BarChart` + `Painter` (gráfico que desenha em tela ou no mundo), `ItemGrid` (grade de itens rolável com busca, ícone substituto e entradas apagadas), `GridHeightButton` (altura da grade dos terminais), `UiFormat`, `RedstoneIcons`. |
| `grid` | Regras puras das grades de itens: `ItemListing` (busca por nome ou `@mod`, ordem por quantidade/nome) `CountDiff` (só o que mudou entre duas contagens, para sincronizar) e `GridHeight` (5 linhas, 8 ou o que couber). |
| raiz | `TcCore` (entrada do mod) e `TcCoreClientConfig` (`config/tccore-client.toml`: preferências de tela comuns, hoje a altura da grade). |
| `menu` | Ghost slots: `AbstractGhostMenu` (clique copia 1 unidade, nunca move itens — proteção contra duplicação), `GhostContainer`, `GhostSlot`, `TabSlot` (slot visível só numa aba), `JoinedList`. |
| `block` | `RedstoneMode` (ignorar / com sinal / sem sinal), com traduções próprias em `assets/tccore/lang`. |
| `stats` | `MetricRing` (ring buffer de contadores por enum de métricas) e `MetricSeries` (janela de tempo de jogo, NBT, gráfico). |

## Regras

- O core **não depende** de nenhum mod externo nem de mod TC. Um mod TC depende do core, nunca o contrário.
- Mudou algo aqui? Rode `./gradlew build` na raiz: compila e testa todos os mods contra a mudança.
- Testes: `./gradlew :core:test --rerun`.
