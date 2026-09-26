# Guia rápido — Blockbench para este mod

## Criar o modelo
1. **File → New → Java Block/Item** (não use "Bedrock" nem "Generic Model").
2. Grade de 16×16×16 = 1 bloco. Texturas de 16×16 px (ou 32×32 para mais detalhe, sempre potência de 2).
3. Limitações do formato Java (o Blockbench avisa):
   - cada cubo só gira em **um eixo**, em passos de **22,5°**;
   - nada de malhas livres; tudo é feito de cubos.
4. Comece simples: um cubo com faces diferentes (frente com "tela", laterais com moldura). Depois
   adicione detalhes com cubos pequenos (parafusos, cabos, relevos).

## Textura
- Use o **Paint** do próprio Blockbench ou outro editor (Aseprite, GIMP, Krita).
- Textura animada (ex.: luz piscando): imagem vertical com vários quadros + arquivo
  `nome.png.mcmeta` com `{ "animation": { "frametime": 4 } }`.
- Brilho (emissivo) não existe em modelo JSON vanilla. A parte que "acende" (tela dos monitores)
  será desenhada pelo código com luz máxima.

## Exportar para o projeto
| O quê | Onde |
|---|---|
| Modelo (`File → Export → Block/Item Model`) | `src/main/resources/assets/tccolonybridge/models/block/<nome>.json` |
| Textura (`.png`) | `src/main/resources/assets/tccolonybridge/textures/block/<nome>.png` |
| Projeto `.bbmodel` (fonte editável) | `art/<nome>.bbmodel` (fora de `resources`, não vai para o jar) |

No JSON exportado, as texturas devem apontar para `tccolonybridge:block/<nome>`. Se o Blockbench
gravar um caminho do seu PC, corrija à mão ou peça ao Claude Code.

## Dicas para iniciante
- Aprenda 3 atalhos: `Ctrl+D` duplicar, `R` girar, `S` escalar. O resto é ir testando.
- Mantenha o estilo coerente com AE2 (moldura escura, linhas de energia) para o bloco não destoar
  na base.
- Teste no jogo com `F3+T` (recarrega recursos sem reiniciar) durante o `runClient`.
