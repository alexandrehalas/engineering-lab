# AGENTS.md — Engineering Lab

## 1. Propósito e papel do agente

Este repositório é um **laboratório de engenharia de software**, não uma corretora nem um produto comercial. O domínio de investimentos é **inteiramente simulado**. O objetivo é aprender, justificar escolhas e produzir evidências por meio de implementações pequenas e verificáveis.

Ao atuar como Codex ou outro agente de implementação:

- Execute **somente o incremento solicitado**, com alterações pequenas e revisáveis.
- Preserve as decisões de negócio e arquitetura existentes; **não tome decisões arquiteturais relevantes silenciosamente**.
- Prefira código simples e explícito a abstrações criadas apenas para demonstrar padrões.
- Explique os trade-offs de alternativas quando houver uma decisão a tomar. Não substitua o aprendizado do responsável pelo projeto.
- Não utilize código, contratos, dados ou regras confidenciais de empregadores ou terceiros. Use apenas código original e dados sintéticos ou públicos devidamente licenciados.

## 2. Fontes de verdade e precedência

Leia os documentos aplicáveis **antes de implementar**:

1. `docs/use-cases/uc-001-consultar-cotacao.md` — comportamento de negócio e critérios de aceite da UC-001.
2. `docs/architecture/initial-architecture.md` — arquitetura vigente, decisões técnicas e questões pendentes.
3. `docs/architecture/adr/0001-architecture-style.md` — justificativa e consequências do estilo arquitetural aprovado.
4. `README.md` — visão geral e instruções de uso, quando existentes.

Em caso de divergência, **não escolha arbitrariamente**: sinalize o conflito e solicite decisão. Um ADR registra o motivo de uma decisão; a arquitetura vigente descreve o estado atual. Não altere requisitos de negócio por conveniência técnica.

## 3. Escopo do incremento 01

Implementar apenas a **UC-001 — Consulta de cotação**:

- Tickers suportados pelo produto: `PETR4`, `VALE3` e `ITUB4`.
- Cotações sintéticas, exclusivamente em `BRL`, consultadas sob demanda.
- Resposta válida contendo ticker, preço de referência, moeda e horário de referência.
- Preço estritamente positivo; dados inválidos não são apresentados como cotações válidas.
- Cotação antiga obtida com sucesso **pode** ser exibida com seu horário; não existe limite de idade.
- Indisponibilidade do fornecedor **não** autoriza cache/fallback com cotação anterior.
- Distinguir ticker não suportado, cotação ausente, falha do fornecedor e dados inválidos.

**Fora do escopo:** compras e vendas, carteira, histórico, autenticação, frontend, bancos de dados, ativos adicionais, preços reais, integrações com a B3, Kafka, cache de cotações, failover e processamento assíncrono. Não antecipe essas capacidades.

## 4. Stack e organização

- **Java 25 (LTS)** e **Spring Boot 4.x**, com Spring MVC. A versão de manutenção exata deve ser definida quando o scaffold for aprovado.
- **Maven**, inicialmente um módulo de aplicação Spring Boot no monorepo `engineering-lab`.
- Arquitetura **hexagonal (Ports & Adapters)** com **Package by Feature**, começando pela capacidade `quote`.
- **DDD pragmático**: linguagem ubíqua, Value Objects, políticas e invariantes quando justificáveis.
- **Domínio em Java puro**: sem Spring, HTTP, DTOs externos ou detalhes de infraestrutura. Spring é permitido na aplicação e nos adaptadores.
- Cliente HTTP de saída: **Spring `RestClient`**. Fornecedor externo: **WireMock**; não crie outra aplicação Spring Boot para simulá-lo.
- **Sem Lombok ou MapStruct inicialmente**; prefira `record` quando adequado e mapeamento manual simples.
- Código, pacotes, tipos, métodos e testes **em inglês**. Documentação funcional, de arquitetura, ADRs e notas de estudo **em português do Brasil**. O idioma do contrato HTTP público continua pendente de decisão.

Estrutura **de referência**, a criar apenas conforme necessário:

```text
investment-simulator/src/main/java/<base-package>/quote/
├── domain/                # model/ e policy/ quando usados
├── application/           # casos de uso e portas
└── infrastructure/        # inbound/rest, outbound/marketdata e config
```

Não crie diretórios vazios, pacotes `shared` genéricos, novos módulos Maven ou microsserviços por antecipação. A estrutura de pacotes não é, por si só, uma definição de Bounded Contexts.

## 5. Regras arquiteturais obrigatórias

1. **Domínio:** expressa conceitos e invariantes sem dependências de frameworks. `Ticker`, `Money` e `Quote` são Value Objects na UC-001; `SupportedAssetCatalog` representa a política de ativos aceitos.
2. **Aplicação:** orquestra o caso de uso, verifica o catálogo **antes da consulta externa** e depende de uma porta de saída expressa em tipos internos. Pode usar Spring.
3. **Porta de saída:** pertence à aplicação; **nunca exponha** DTOs do fornecedor, `RestClient`, status HTTP externos ou tipos de bibliotecas de transporte.
4. **Adaptador de saída:** implementa a porta, executa HTTP e traduz contrato, ausência e falhas do fornecedor. A **ACL** é uma responsabilidade dessa fronteira, não uma API/camada física adicional obrigatória.
5. **Adaptador de entrada:** recebe HTTP e traduz resultados/erros públicos; não contém a lógica de validade de preço nem conhece detalhes do fornecedor.
6. **Composição:** use configuração Spring (`@Bean`, quando apropriado) para injetar componentes de domínio sem anotá-los. Não transforme Value Objects em singletons do container.
7. **Dependências:** adaptadores apontam para abstrações/modelos internos; casos de uso não importam classes de adaptadores. Não crie interfaces para classes que não possuem uma fronteira real a proteger.

### Invariantes do modelo

- `Ticker`: código presente e não vazio. Sua validade estrutural **não** significa que seja suportado pelo produto.
- `Money`: montante `BigDecimal` e `Currency` não nulos; permite negativos, zero e positivos.
- `Quote`: ticker, preço monetário e instante de referência presentes; **preço > 0** e **moeda `BRL`** na UC-001.
- `SupportedAssetCatalog`: aceita **somente** os três tickers definidos no caso de uso; é independente do fornecedor.

Não introduza entidades, agregados, repositórios ou serviços de domínio apenas para exibir DDD.

## 6. Contratos, erros e integração

Preserve o contrato **público** discutido para os cenários da UC-001:

| Cenário | HTTP público |
|---|---:|
| Cotação válida | `200` |
| Ticker não suportado | `400` |
| Ativo suportado sem cotação | `404` |
| Fornecedor indisponível ou timeout | `503` |
| Dados inválidos recebidos do fornecedor | `500` |

Use, quando implementar respostas de erro, o suporte nativo do Spring a **Problem Details (RFC 9457)**. Não invente um envelope de erro próprio nem exponha stack traces, segredos ou URLs internas ao consumidor.

**Contrato externo do WireMock:** `200` com dados válidos produz `Quote`; ausência de cotação **confirmada** pelo contrato produz resultado vazio; `503`/timeout produz falha interna tipada; JSON malformado ou cotação inválida produz falha de dados/integração. **Não traduza todo `404` externo em ausência de cotação**: um stub inexistente ou rota incorreta também pode produzir `404`.

A assinatura exemplificativa `Optional<Quote>` da porta não é autorização para tratar falhas como `Optional.empty()`. Preserve as causas técnicas nos diagnósticos sem obrigar a aplicação a conhecer cada detalhe HTTP. Não registre a mesma exceção desnecessariamente em todas as camadas.

## 7. Pontos ainda não aprovados — não decidir silenciosamente

Antes de implementar código afetado por eles, apresente proposta breve, alternativas e consequências, e peça decisão ao responsável:

- Igualdade de `Money` quando `BigDecimal` usa escalas diferentes (`35.5` vs. `35.50`).
- Formato/normalização de `Ticker` além de nulo ou vazio.
- Caminhos definitivos das APIs, nomes dos campos JSON públicos e convenção de data/hora.
- Corpo/código de erro do fornecedor para distinguir cotação ausente de rota/stub incorreto.
- Assinatura final da porta, exceções internas e valores de timeout.
- Versão de manutenção exata do Spring Boot 4 e compatibilidade das ferramentas selecionadas.

Outros detalhes locais e reversíveis podem ser implementados com escolhas simples, descritas no resumo da alteração. Não apresente propostas como decisões consolidadas.

## 8. Testes e verificação

- **JUnit Jupiter** para domínio e regras; **Mockito** quando ajudar a isolar o caso de uso.
- **WireMock + JUnit** para exercitar HTTP real do adaptador de saída, sem precisar iniciar o contexto Spring nos testes de domínio/caso de uso.
- **Spring Boot Test** e cliente HTTP compatível com a versão adotada para verificar a API REST e `ProblemDetail`.
- **JaCoCo** desde o início; cobertura é diagnóstico, **não** meta obrigatória de 100%. SonarQube poderá ser acrescentado em incremento posterior.
- Verifique os critérios `CA-001` a `CA-007` da UC-001 e cenários adversos: ticker não suportado sem chamada externa, cotação ausente, cotação antiga, preço não positivo, JSON malformado, timeout, `503`, `404` legítimo versus `404` inesperado, ausência de fallback.
- Teste limites e comportamento observável, não apenas execução de linhas. Não introduza Testcontainers nem infraestrutura de banco de dados sem necessidade comprovada.
- Para falhas, preserve informação diagnóstica útil e evite dados sensíveis ou cardinalidade excessiva em métricas.

## 9. Fluxo de trabalho para cada tarefa

1. **Ler** os arquivos e o código relevante, identificando requisitos, restrições e testes envolvidos.
2. **Delimitar** o menor incremento executável. Se houver decisão arquitetural relevante pendente, explicitar antes de implementar.
3. **Implementar** somente o solicitado, com testes proporcionais ao risco e ao comportamento.
4. **Executar** verificações disponíveis (compilação, testes, cobertura, quando configurada). Nunca afirmar que algo passou sem executar; se não conseguir, informar o motivo.
5. **Resumir** arquivos alterados, decisões locais, testes executados e resultados, limitações e pendências.
6. **Documentar** mudanças relevantes no local correto. Criar ADR apenas para decisão arquitetural significativa; não reescrever ADR aceito para apagar o histórico.

Não modifique arquivos não relacionados, não faça refatorações amplas incidentalmente e não gere commits ou publique artefatos sem solicitação explícita. Preserve o histórico e as alterações existentes; não sobrescreva trabalho do usuário.

## 10. Critério de conclusão de uma entrega

Uma tarefa está concluída quando o comportamento solicitado está implementado, os testes relevantes foram executados ou suas limitações declaradas, as dependências respeitam a arquitetura, e o resumo permite ao responsável revisar e explicar as decisões.

**Princípio central:** negócio antes da arquitetura, aprendizado antes de automação cega e complexidade somente quando uma necessidade ou hipótese técnica justificar.
