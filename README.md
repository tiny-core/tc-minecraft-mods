# TC Colony Bridge

Addon NeoForge 1.21.1 que liga uma rede ME (Applied Energistics 2) ao sistema de pedidos do MineColonies.
Alvo: ATM10.

## Como funciona (MVP)
Coloque o bloco **ME Colony Bridge** dentro das fronteiras da colônia e ligue-o à rede ME (usa 1 canal).
Só dá para colocar a ponte numa colônia onde você tem permissão de acessar as cabanas
(por padrão: dono, oficiais e amigos). A permissão de quem colocou é conferida de novo a cada ciclo:
se ela for retirada, a ponte para e mostra o estado "sem permissão".

A cada ciclo (`cycleTicks`, 5 s por padrão):
1. Lê os pedidos em aberto (resolver do jogador + resolver de novas tentativas).
2. Se o item existe na rede → move para os racks do armazém e reatribui o pedido; um courier entrega.
3. Se não existe e é um pedido de item exato → agenda autocrafting (sem requester; o resultado entra na rede
   e é entregue num ciclo seguinte).

Um pedido já entregue não é atendido de novo durante `redeliveryCooldownTicks`, mesmo depois de reiniciar
o servidor. Várias pontes na mesma colônia (ex.: redes ME diferentes) podem coexistir: elas compartilham um
registro salvo no mundo e nunca atendem o mesmo pedido.

Clique direito no bloco mostra o estado. O próprio bloco também muda de visual: offline (sem rede ME),
erro (sem colônia, permissão ou armazém), ocioso e trabalhando.

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
