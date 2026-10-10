# Changelog

Mudanças por versão de cada mod TC. A versão de cada mod fica no `gradle.properties` dele. "Não lançado" = já no
`main`, ainda sem release (falta a arte dos blocos).

## Não lançado

### TC Core 0.3.0
- Ícones 16×16 nas cores da marca para os botões das telas (`TcIcons`, `IconButton.sprite`), gerados por
  `tools/icons/generate_icons.py`; o botão de altura da grade usa ícones.

### TC Colony Bridge 0.5.0 (exige TC Core 0.3.0)
- **Arte dos blocos e itens:** Ponte, Abastecedor, Terminal do Armazém, Chunk Loader e Pattern Encoder com modelo
  próprio (pedra, madeira e metal em volta de um núcleo de glifos), núcleo, lâmpadas e tela que mudam por estado, e
  o conector do AE2 na face de baixo; Monitor, Tablet e Cartão de Ligação com textura própria. Os blocos com modelo
  novo deixaram de ser cubo cheio (`noOcclusion`).
- Botões de ajuda e de ordem com ícones no lugar de letras.

### TC Cloud Storage 0.4.0 (exige TC Core 0.3.0)
- **Nuvem local:** sem o TCMine, os canais ficam num arquivo deste computador (`localCloud`, ligada por padrão;
  `localCloudFile` relativo = por instância, absoluto = compartilhada entre instâncias e modpacks). Uma instância por
  vez (trava de arquivo); servidor dedicado exige `online-mode`. Substitui o backend de desenvolvimento
  (`devFileBackend`/`dev*` viraram `local*`; `tccloud-dev-backend.json` é renomeado sozinho).
- **AE2 opcional:** sem ele, o Cloud Link funciona só pela tela (guardar, retirar, canais, cota) e ganha uma receita
  vanilla; com ele, tudo como antes. O Link não gasta mais energia da rede (`linkIdlePower` removido).
- Modo de rede **só retirar**: a rede AE2 vê e tira itens da nuvem, mas não guarda nada nela.
- Botões com ícones (modo da rede, ordem, incompatíveis, prioridade, ajuda); o tooltip do modo explica o que a rede
  pode fazer e avisa que quem acessa a rede usa o canal enquanto o dono está online.
- **Arte do TC Cloud Link:** modelo de dois blocos de altura (emissor embaixo, nuvem em cima, chuva de dados entre
  os dois), com versão ligada e desligada (propriedade `active` do bloco). Aceso = rede AE2 ligada e nuvem do dono
  aberta neste servidor. O bloco ocupa de fato dois blocos (metade de cima invisível, como a porta): precisa do espaço
  de cima livre, abre a tela clicando em qualquer parte e quebra inteiro por qualquer metade.

### TC Core 0.2.0
- Altura da grade dos terminais (5 linhas, 8 ou a janela toda), salva em `config/tccore-client.toml`, com botão
  para a barra lateral (`GridHeightButton`).
- Busca por `#tag` nas grades de itens (além de nome e `@mod`).

### TC Colony Bridge 0.4.0 (exige TC Core 0.2.0)
- **Abastecedor com auto-craft:** linha "manter" que continua abaixo da meta, com a rede ME sem o item, pede o
  craft ao AE2 (botão por bloco, `supplyCrafting` na config).
- **TC Pattern Encoder:** cria padrões de crafting para os pedidos da colônia que a rede ME não sabe craftar; Blank
  Patterns do slot ou da rede ME; também pela aba do tablet.
- Receitas mais caras para todos os blocos e itens; Monitor, Abastecedor, Terminal e Cartão de Ligação ganharam
  receita.
- Terminal do Armazém: grade começa com 5 linhas, botão de altura e busca por `#tag`.
- Integração com o JEI sem APIs marcadas para remoção.
- Protocolo de rede 13: cliente e servidor precisam da mesma versão.

### TC Cloud Storage 0.3.0 (exige TC Core 0.2.0)
- **Vários canais:** cada Cloud Link escolhe o seu canal; criar e renomear pela tela (nuvem local completa; no
  TCMine, escolher já funciona e criar/renomear aguardam os endpoints `POST /channels` e `/channels/rename`).
- Barra de cota do canal no topo da tela do Cloud Link (itens e tipos, com cores perto do limite); cota opcional
  na nuvem local (`localQuotaMaxTotal`, `localQuotaMaxTypes`).
- Receita do TC Cloud Link.
- Grade começa com 5 linhas, botão de altura e busca por `#tag`.
- Fala com a nuvem do TCMine (fase 4): chave entregue pelo TCMine, política de itens, relatórios.
