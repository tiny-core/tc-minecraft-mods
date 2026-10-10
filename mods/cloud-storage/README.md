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

## Sem o TCMine: nuvem local (singleplayer e servidor próprio)
Sem o TCMine, o mod usa a **nuvem local**: os canais ficam num arquivo deste computador, fora dos mundos. Guarde
itens num mundo e pegue-os em outro. Já vem ligada (`localCloud = true` em `config/tccloud-common.toml`).

- **Arquivo:** `localCloudFile`. O padrão, `tccloud-local.json`, fica na pasta do jogo (uma nuvem por instância). Com
  um caminho **absoluto** (ex.: `C:/Users/voce/tccloud-local.json`), várias instâncias e modpacks deste computador
  usam a mesma nuvem.
- **Uma instância por vez:** quem abre o arquivo primeiro fica com ele; outra instância aberta ao mesmo tempo fica
  com a nuvem desligada (aparece no log). Isso evita que uma apague as mudanças da outra.
- **Trocar de mundo:** ao sair, o canal é liberado depois de salvo. Se o jogo fechar sem salvar, o canal fica
  "em uso" por até `localLeaseTtlSeconds` (90 s) antes de abrir em outro mundo.
- **Cota opcional:** `localQuotaMaxTotal` e `localQuotaMaxTypes` (0 = sem limite).
- **Servidor dedicado sem TCMine:** também funciona (uma nuvem só para aquele servidor), mas exige `online-mode=true`.
- Quem testou versões antigas: o arquivo `tccloud-dev-backend.json` é renomeado sozinho para `tccloud-local.json`.

Como usar:
1. Faça (ou pegue na aba criativa "TC Cloud Storage") um **TC Cloud Link** e coloque-o. Com o AE2, ligue-o a uma
   rede ME (qualquer lado do bloco de baixo). Ele tem **dois blocos de altura** (precisa do espaço de cima livre) e
   acende quando a sua nuvem está aberta (e, com o AE2 num modo que usa a rede, quando a rede está ligada). Só quem
   colocou abre a tela, clicando em qualquer parte.
2. Na tela: clique esquerdo tira um stack, direito tira 1; com item no cursor, esquerdo guarda tudo e direito
   guarda 1; shift-clique no inventário guarda o stack. O botão de aviso (triângulo) mostra os itens que não podem ser usados
   neste servidor (apagados, com o motivo).
3. Barra lateral: altura da grade (5 linhas, 8 ou a janela toda, a mesma escolha do Terminal do Armazém), e, com o AE2, modo da rede e prioridade
   (setas). Os botões são ícones; o tooltip diz o que cada um faz. Modos da rede AE2 (o botão alterna nesta ordem):
   - **só esta tela** (cadeado): a rede não vê o canal;
   - **só guardar** (seta verde entrando): a rede guarda, mas não vê nem retira;
   - **só retirar** (seta laranja saindo): a rede vê e retira, mas nada entra sozinho na nuvem;
   - **guardar e retirar** (as duas setas, padrão): como um drive.

   Quem acessa a rede AE2 usa o que o modo permite enquanto o dono está online.
4. **Canais:** a linha "Canal" no topo escolhe o canal deste Link (◀ ▶); `+` cria um canal (digite o nome, Enter
   confirma, Esc cancela) e já passa o Link para ele; `✎` renomeia. Cada Link mostra e monta na rede AE2 o seu
   canal; o mesmo canal só pode estar montado em um Link por servidor. Limite: o do painel do TCMine, ou
   `maxChannels` (padrão 8). Com o TCMine, criar/renomear depende de endpoints que ele ainda não tem: a tela avisa;
   escolher entre canais existentes já funciona. Na nuvem local, tudo funciona.
5. No topo da tela, a **barra de cota** mostra quanto do canal está ocupado (a barra segue o limite mais apertado
   entre itens e tipos; amarela a partir de 75 %, vermelha a partir de 95 %). O tooltip traz os dois números. A cota
   vem do painel do TCMine; na nuvem local, de `localQuotaMaxTotal` e `localQuotaMaxTypes` (0 = sem limite).
6. Quebrar o bloco não derruba itens: eles estão na nuvem. Coloque outro Link (até em outro mundo) e eles
   estão lá.

Itens recusados na entrada: com itens dentro (shulker, mochila, célula do AE2 cheia), grandes demais, na tag
`#tccloud:never_transfer` ou com sinais de guardar dados no mundo.

**AE2 é opcional.** Com ele, o Link monta o canal na rede ME (modos e prioridade na barra lateral). Sem ele, o Link
funciona só pela tela (guardar, retirar, canais, cota) e os botões de rede somem. O Link não gasta energia.

Receitas (caras de propósito: o Link leva itens entre mundos e servidores), em `data/tccloud/recipe/`:
```
Com AE2 (cloud_link.json)                      Sem AE2 (cloud_link_vanilla.json)
RSR  R = Anel de quantum                       EDE  E = Fragmento de eco
PDP  S = Singularidade entrelaçada              DCD  D = Bloco de diamante
RCR  P = Processador de Cálculo, D = ME Drive   ENE  C = Baú do Ender, N = Estrela do Nether
     C = Componente de armazenamento 64k
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
