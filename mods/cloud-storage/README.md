# TC Cloud Storage (`tccloud`)

Armazenamento de itens na nuvem do TCMine, visível para a rede ME do Applied Energistics 2. O jogador
guarda itens num canal dele e os reencontra em outro servidor do **mesmo dono** (outro modpack, outro
mundo). Itens de mods que o servidor novo não tem aparecem como incompatíveis e não podem ser retirados.

**Estado: em desenvolvimento (fase 4 de 6).** Fala com a nuvem do TCMine; também funciona com uma "nuvem" de
teste (arquivo local). Plano e fases: `docs/planos/tc-cloud-storage.md` na raiz do workspace.

## Com o TCMine
1. No painel do TCMine, em **Nuvem de itens**, crie uma nuvem e ligue a ela os servidores que devem
   compartilhar os itens.
2. Inicie (ou reinicie) o servidor pelo TCMine: ele grava `tccloud-server.json` na pasta do servidor com a URL e
   uma chave nova. Ninguém copia chave nenhuma.
3. O servidor precisa estar em `online-mode=true` (padrão). Em modo offline a nuvem não liga: sem a verificação
   da Mojang, qualquer um entraria como outro jogador.

Servidor fora do TCMine: defina `TCMINE_CLOUD_URL` e `TCMINE_CLOUD_KEY` no ambiente do processo.

O que o dono vê e decide no painel: regras de item (e a fila de itens suspeitos que o mod recusou), lotes em
quarentena, operações em dúvida depois de uma queda, mundos que voltaram no tempo e o histórico de tudo.

## Sem o TCMine (testes)
1. Em `config/tccloud-common.toml`, ligue `devFileBackend = true` (a nuvem de teste fica em
   `tccloud-dev-backend.json`, na pasta do jogo/servidor, e é a mesma para todos os mundos).
2. Pegue o **TC Cloud Link** na aba criativa "TC Cloud Storage" e coloque-o ligado a uma rede AE2 (qualquer
   lado). Só quem colocou abre a tela.
3. Na tela: clique esquerdo tira um stack, direito tira 1; com item no cursor, esquerdo guarda tudo e direito
   guarda 1; shift-clique no inventário guarda o stack. O botão `!` mostra os itens que não podem ser usados
   neste servidor (apagados, com o motivo).
4. Barra lateral: altura da grade (5 linhas, 8 ou a janela toda, a mesma escolha do Terminal do Armazém), modo da rede AE2 (`T` só esta tela, `D` só guardar, `R` guardar e retirar) e prioridade
   (`+`/`-`).
5. **Canais:** a linha "Canal" no topo escolhe o canal deste Link (◀ ▶); `+` cria um canal (digite o nome, Enter
   confirma, Esc cancela) e já passa o Link para ele; `✎` renomeia. Cada Link mostra e monta na rede AE2 o seu
   canal; o mesmo canal só pode estar montado em um Link por servidor. Limite: o do painel do TCMine, ou
   `maxChannels` (padrão 8). Com o TCMine, criar/renomear depende de endpoints que ele ainda não tem: a tela avisa;
   escolher entre canais existentes já funciona. Na nuvem de teste, tudo funciona.
6. No topo da tela, a **barra de cota** mostra quanto do canal está ocupado (a barra segue o limite mais apertado
   entre itens e tipos; amarela a partir de 75 %, vermelha a partir de 95 %). O tooltip traz os dois números. A cota
   vem do painel do TCMine; na nuvem de teste, use `devQuotaMaxTotal` e `devQuotaMaxTypes` (0 = sem limite).
7. Quebrar o bloco não derruba itens: eles estão na nuvem. Coloque outro Link (até em outro mundo) e eles
   estão lá.

Itens recusados na entrada: com itens dentro (shulker, mochila, célula do AE2 cheia), grandes demais, na tag
`#tccloud:never_transfer` ou com sinais de guardar dados no mundo.

Receita (cara de propósito: o Link leva itens entre servidores), em `data/tccloud/recipe/cloud_link.json`:
```
RSR     R = Anel de quantum (AE2)   S = Singularidade entrelaçada (AE2)
PDP     P = Processador de Cálculo (AE2)   D = ME Drive (AE2)
RCR     C = Componente de armazenamento 64k (AE2)
```

Admin: `/tccloud checkpoint` salva o mundo com flush e torna tudo durável (usado pelo backup do TCMine).

## Requisitos (quando estiver pronto)
- NeoForge 1.21.1, AE2, TC Core.
- Servidor orquestrado pelo TCMine com a nuvem ligada (o TCMine injeta a chave). Em singleplayer ou
  servidor sem chave, a nuvem fica desligada.

## Testes
```bash
./gradlew :cloud-storage:test --rerun
```
O `CrashSimulationTest` joga milhares de partidas com crash em cada passo e falha se algum cenário
duplicar itens.
