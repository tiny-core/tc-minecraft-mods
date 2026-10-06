# Changelog

Mudanças por versão de cada mod TC. A versão de cada mod fica no `gradle.properties` dele. "Não lançado" = já no
`main`, ainda sem release (falta a arte dos blocos).

## Não lançado

### TC Core 0.2.0
- Altura da grade dos terminais (5 linhas, 8 ou a janela toda), salva em `config/tccore-client.toml`, com botão
  para a barra lateral (`GridHeightButton`).
- Busca por `#tag` nas grades de itens (além de nome e `@mod`).

### TC Colony Bridge 0.4.0 (exige TC Core 0.2.0)
- **TC Pattern Encoder:** cria padrões de crafting para os pedidos da colônia que a rede ME não sabe craftar; Blank
  Patterns do slot ou da rede ME; também pela aba do tablet.
- Receitas mais caras para todos os blocos e itens; Monitor, Abastecedor, Terminal e Cartão de Ligação ganharam
  receita.
- Terminal do Armazém: grade começa com 5 linhas, botão de altura e busca por `#tag`.
- Integração com o JEI sem APIs marcadas para remoção.
- Protocolo de rede 13: cliente e servidor precisam da mesma versão.

### TC Cloud Storage 0.3.0 (exige TC Core 0.2.0)
- Receita do TC Cloud Link.
- Grade começa com 5 linhas, botão de altura e busca por `#tag`.
- Fala com a nuvem do TCMine (fase 4): chave entregue pelo TCMine, política de itens, relatórios.
