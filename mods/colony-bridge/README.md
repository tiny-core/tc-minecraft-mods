# TC Colony Bridge

Addon NeoForge 1.21.1 que liga uma rede ME (Applied Energistics 2) ao sistema de pedidos do MineColonies.
Alvo: ATM10.

## Como funciona (MVP)
Coloque o bloco **ME Colony Bridge** dentro das fronteiras da colônia e ligue-o à rede ME (usa 1 canal).
A conexão é **só por baixo** e **só com cabo comum** (vidro, coberto ou smart — os de 8 canais).
Cabo denso ou outro dispositivo AE2 encostado não conecta, e o estado avisa.
Só dá para colocar a ponte numa colônia onde você tem permissão de acessar as cabanas
(por padrão: dono, oficiais e amigos). A permissão de quem colocou é conferida de novo a cada ciclo:
se ela for retirada, a ponte para e mostra o estado "sem permissão".

A cada ciclo (`cycleTicks`, 5 s por padrão):
1. Lê os pedidos em aberto (resolver do jogador + resolver de novas tentativas).
2. Desconta o que o armazém já tem. Se já basta, nada sai da rede: a ponte só reatribui o pedido.
3. Se a rede cobre a falta → move **só a falta** para os racks do armazém e reatribui o pedido; um courier entrega.
4. Se não cobre e é um pedido de item exato → agenda autocrafting **só da diferença** (falta − estoque da rede).
   A ponte é a dona do craft no AE2: o resultado vai **direto para os racks do armazém**, sem passar pela rede
   (o que não couber vai para a rede e é entregue no ciclo seguinte). Quando o job termina, a ponte entrega o
   resto que já estava na rede e reatribui o pedido. Crafts em andamento sobrevivem a reinícios; cancelar o
   job no terminal do AE2 libera o pedido. Se não der para craftar, entrega o que houver na rede.
5. Pedido por **tag/ferramenta/comida** sem nenhum item compatível na rede → a ponte escolhe um item craftável
   que o pedido aceite e crafta a falta; a tela mostra o item escolhido (ex.: "Qualquer picareta → Picareta
   de Pedra"). Se a rede tiver **vários** itens compatíveis (ex.: tábuas de carvalho e de bétula), a ponte
   junta todos na mesma entrega. A escolha segue a **tela da ponte** (abas Geral, Preferidos e Mods); onde a
   ponte está em "padrão do servidor", vale a config:
   - `tagCraftPreference`: `CHEAPEST` (menor custo estimado pelas receitas do AE2, padrão), `MOST_EXPENSIVE`
     (maior custo) ou `LIST` (ordem de `tagCraftPreferredItems`; os demais depois, do mais barato ao mais caro);
   - `tagCraftVanillaOnly`: regra do servidor, só itens do Minecraft vanilla (a ponte não muda);
   - o filtro da ponte também vale (bloqueie o que não quer que seja craftado);
   - se faltar material para o item escolhido, o craft falha, entra em espera (`craftFailCooldownTicks`) e no
     ciclo seguinte a ponte tenta o próximo candidato. `tagCrafting = false` desliga tudo isso.

Um pedido já entregue não é atendido de novo durante `redeliveryCooldownTicks`, mesmo depois de reiniciar
o servidor. Várias pontes na mesma colônia (ex.: redes ME diferentes) podem coexistir: elas compartilham um
registro salvo no mundo e nunca atendem o mesmo pedido.

### Tela da ponte
Clique direito abre a tela (para quem tem permissão na colônia; fora de colônia, só o dono). Barras de botões
com ícone (passe o mouse para ver o valor): à **esquerda** o que vale para a ponte toda ("?" explica a aba
aberta, crafting, redstone); à **direita** os ajustes da aba aberta. Quatro abas:

- **Geral:** estado da ponte, nome da colônia, **crafting ligado/desligado**, **modo de redstone** (ignorar /
  só com sinal / só sem sinal), **preferência de craft por tag** (padrão do servidor / mais barato / mais caro /
  lista de preferidos) e um resumo do último ciclo (pedidos abertos, atendidos, craftando, pendentes).
  A lista com o motivo de cada pedido e as estatísticas ficam no **Monitor da Colônia**.
- **Filtro:** quais itens podem sair da rede (entrega ou craft):
  - modo **Desligado / Só permitir / Bloquear** e comparação **só o item** ou **exata** (encantamentos, durabilidade);
  - 18 slots "fantasma": clique com um item na mão para copiá-lo (o item continua com você), mão vazia ou
    shift-clique limpa, shift-clique no inventário copia para o primeiro slot livre;
  - com **JEI** instalado, dá para arrastar itens da lista do JEI direto para os slots.
- **Preferidos:** 18 slots fantasma com a ordem usada pela "Lista de preferidos" (1º slot primeiro). Vazio =
  usa `tagCraftPreferredItems` da config do servidor.
- **Mods:** modo **Todos / Só os marcados / Todos menos os marcados / Preferir os marcados** e a lista dos mods
  que a rede ME sabe craftar; clique para marcar.

As preferências de craft ficam salvas **em cada ponte**: cada jogador configura a sua. A regra
`tagCraftVanillaOnly` e a `craftBlacklist` são do servidor e valem para todas as pontes.

Pedidos por tag respeitam o filtro: se o item bloqueado não serve, a ponte procura outro compatível.

Quem não tem permissão só vê o estado na barra de ação. O próprio bloco também muda de visual: offline (sem rede ME),
erro (sem colônia, permissão ou armazém), ocioso e trabalhando.

## Abastecedor da Colônia
Segundo bloco, o caminho contrário da ponte. Mesmas regras (dentro da colônia, com permissão, cabo ME
comum por baixo) e uma tela com duas listas de 9 itens:

- **ME → Armazém (mínimo)** — "sempre ter 64 de farinha de osso": se cair abaixo, o bloco tira da rede ME
  e coloca nos racks.
- **Armazém → ME (máximo)** — "acima de 128 de trigo, o resto volta para o ME": esvazia o armazém
  entupido pelas fazendas da colônia.

Clique num slot com o item na mão para escolher; role o mouse sobre ele para mudar a quantidade
(Shift ±10, Ctrl ±64); o tooltip do slot diz a regra em frase e quanto há no armazém agora. O excedente **nunca sai** de um item que esteja em algum pedido em aberto da
colônia — sem isso, a ponte entregaria e o abastecedor levaria de volta, num vaivém sem fim.
`supplyMaxPerCycle` limita quanto cada linha move por ciclo.

### Monitor da Colônia (em desenvolvimento)
Coloque vários **Monitores da Colônia** lado a lado numa parede, virados para o mesmo lado: se formarem
um retângulo completo (até 8×6, configurável em `monitorMaxWidth`/`monitorMaxHeight`), viram uma tela única.
Formatos que não são retângulo mostram "Estrutura inválida".

Para mostrar dados, use o **Cartão de Ligação**: shift + clique direito numa **Ponte** ou num **Abastecedor**
grava a posição; clique direito em qualquer bloco da tela liga a tela a ele. Precisa estar na mesma dimensão,
a até 64 blocos (`monitorLinkRange`) e com permissão no bloco.

**Ponte na tela:** estado, colônia, pedidos em aberto, itens entregues (1h e 24h), crafts concluídos (4+ blocos
de largura) e, com 2+ blocos de altura, a **lista de pedidos** (ícone, descrição, quantidade e resultado).
Com 3+ de altura, gráfico por hora; com 4+, faixa com os itens mais entregues.

**Abastecedor na tela:** linhas faltando, entrou do ME (1h), voltou ao ME (1h), entrou do ME (24h) e,
com 2+ blocos de altura, a **lista das linhas** com a quantidade atual, a direção e o limite à direita
(`↓ mín 64` = vem do ME até ter 64; `↑ máx 64` = o que passar de 64 volta ao ME) e uma barra colorida — âmbar = falta repor,
verde = ok, ciano = acima do alvo (excedente que volta ao ME). Com 3+ de altura, gráfico de repostos por hora.

As listas são paginadas: trocam sozinhas a cada 10 s; clique direito na metade direita da tela avança, na
esquerda volta (a página é só sua; a troca automática pausa 30 s após um clique). Com 4+ de largura, a lista usa
duas colunas. Atualiza 1×/s, só quando algo muda, e só lê o bloco ligado se o chunk dele estiver carregado
(senão mostra "Bloco ligado não encontrado").

### Receita
```
 E      E = Processador de Engenharia (AE2)
IRI     I = Interface ME (AE2)
 E      R = Rack (MineColonies)
```
O item fica na aba própria **TC Colony Bridge** do modo criativo. A receita é um JSON comum
(`data/tccolonybridge/recipe/colony_bridge.json`) e pode ser trocada por datapack/KubeJS.

## Setup
Este mod faz parte do workspace **TC Minecraft Mods** (ver o `README.md` da raiz: build, VS Code, testes).
1. Copie para `mods/colony-bridge/libs/` os jars do seu ATM10 (ver `libs/LEIA-ME.txt`).
2. `ae2_version` fica em `mods/colony-bridge/gradle.properties`; a versão do NeoForge, no `gradle.properties` da raiz.
3. Na raiz: `./gradlew :colony-bridge:build` → `build/libs/tccolonybridge-<versão>.jar`.
4. **Em jogo, instale também o TC Core** (`build/libs/tccore-<versão>.jar`): o Colony Bridge depende dele.
5. `./gradlew :colony-bridge:runClient` para testar em dev (o core roda junto).

Testes automáticos deste mod: `./gradlew :colony-bridge:test --rerun` — escolha do item a craftar
(`CraftOrdering`), limpeza de pacotes/NBT das preferências (`CraftSettings`), reservas entre pontes
(`DeliveryLedger`), linhas do Abastecedor no monitor, quanto o Abastecedor repõe/devolve (`SupplyRule`),
ranking de itens (`TopRanking`), formação do monitor (`MonitorShape`), animação e paginação do monitor,
NBT e pacote das configurações da ponte (`BridgeSettings`). Os do core (`JoinedList`, `MetricRing`,
`MetricSeries`) ficam em `mods/core/`.

## Configuração
`<mundo>/serverconfig/tccolonybridge-server.toml` (gerado no primeiro arranque do mundo).

- **Em tempo real:** editar e salvar o arquivo com o jogo/servidor rodando basta — o NeoForge recarrega e a
  mudança vale no próximo ciclo. Exceções: `statsBucketTicks`/`statsBuckets` zeram as estatísticas, e o
  tamanho máximo dos monitores só vale na próxima vez que uma tela for formada.
- **Tela no jogo:** Mods → TC Colony Bridge → Config. Edita a config em single player ou no host de uma LAN;
  num servidor dedicado, só pelo arquivo.

## Estrutura
Cada camada (`block/`, `logic/`, `menu/`, `client/`) tem um subpacote por bloco (`bridge/`, `supply/`,
`monitor/`); a raiz guarda o que é compartilhado. `integration/ColonyAccess` é o único ponto que toca na API
do MineColonies. Responsabilidade de cada classe: [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).
