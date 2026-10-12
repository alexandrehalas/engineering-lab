# ADR-0001 — Arquitetura hexagonal com organização por capacidade de negócio

- **Status:** Aceito
- **Data:** 2026-10-10
- **Projeto:** Engineering Lab — Investment Simulator
- **Escopo:** organização arquitetural inicial da aplicação, a partir da UC-001
- **Documentos relacionados:** [UC-001 — Consultar cotação](../../use-cases/uc-001-consultar-cotacao.md) · [Arquitetura inicial](../initial-architecture.md)

## Contexto

O Engineering Lab é um laboratório de engenharia de software, não uma plataforma comercial de investimentos. Seu primeiro incremento, a **UC-001 — Consultar cotação**, permite consultar preços sintéticos de três ativos brasileiros por meio de um fornecedor externo fictício.

A aplicação precisa manter sob seu controle o catálogo de ativos suportados e a validade das cotações, sem depender dos modelos e convenções HTTP do fornecedor. Ao mesmo tempo, o laboratório pretende aprofundar o aprendizado de **DDD**, **inversão de dependências** e **Ports & Adapters**.

O sistema é pequeno: inicialmente haverá **uma aplicação Spring Boot**, um fornecedor simulado por **WireMock** e **um módulo Maven de aplicação** em um monorepo. Não há justificativa para introduzir múltiplos serviços ou módulos físicos apenas para reproduzir uma topologia corporativa.

A decisão deve conciliar fronteiras de dependência explícitas, facilidade de teste e evolução incremental com um custo estrutural proporcional ao tamanho da UC-001.

## Alternativas consideradas

### A. Organização tradicional por camadas técnicas

Exemplo: pacotes globais `controller`, `service`, `client` e, quando necessário, `repository`.

- **Vantagens:** estrutura familiar, rápida de iniciar e com menor esforço inicial de organização.
- **Desvantagens:** capacidades de negócio ficam espalhadas entre pacotes técnicos; a separação entre regra de negócio e integração depende mais de disciplina do que de contratos explícitos.
- **Avaliação:** viável para a UC-001, porém oferece menos oportunidades de exercitar os limites arquiteturais que constituem um objetivo do laboratório.

### B. Arquitetura hexagonal organizada por capacidade de negócio — **escolhida**

Agrupar o código da capacidade `quote` e separar, dentro dela, `domain`, `application` e adaptadores de entrada e saída. Definir portas **quando houver uma fronteira real a proteger**.

- **Vantagens:** dependências explícitas, domínio protegido dos detalhes do fornecedor, testes isolados e componentes relacionados à mesma capacidade próximos no código.
- **Desvantagens:** mais conceitos e alguma estrutura adicional para um caso de uso pequeno; exige atenção para não criar interfaces, mapeamentos e delegações cerimoniais.
- **Avaliação:** o custo adicional é aceitável porque atende diretamente à hipótese de aprendizado do laboratório.

### C. Hexagonal com módulos Maven ou serviços separados desde o início

- **Vantagens:** isolamento físico mais rígido e possibilidade de impor fronteiras por artefatos de build.
- **Desvantagens:** configuração, manutenção e custo operacional superiores, sem necessidade demonstrada pela UC-001.
- **Avaliação:** rejeitada neste estágio. A arquitetura hexagonal não exige multimódulo nem microsserviços.

## Decisão

Adotar **arquitetura hexagonal (Ports & Adapters)** e **Package by Feature**, começando pela capacidade de negócio `quote`, **em uma única aplicação Spring Boot e um único módulo Maven de aplicação**.

Estabelecer as seguintes regras:

1. **Domínio:** representa conceitos, políticas e invariantes de negócio em **Java puro**, sem anotações ou dependências do Spring ou de infraestrutura. A modelagem utilizará DDD de forma pragmática, sem exigir entidades, agregados ou repositórios artificiais.
2. **Aplicação:** coordena os casos de uso e define as **portas de saída** necessárias. Pode utilizar recursos do Spring, mas não deve conhecer clientes HTTP, DTOs ou detalhes do fornecedor.
3. **Adaptadores de entrada:** traduzem o contrato de entrada (inicialmente REST) para chamadas à aplicação e convertem resultados e falhas em respostas públicas.
4. **Adaptadores de saída:** implementam as portas da aplicação. Na UC-001, o adaptador HTTP consulta o fornecedor simulado e traduz contratos, ausências e falhas externas para conceitos internos.
5. **Anti-Corruption Layer (ACL):** a proteção semântica em relação ao fornecedor será exercida **dentro da fronteira do adaptador de saída**; não haverá camada física ou serviço intermediário de ACL por padrão.
6. **Direção das dependências:** o domínio não depende das camadas externas; a aplicação pode depender do domínio; os adaptadores dependem das abstrações internas que implementam ou utilizam. A direção da chamada HTTP em execução **não** altera essa regra de dependência de código.
7. **Composição:** configurações do Spring fazem a associação entre portas e implementações. Classes de domínio que precisem ser injetadas podem ser registradas por `@Bean`, sem receber anotações do framework.
8. **Proporcionalidade:** criar apenas os pacotes, classes, portas e configurações necessários ao incremento vigente. Não criar antecipadamente capacidades futuras nem um pacote `shared` genérico.

Estrutura de referência, sem obrigação de criar diretórios vazios:

```text
investment-simulator/
└── src/main/java/<base-package>/
    └── quote/
        ├── domain/
        │   ├── model/
        │   └── policy/
        ├── application/
        │   └── port/
        └── infrastructure/
            ├── inbound/rest/
            ├── outbound/marketdata/
            └── config/
```

O monorepo `engineering-lab` também conterá documentação e configurações do WireMock; isso **não** implica executar múltiplas aplicações de negócio.

## Consequências

### Positivas

- A UC-001 pode ser testada sem acoplar suas regras à infraestrutura HTTP ou ao fornecedor simulado.
- A substituição de um fornecedor com contrato semanticamente compatível tende a afetar principalmente o adaptador de saída, não o modelo interno.
- O agrupamento por capacidade favorece coesão e localização de código relacionado.
- O projeto oferece exemplos concretos para estudar DDD, DIP e ACL, em vez de apenas reproduzir uma organização de pacotes.

### Custos e riscos aceitos

- Há mais fronteiras e conceitos do que em uma implementação em camadas simples.
- A organização de pacotes, sozinha, **não garante** o respeito à direção das dependências; testes de arquitetura poderão reforçar essa regra quando justificável.
- A separação por `quote` **não define automaticamente um Bounded Context**; futuras capacidades serão modeladas de acordo com suas regras, e não apenas com nomes de pacotes.
- A tradução via ACL não garante independência absoluta: mudanças semânticas nas informações oferecidas pelo fornecedor podem exigir revisão de requisitos e do domínio.

## Critérios de verificação

A implementação inicial deverá permitir verificar que:

- [ ] O pacote `quote.domain` não importa classes de Spring, HTTP nem infraestrutura.
- [ ] O caso de uso consulta uma porta interna, sem instanciar ou importar o adaptador HTTP.
- [ ] Os DTOs e convenções do fornecedor não atravessam a porta para a aplicação.
- [ ] Um teste do caso de uso consegue substituir a porta por um dublê sem executar WireMock ou iniciar o Spring.
- [ ] Um teste do adaptador exercita a tradução do contrato externo para o modelo interno.
- [ ] Não foram criados módulos, serviços, abstrações ou capacidades futuras sem necessidade verificada.

## Limites desta decisão

Este ADR **não fixa** o desenho final dos endpoints, a semântica de igualdade de `Money`, o formato dos erros do fornecedor, os limites numéricos de timeout ou a versão de manutenção exata do Spring Boot. Esses pontos constam como pendências na [arquitetura inicial](../initial-architecture.md) e serão definidos antes de implementar os componentes afetados.

A escolha de Java 25, Spring Boot, RestClient, WireMock e ferramentas de teste está documentada na arquitetura inicial; este ADR registra especificamente **a decisão de estilo arquitetural e organização do código**.

## Revisão da decisão

Reavaliar somente diante de necessidade concreta — por exemplo, crescimento de capacidades com fronteiras independentes, dificuldade verificável de impor dependências ou experimentos que exijam isolamento físico. Se uma nova decisão substituir esta, criar outro ADR e referenciar este registro, preservando o histórico.
