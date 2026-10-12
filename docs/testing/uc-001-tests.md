# Verificação da UC-001

**Data da execução registrada:** 12/10/2026  
**Escopo:** critérios de aceite e cenários adversos da [UC-001](../use-cases/uc-001-consultar-cotacao.md).  
**Contrato:** [arquitetura inicial](../architecture/initial-architecture.md).

## Organização dos testes

Os testes existentes de domínio e aplicação permanecem inalterados. Foram acrescentadas duas classes:

- `MarketDataHttpAdapterTest`: chamadas HTTP reais ao WireMock, com JUnit Jupiter e sem contexto Spring. Verifica conversão para tipos internos, ausência confirmada, falhas tipadas e preservação das causas técnicas.
- `QuoteApiTest`: aplicação Spring Boot completa em porta aleatória, WireMock em outra porta aleatória e chamadas reais com `RestTestClient`. Verifica o contrato JSON público e o tratamento de erros no fluxo inteiro.

WireMock 3.13.2 foi adicionado ao Maven como `wiremock-standalone`, somente no escopo `test`. Seus componentes empacotados ficam isolados das bibliotecas da aplicação. Não é necessário iniciar Docker, WireMock manual ou a aplicação no IntelliJ para executar os testes.

Cada teste reinicia os stubs e o histórico de requisições de seu próprio WireMock. Os servidores são encerrados ao final. Os arquivos de `wiremock/mappings/` usados manualmente não são alterados.

Na API, somente a URL do fornecedor é substituída. Os timeouts de conexão e leitura continuam vindo de `application.yaml`. O cliente HTTP do teste aguarda até 10 segundos, para receber o `503` gerado pelo timeout de leitura de 2 segundos da aplicação. No teste isolado de timeout do adaptador, o limite de leitura é reduzido para 300 ms e a resposta é atrasada em 3 segundos.

## Critérios de aceite

| Critério | Evidência principal em `QuoteApiTest` |
|---|---|
| `CA-001`: cotação válida com os quatro campos públicos | `returnsPublicQuoteContractForSupportedTicker` |
| `CA-002`: ticker não suportado sem consulta externa | `rejectsUnsupportedTickerWithoutCallingProvider` |
| `CA-003`: ativo suportado sem cotação | `returnsNotFoundOnlyForConfirmedMissingQuote` |
| `CA-004`: indisponibilidade sem fallback | `returnsServiceUnavailableForProvider503`, `doesNotReturnPreviousQuoteDuringProviderFailure` |
| `CA-005`: cotação antiga aceita com horário original | `returnsOldQuoteWithItsOriginalReferenceTime` |
| `CA-006`: preço zero ou negativo rejeitado | `returnsInvalidDataProblemForNonPositivePrice` |
| `CA-007`: nova consulta recebe cotação atualizada | `returnsUpdatedQuoteOnNewRequest` |

Os testes do adaptador acrescentam verificação do cabeçalho `Accept`, precisão monetária, ticker divergente, campos obrigatórios ausentes/nulos, moeda inválida, corpo vazio, JSON malformado, falha de conversão, timeout de leitura, conexão interrompida e causas preservadas. O `404` confirmado é comparado a respostas com `type`, `code`, status ou Content-Type incorretos.

Na API, um caminho público inexistente não recebe `QUOTE_NOT_FOUND`. Um `404` inesperado do fornecedor resulta em falha de integração, não em cotação ausente. As mensagens públicas são verificadas integralmente, com ausência de stack trace, classe da exceção e preço nas respostas de erro.

## Executar

No diretório `investment-simulator/`, com Java 25 configurado:

```powershell
.\mvnw.cmd verify
```

Para executar somente as duas novas classes:

```powershell
.\mvnw.cmd '-Dtest=MarketDataHttpAdapterTest,QuoteApiTest' test
```

Os resultados detalhados ficam em `investment-simulator/target/surefire-reports/`.

Se houver falhas, Maven interrompe `verify` antes da fase do relatório JaCoCo. A coleta já realizada pode ser convertida em relatório sem executar novamente os testes:

```powershell
.\mvnw.cmd jacoco:report
```

O relatório HTML fica em `investment-simulator/target/site/jacoco/index.html`. Gerar o relatório não altera o resultado dos testes nem torna a suíte aprovada.

## Resultado registrado

Na verificação após a correção do adaptador, foram executados **103 testes: todos passaram**, sem falhas, erros de execução ou testes ignorados. As duas classes HTTP acrescentam 65 cenários aos testes existentes:

| Grupo | Executados | Passaram | Falharam |
|---|---:|---:|---:|
| Testes existentes de domínio, aplicação e inicialização | 38 | 38 | 0 |
| `MarketDataHttpAdapterTest` | 41 | 41 | 0 |
| `QuoteApiTest` | 24 | 24 | 0 |
| **Total** | **103** | **103** | **0** |

A compilação passou e `verify` terminou com `BUILD SUCCESS`, gerando o JAR executável e os relatórios JaCoCo. O resultado foi confirmado por uma nova execução completa, após o responsável ajustar o código de produção.

Após a extração de `MarketDataQuoteMapper`, uma nova execução de `verify` confirmou os mesmos 103 testes aprovados. O teste isolado do adaptador instancia o mapper real, e os testes da API verificam sua composição via Spring. As asserções e os contratos HTTP foram preservados na refatoração.

### Falha identificada e corrigida

Na primeira execução, 94 testes passaram e 9 falharam. O adaptador não traduzia falhas ocorridas durante a extração/conversão de respostas HTTP `200` para `InvalidQuoteDataException`. A falha ocorria em `.body(MarketDataQuoteResponse.class)`, antes de `toQuote()`.

Cinco testes do adaptador expuseram essa lacuna:

- JSON malformado;
- array JSON no lugar do objeto esperado;
- preço que não pode ser convertido em número;
- timestamp que não pode ser convertido em `Instant`;
- Content-Type inesperado, sem conversor compatível.

Nesses casos, escapavam `RestClientException` ou `UnknownContentTypeException`. Quatro testes da API exercitam JSON malformado, array, timestamp inválido e Content-Type inesperado. Antes da correção, recebiam `500`, mas com `application/json` e sem o contrato `INVALID_QUOTE_DATA` aprovado para Problem Details.

O responsável implementou a tradução de `UnknownContentTypeException` para `InvalidQuoteDataException` e, no tratamento de `RestClientException`, utiliza `contains(HttpMessageNotReadableException.class)` para identificar falhas de conversão na cadeia de causas. A exceção original é preservada como causa. Os tratamentos específicos de `404`, `503` e comunicação continuam anteriores ao tratamento genérico.

As nove asserções passaram após esse ajuste, incluindo a resposta pública com `application/problem+json`, código e mensagem aprovados. Nenhum teste foi ignorado ou flexibilizado para aceitar o desvio anterior.

Os testes implementados verificam os critérios `CA-001` a `CA-007` e os cenários adversos definidos neste documento. Todos passaram na execução registrada. Cobertura de linhas é diagnóstico e não substitui essas asserções de comportamento.
