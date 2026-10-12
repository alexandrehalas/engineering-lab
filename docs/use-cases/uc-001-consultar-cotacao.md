# UC-001 — Consultar cotação de um ativo

**Produto:** Engineering Lab — Investment Simulator  
**Status:** Implementada e verificada  
**Incremento:** 01 — Consulta de cotações  
**Verificada em:** 12/10/2026  
**Evidências:** [Relatório de verificação da UC-001](../testing/uc-001-tests.md) — 103 testes aprovados, sem falhas, erros ou testes ignorados.

## 1. Objetivo

Permitir que um investidor consulte o **preço de referência** de uma ação brasileira, com informações disponibilizadas por um **fornecedor fictício de Market Data**, sem movimentação de dinheiro real.

O preço consultado é informativo: **não representa uma oferta de compra ou venda nem garante um preço de execução**.

## 2. Ator

**Investidor:** pessoa que deseja conhecer o preço de referência de um ativo no simulador.

Não haverá diferenciação de perfis nem autenticação neste primeiro incremento.

## 3. Escopo e premissas

- O simulador reconhecerá **exclusivamente três ativos**: `PETR4`, `VALE3` e `ITUB4`.
- O fornecedor fictício de Market Data disponibilizará cotações para esses mesmos ativos, conforme os cenários simulados.
- Os códigos identificam ações brasileiras conhecidas, mas **todos os preços são sintéticos**, sem compromisso com cotações reais.
- Todas as cotações serão expressas em **real brasileiro (`BRL`)**.
- A consulta ocorrerá **sob demanda**, a partir do código informado pelo investidor.
- O universo de ativos é fixo neste incremento; não haverá cadastro ou inclusão dinâmica.
- **O simulador determina quais ativos são suportados**. O fornecedor informa suas cotações, não define o catálogo aceito pelo produto.

## 4. Fluxo principal

1. O investidor solicita a cotação de um ativo informando seu código.
2. O simulador identifica que o ativo pertence ao conjunto de ativos suportados.
3. O simulador obtém a cotação disponibilizada pelo fornecedor fictício de Market Data.
4. Havendo uma cotação válida, o simulador apresenta:
   - código do ativo;
   - preço de referência;
   - moeda (`BRL`);
   - data e horário de referência da cotação.

**Exemplo ilustrativo (dados fictícios):**

| Campo | Valor |
|---|---|
| Ativo | `PETR4` |
| Preço de referência | `R$ 35,50` |
| Moeda | `BRL` |
| Data e hora de referência | `10/10/2026 14:30` |

## 5. Regras de negócio

| ID | Regra |
|---|---|
| **RN-001** | Cada ativo suportado possui um código identificador. |
| **RN-002** | Uma cotação representa o preço de referência de um ativo em um instante específico. |
| **RN-003** | O preço de uma cotação válida deve ser maior que zero. |
| **RN-004** | O fornecedor pode disponibilizar novas cotações para os ativos ao longo do tempo. |
| **RN-005** | O produto não oferece consulta ao histórico de cotações neste incremento. |
| **RN-006** | Toda cotação apresentada contém código do ativo, preço, moeda (`BRL`) e data/hora de referência. |
| **RN-007** | Não existe limite de idade da cotação para sua apresentação. O produto não deve afirmar que um preço é atual sem poder garantir isso. |
| **RN-008** | Se a consulta ao fornecedor estiver indisponível, o simulador não apresenta uma cotação anterior como alternativa. |
| **RN-009** | Somente `PETR4`, `VALE3` e `ITUB4` são reconhecidos pelo simulador. Outros códigos são tratados como ativos não suportados. |
| **RN-010** | Um ativo suportado pode estar temporariamente sem cotação disponível. Isso é diferente de um ativo não suportado. |
| **RN-011** | Dados de cotação inválidos não podem ser apresentados como uma cotação válida. |

## 6. Cenários alternativos e excepcionais

| Situação | Comportamento esperado |
|---|---|
| **Ativo não suportado** | Informar que o ativo não está disponível no simulador. |
| **Ativo suportado sem cotação** | Informar que não há cotação disponível para o ativo. |
| **Fornecedor indisponível** | Informar que não foi possível consultar a cotação naquele momento; não apresentar preço anterior como substituto. |
| **Cotação antiga** | Apresentar normalmente o preço e seu horário de referência, sem classificá-lo automaticamente como atualizado. |
| **Cotação inválida** | Não apresentar os dados como cotação válida. |
| **Cotação atualizada pelo fornecedor** | Uma nova consulta pode apresentar o novo preço e seu respectivo horário de referência. |

**Distinção essencial:** uma cotação **antiga, mas obtida com sucesso**, pode ser exibida; uma cotação **anterior recuperada como substituta durante indisponibilidade do fornecedor** não será exibida neste incremento.

## 7. Critérios de aceite

| ID | Dado que... | Quando... | Então... |
|---|---|---|---|
| **CA-001** | `PETR4` é suportado e possui cotação válida | O investidor consulta `PETR4` | O sistema apresenta código, preço, moeda `BRL` e data/hora de referência. |
| **CA-002** | `ABCD9` não pertence aos ativos suportados | O investidor consulta `ABCD9` | O sistema informa que o ativo não está disponível no simulador. |
| **CA-003** | `VALE3` é suportado, mas está sem cotação disponível | O investidor consulta `VALE3` | O sistema informa que não existe cotação disponível. |
| **CA-004** | O fornecedor de Market Data está indisponível | O investidor consulta um ativo suportado | O sistema informa a impossibilidade da consulta e não apresenta uma cotação anterior. |
| **CA-005** | O fornecedor retorna uma cotação válida com horário antigo | O investidor consulta o ativo | O sistema apresenta o preço e o horário informado, sem rejeitar por idade. |
| **CA-006** | O fornecedor retorna um preço igual ou inferior a zero | O investidor consulta o ativo | O sistema não apresenta o valor como cotação válida. |
| **CA-007** | O fornecedor passou a disponibilizar uma nova cotação válida | O investidor realiza uma nova consulta | O sistema apresenta a nova cotação recebida, com preço e horário de referência. |

## 8. Restrições de qualidade e decisões ainda não necessárias

- **Clareza da informação:** preço e horário de referência devem ser apresentados juntos; não prometer tempo real.
- **Comportamento diante de falhas:** diferenciar ativo não suportado, ausência de cotação, dados inválidos e indisponibilidade do fornecedor.
- **Sem metas arbitrárias:** ainda não foram estabelecidos tempos máximos de resposta, percentuais de disponibilidade ou um limite de atualização de preços.
- **Independência de implementação:** formato da API, códigos de resposta, contrato do fornecedor simulado e timeouts estão registrados na [arquitetura inicial](../architecture/initial-architecture.md). Esses detalhes técnicos preservam as regras e os critérios funcionais deste documento.

## 9. Fora do escopo do incremento

- Compra, venda ou envio de ordens.
- Saldo virtual e carteira de investimentos.
- Consulta de histórico e gráficos de cotações.
- Autenticação, contas e perfis de investidores.
- Inclusão dinâmica de ativos.
- Ativos internacionais, outras classes de investimento e câmbio.
- Acompanhamento contínuo de preços sem solicitação do investidor.
- Integração com bolsa ou corretora real.

## 10. Direção futura (não aprovada como funcionalidade)

O produto poderá evoluir para operações fictícias, consulta de carteira e acompanhamento patrimonial. Essas capacidades serão refinadas apenas quando selecionadas para um próximo incremento.

---

**Verificação da entrega:** os critérios `CA-001` a `CA-007` e os cenários adversos definidos foram implementados e verificados. O [relatório dos testes](../testing/uc-001-tests.md) relaciona os critérios às evidências automatizadas, registra as falhas corrigidas e informa os comandos de execução. A evolução do projeto permanece sujeita ao escopo aprovado para cada próximo incremento.
