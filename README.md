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
4. Se não cobre e é um pedido de item exato → agenda autocrafting **só da diferença** (falta − estoque da rede;
   sem requester: o resultado entra na rede e tudo é entregue num ciclo seguinte). Se não der para craftar,
   entrega o que houver na rede.
5. Pedido por **tag/ferramenta/comida** sem nenhum item compatível na rede → a ponte escolhe um item craftável
   que o pedido aceite e crafta a falta. A escolha segue a config do servidor:
   - `tagCraftPreference`: `CHEAPEST` (menor custo estimado pelas receitas do AE2, padrão), `MOST_EXPENSIVE`
     (maior custo) ou `LIST` (ordem de `tagCraftPreferredItems`; os demais depois, do mais barato ao mais caro);
   - `tagCraftVanillaOnly`: só itens do Minecraft vanilla;
   - o filtro da ponte também vale (bloqueie o que não quer que seja craftado);
   - se faltar material para o item escolhido, o craft falha, entra em espera (`craftFailCooldownTicks`) e no
     ciclo seguinte a ponte tenta o próximo candidato. `tagCrafting = false` desliga tudo isso.

Um pedido já entregue não é atendido de novo durante `redeliveryCooldownTicks`, mesmo depois de reiniciar
o servidor. Várias pontes na mesma colônia (ex.: redes ME diferentes) podem coexistir: elas compartilham um
registro salvo no mundo e nunca atendem o mesmo pedido.

### Tela da ponte
Clique direito abre a tela (para quem tem permissão na colônia; fora de colônia, só o dono):
- estado da ponte e nome da colônia;
- lista dos pedidos em aberto com **o que a ponte fez com cada um** (entregue, craftando, sem estoque,
  não craftável, racks cheios, atendido por outra ponte, na fila...). Passe o mouse para ver o texto completo;
- **Crafting ligado/desligado** e **modo de redstone** (ignorar / só com sinal / só sem sinal).

A aba **Filtro** controla quais itens podem sair da rede (entrega ou craft):
- modo **Desligado / Só permitir / Bloquear** e comparação **só o item** ou **exata** (encantamentos, durabilidade);
- 18 slots "fantasma": clique com um item na mão para copiá-lo (o item continua com você), mão vazia ou
  shift-clique limpa, shift-clique no inventário copia para o primeiro slot livre;
- com **JEI** instalado, dá para arrastar itens da lista do JEI direto para os slots.

A aba **Estatísticas** mostra, por ponte: itens e pedidos entregues na última hora e nas últimas 24 h,
crafts enviados/falhos, um gráfico de itens entregues por hora e os itens mais entregues (nome no tooltip).
Contam só enquanto o mundo está rodando (tempo de jogo). Janela e resolução ajustáveis na config
(`statsBucketTicks`, `statsBuckets`; mudar zera as estatísticas). "Crafts ok" = jobs aceitos pelo AE2
(a ponte não sabe quando o craft termina, porque o craft não tem requester).

Pedidos por tag respeitam o filtro: se o item bloqueado não serve, a ponte procura outro compatível.

Quem não tem permissão só vê o estado na barra de ação. O próprio bloco também muda de visual: offline (sem rede ME),
erro (sem colônia, permissão ou armazém), ocioso e trabalhando.

## Abastecedor da Colônia
Segundo bloco, o caminho contrário da ponte. Mesmas regras (dentro da colônia, com permissão, cabo ME
comum por baixo) e uma tela com duas listas de 9 itens:

- **Manter no armazém** — "sempre ter 64 de farinha de osso": se cair abaixo, o bloco tira da rede ME
  e coloca nos racks.
- **Excedente para a rede ME** — "acima de 128 de trigo, o resto volta para o ME": esvazia o armazém
  entupido pelas fazendas da colônia.

Clique num slot com o item na mão para escolher; role o mouse sobre ele para mudar a quantidade
(Shift ±10, Ctrl ±64). O excedente **nunca sai** de um item que esteja em algum pedido em aberto da
colônia — sem isso, a ponte entregaria e o abastecedor levaria de volta, num vaivém sem fim.
`supplyMaxPerCycle` limita quanto cada linha move por ciclo.

### Monitor da Colônia (em desenvolvimento)
Coloque vários **Monitores da Colônia** lado a lado numa parede, virados para o mesmo lado: se formarem
um retângulo completo (até 8×6, configurável em `monitorMaxWidth`/`monitorMaxHeight`), viram uma tela única.
Formatos que não são retângulo mostram "Estrutura inválida".

Para mostrar dados, use o **Cartão de Ligação**: shift + clique direito na ponte grava a posição dela;
clique direito em qualquer bloco da tela liga a tela à ponte. Precisa estar na mesma dimensão, a até
64 blocos (`monitorLinkRange`) e com permissão na ponte. A tela mostra estado, colônia, pedidos em aberto,
itens entregues e, com 2+ blocos de altura, a **lista de pedidos** (ícone, descrição, quantidade e resultado),
paginada: troca sozinha a cada 10 s; clique direito na metade direita da tela avança, na esquerda volta
(a página é só sua; a troca automática pausa 30 s após um clique). Com 3+ blocos de altura, o gráfico
por hora fica acima da lista; com 4+ de largura, a lista usa duas colunas. Atualiza 1×/s, só quando algo muda,
e só lê a ponte se o chunk dela estiver carregado (senão mostra "Ponte não encontrada").

### Receita
```
 E      E = Processador de Engenharia (AE2)
IRI     I = Interface ME (AE2)
 E      R = Rack (MineColonies)
```
O item fica na aba própria **TC Colony Bridge** do modo criativo. A receita é um JSON comum
(`data/tccolonybridge/recipe/colony_bridge.json`) e pode ser trocada por datapack/KubeJS.

## Setup
1. Copia para `libs/` os jars do teu ATM10 (ver `libs/LEIA-ME.txt`).
2. Ajusta `neo_version` e `ae2_version` em `gradle.properties` para as versões do ATM10.
3. `./gradlew build` → `build/libs/tccolonybridge-<versão>.jar`
4. `./gradlew runClient` para testar em dev.

## Configuração
`<mundo>/serverconfig/tccolonybridge-server.toml` (gerado no primeiro arranque do mundo).

## Estrutura
- `block/` — bloco e block entity (nó da grid AE2)
- `logic/` — ciclo de pedidos, entrega e crafting
- `integration/ColonyAccess` — único ponto que toca na API do MineColonies
