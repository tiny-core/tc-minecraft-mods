# TC Cloud Storage (`tccloud`)

Armazenamento de itens na nuvem do TCMine, visível para a rede ME do Applied Energistics 2. O jogador
guarda itens num canal dele e os reencontra em outro servidor do **mesmo dono** (outro modpack, outro
mundo). Itens de mods que o servidor novo não tem aparecem como incompatíveis e não podem ser retirados.

**Estado: em desenvolvimento (fase 1 de 6).** Ainda não há nada para usar em jogo; o jar só carrega o
núcleo de regras. Plano e fases: `docs/planos/tc-cloud-storage.md` na raiz do workspace.

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
