# TC Colony Bridge

Addon NeoForge 1.21.1 que liga uma rede ME (Applied Energistics 2) ao sistema de pedidos do MineColonies.
Alvo: ATM10.

## Como funciona (MVP)
Coloca o bloco **ME Colony Bridge** dentro das fronteiras da colónia e liga-o à rede ME (usa 1 canal).
A cada ciclo (`cycleTicks`, 5 s por defeito):
1. Lê os pedidos em aberto (resolver do jogador + resolver de novas tentativas).
2. Se o item existe na rede → move para os racks do armazém e reatribui o pedido; um courier entrega.
3. Se não existe e é um pedido de item exato → agenda autocrafting (sem requester; o resultado entra na rede
   e é entregue no ciclo seguinte).

Clique direito no bloco mostra o estado.

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
