# EXP-001 — Estudo de Actuator, Micrometer e Prometheus

**Data:** 24/10/2026  
**Contexto:** UC-001 — Consulta de cotação; etapas 4 e 5 do desenvolvimento guiado.  
**Finalidade:** revisar os conceitos e reproduzir as consultas aprendidas. Esta é uma nota de estudo em evolução, não o relatório final do experimento.

## 1. O problema que estamos aprendendo a observar

A API recebe `GET /v1/quotes/PETR4`, consulta o WireMock por HTTP e devolve a cotação sintética. Observar somente o tempo percebido pelo consumidor não mostra onde esse tempo foi gasto.

Queremos acompanhar requisições recebidas, chamadas ao fornecedor, CPU, memória e threads. Mais adiante, compararemos essas medições sob carga controlada. Até aqui, validamos a instrumentação e a coleta; não identificamos capacidade máxima nem comprovamos gargalos.

Arquivos relacionados:

- [Dependências Maven](../../investment-simulator/pom.xml).
- [Configuração da aplicação](../../investment-simulator/src/main/resources/application.yaml).
- [Configuração de coleta](../../observability/prometheus/prometheus.yml).
- [Docker Compose](../../compose.yaml).
- [Script k6](../../performance/k6/quote.js).

## 2. Quem faz o quê

| Componente | Responsabilidade | Como apareceu no laboratório |
|---|---|---|
| Actuator | Disponibilizar recursos de monitoramento do Spring Boot | Endpoints de saúde, inspeção e exportação de métricas |
| Micrometer | Registrar medições por uma API comum e integrá-las a sistemas de monitoramento | Tempos HTTP, memória, CPU e métricas do Tomcat |
| Registry Prometheus | Adaptar a exportação do Micrometer para o formato coletado pelo Prometheus | Dependência `micrometer-registry-prometheus` |
| Prometheus | Coletar periodicamente, armazenar séries temporais e executar PromQL | Serviço Docker e interface na porta 9090 |
| Grafana | Consultar uma fonte de dados e apresentar dashboards | Próxima etapa; ainda não configurado nesta nota |
| k6 | Gerar requisições e medir o comportamento visto pelo consumidor | Script de consulta de cotação executado sob demanda |

Adicionar o registry à aplicação não instala o servidor Prometheus. São componentes separados: a aplicação publica as medições, e o servidor vai buscá-las. O Spring Boot integra o Micrometer e configura os registries encontrados no classpath. [Referência de métricas do Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).

```text
k6 → Spring Boot no Windows → WireMock no Docker
           │
           └─ Micrometer registra medições
                    │
               Actuator expõe
             /actuator/prometheus
                    ↑
        Prometheus no Docker coleta a cada 15 s
                    ↑
          Grafana consultará os dados
```

## 3. Actuator: saúde, inspeção e exportação

Incluímos `spring-boot-starter-actuator` no Maven, com versão gerenciada pelo parent do Spring Boot.

| Endpoint | Finalidade | Formato observado |
|---|---|---|
| `/actuator/health` | Consultar o estado dos indicadores de saúde configurados | JSON, como `{"status":"UP"}` |
| `/actuator/metrics` | Listar nomes dos medidores disponíveis | JSON com `names` |
| `/actuator/metrics/jvm.memory.used` | Inspecionar um medidor | JSON com unidade, medições e tags |
| `/actuator/prometheus` | Publicar métricas para coleta | Texto com séries e metadados como `# HELP` e `# TYPE` |

Por padrão, somente `health` é exposto por HTTP. Nossa configuração explicita os três endpoints usados:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

Exposição é tornar o endpoint acessível; não é o mesmo que criar um medidor. Determinadas métricas HTTP aparecem somente depois de uma operação ser registrada. [Endpoints do Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

Comandos de inspeção no Windows:

```powershell
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/actuator/metrics
curl.exe -i http://localhost:8080/actuator/metrics/jvm.memory.used
curl.exe -i http://localhost:8080/actuator/prometheus
```

No JSON de um medidor:

- `baseUnit` informa a unidade; memória foi apresentada em bytes e durações HTTP em segundos.
- `measurements` contém estatísticas como `VALUE`, `COUNT`, `TOTAL_TIME` e `MAX`, conforme o tipo de medidor.
- `availableTags` apresenta dimensões disponíveis para filtros.

Sem filtros, `jvm.memory.used` pode agregar diferentes pools e áreas: o valor não representa somente o heap.

## 4. Métricas de entrada, saída e threads

| Nome no Actuator | O que observamos |
|---|---|
| `http.server.requests` | Duração de requisições concluídas atendidas pela aplicação |
| `http.client.requests` | Duração das chamadas HTTP realizadas pela aplicação |
| `process.cpu.usage` | Utilização recente e normalizada de CPU do processo |
| `system.cpu.usage` | Utilização de CPU do sistema reportada pelo runtime |
| `jvm.memory.used` | Memória usada nos pools da JVM |
| `jvm.gc.*` | Informações sobre alocação e coleta de lixo |
| `tomcat.threads.busy` | Threads ocupadas no pool do conector observado |
| `tomcat.threads.current` | Threads existentes nesse pool |
| `tomcat.threads.config.max` | Limite configurado para esse pool |

O projeto constrói o `RestClient` com o builder injetado pelo Spring em `QuoteConfiguration`, aproveitando a instrumentação automática. Não adicionamos temporizadores manuais ao caso de uso.

Entrada e saída medem escopos diferentes. O tempo de entrada inclui o atendimento da API; o de saída observa a integração HTTP. Subtrair os tempos não demonstra, por si só, tempo de fila.

Para disponibilizar métricas internas do Tomcat, habilitamos seu registro de MBeans:

```yaml
server:
  tomcat:
    mbeanregistry:
      enabled: true
```

Esse registro permite consultar informações de gerenciamento dentro da JVM. Não foi necessário abrir uma porta de JMX remoto. [Registro de MBeans do Tomcat](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.enable-tomcat-mbean-registry).

Uma thread pode estar ocupada esperando HTTP e consumir pouca CPU. Além disso, `jvm.threads.live` conta threads da JVM inteira; não equivale às threads ocupadas do Tomcat.

## 5. Prometheus: scrape, job, target e série temporal

**Scrape** é uma coleta: o Prometheus consulta um endpoint e registra amostras. **Target** é o alvo consultado. **Job** é o agrupamento de alvos identificado na configuração.

Usamos:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: investment-simulator
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - host.docker.internal:8080
```

Uma série temporal é identificada pelo nome da métrica e seu conjunto de labels. Por exemplo:

```text
http_server_requests_seconds_count{
  job="investment-simulator",
  instance="host.docker.internal:8080",
  method="GET",
  status="200",
  uri="/v1/quotes/{ticker}",
  ...
}
```

Labels diferentes geram séries diferentes. `uri` usa o modelo da rota, agrupando tickers em vez de criar uma série para cada URL concreta. O medidor `http.server.requests` do Actuator aparece com nome adaptado na exportação, como `http_server_requests_seconds_count`.

O intervalo de 15 segundos é uma configuração inicial, não uma meta de performance. Coletar com maior frequência aumenta a resolução temporal e o trabalho de coleta. [Configuração do Prometheus](https://prometheus.io/docs/prometheus/latest/configuration/configuration/).

No Docker Desktop, `host.docker.internal` alcança a aplicação no Windows. Dentro do container, `localhost` identifica o próprio container. Já a aplicação no Windows continua acessando o WireMock em `localhost:8081`.

## 6. Comandos para retomar o ambiente

Na raiz do repositório, com Docker Desktop em execução:

```powershell
docker compose up -d wiremock
docker compose --profile observability pull prometheus
docker compose --profile observability config --quiet
docker compose --profile observability run --rm --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose --profile observability up -d prometheus
```

`pull` baixa a imagem; `promtool` valida o arquivo, mas não comprova conectividade. `up -d` inicia o serviço em segundo plano. A interface está em `http://localhost:9090`; os alvos podem ser consultados em `/targets` e as expressões em `/query`.

Inicie a aplicação pelo IntelliJ ou, em outro terminal dentro de `investment-simulator/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Para consultar logs do coletor:

```powershell
docker compose --profile observability logs --tail 50 prometheus
```

O volume nomeado `prometheus-data` mantém as séries quando o container é recriado. As configurações são versionadas no Git; os dados coletados ficam no volume Docker.

## 7. Consultas PromQL que executamos

Execute as expressões seguintes na interface do Prometheus. Em todas as consultas HTTP abaixo, filtramos a rota da UC-001 para separar o tráfego de negócio das coletas do Actuator.

### 7.1. A coleta está funcionando?

```promql
up{job="investment-simulator"}
```

`1` indica sucesso na coleta; `0`, falha. Isso não substitui um teste funcional da UC-001. É possível coletar métricas enquanto a consulta ao fornecedor falha.

### 7.2. Quanto de CPU o processo está utilizando?

```promql
process_cpu_usage{job="investment-simulator"}
```

O valor é uma fração normalizada. Multiplicar por 100 converte a apresentação para porcentagem. O valor observado `0.0008332319789954082` corresponde a aproximadamente `0,0833%` nessa leitura. Não é uma leitura de CPU de uma thread específica.

### 7.3. Quantas requisições foram registradas?

```promql
http_server_requests_seconds_count{
  job="investment-simulator",
  uri="/v1/quotes/{ticker}"
}
```

O resultado é acumulado por série, não uma taxa. Observamos uma série com `status="200"` e valor `2`: duas requisições registradas nela desde a inicialização do medidor.

O contador pode reiniciar quando o processo é reiniciado. Não se deve interpretar seu valor como o total histórico permanente da API. [Tipos de métrica do Prometheus](https://prometheus.io/docs/concepts/metric_types/).

### 7.4. Qual foi o throughput médio no último minuto?

```promql
sum(
  rate(http_server_requests_seconds_count{
    job="investment-simulator",
    uri="/v1/quotes/{ticker}"
  }[1m])
)
```

Leia de dentro para fora:

1. O seletor identifica os contadores da rota.
2. `[1m]` seleciona amostras na janela de um minuto anterior ao instante avaliado.
3. `rate()` estima o crescimento médio por segundo, considerando resets do contador e extrapolação para a janela.
4. `sum()` soma as taxas das séries selecionadas, inclusive diferentes status.

Aplicamos `rate()` antes da soma para tratar resets de cada série. A função precisa de pelo menos duas amostras; no nosso intervalo de coleta, uma janela de um minuto contém poucas amostras. [Função `rate`](https://prometheus.io/docs/prometheus/latest/querying/functions/#rate).

Observamos `0,32–0,33` requisição por segundo. Isso não demonstra capacidade máxima: a janela incluiu períodos sem chamadas.

### 7.5. Qual foi a latência média no último minuto?

```promql
1000 *
sum(
  rate(http_server_requests_seconds_sum{
    job="investment-simulator",
    uri="/v1/quotes/{ticker}"
  }[1m])
)
/
sum(
  rate(http_server_requests_seconds_count{
    job="investment-simulator",
    uri="/v1/quotes/{ticker}"
  }[1m])
)
```

`_sum` acumula as durações em segundos; `_count` acumula observações. A divisão das taxas produz segundos por requisição. Multiplicar por `1000` converte para milissegundos.

A soma ocorre antes da divisão para calcular a média ponderada de todas as requisições selecionadas. Fazer uma média simples das médias de séries diferentes poderia dar o mesmo peso a séries com volumes muito distintos.

Observamos aproximadamente **11,29 ms** em uma consulta. Sem requisições na janela, a divisão pode ser `0/0` e produzir `NaN`: não existe uma média definida para esse período.

### 7.6. Qual foi o p95 estimado no último minuto?

```promql
1000 *
histogram_quantile(
  0.95,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{
      job="investment-simulator",
      uri="/v1/quotes/{ticker}"
    }[1m])
  )
)
```

Um histograma clássico publica buckets cumulativos: o bucket com label `le="0.1"` conta observações de até `0,1` segundo, incluindo as que também cabem nos buckets menores. Não devemos somar buckets diferentes como se representassem faixas independentes.

`sum by (le)` agrega as séries mantendo cada limite superior. `histogram_quantile(0.95, ...)` estima o percentil a partir dessa distribuição. O resultado depende da resolução dos buckets e das observações na janela. [Função `histogram_quantile`](https://prometheus.io/docs/prometheus/latest/querying/functions/#histogram_quantile).

Observamos **117,44 ms**. Esse p95 foi consultado em momento diferente da média de 11,29 ms: não podemos afirmar que ambos descrevem o mesmo conjunto de requisições. O p95 também não é o maior tempo observado, nem um limite garantido para cada chamada.

Para exportar os buckets, configuramos:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        http.client.requests: true
```

Habilitamos apenas os dois medidores HTTP necessários. Histogramas acrescentam séries e consumo de recursos. Diferentemente de percentis calculados diretamente na aplicação, seus buckets permitem agregação no Prometheus. [Histogramas e percentis no Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).

## 8. Como gerar tráfego para verificar a coleta

```powershell
1..30 | ForEach-Object {
    curl.exe -s -o NUL http://localhost:8080/v1/quotes/PETR4
    Start-Sleep -Seconds 1
}
```

O bloco faz 30 consultas sequenciais, esperando um segundo depois de cada resposta. A frequência depende também da duração das chamadas; não é uma taxa fixa garantida de uma requisição por segundo.

É uma verificação manual, não o modelo de carga do EXP-001. O comando descarta o corpo e não valida o status; use uma chamada com `-i` para inspecionar a resposta. Nos scripts k6, os checks e thresholds fazem a validação automática.

Durante a geração, consulte o Prometheus após novas coletas. Ao terminar, `rate()` pode continuar positivo enquanto a janela contiver crescimento. Depois tende a zero. A média e o p95 podem ficar indefinidos sem novas observações.

## 9. Problemas encontrados e como reconhecer

| Sintoma | O que aprendemos a verificar |
|---|---|
| `/actuator/health` retorna `404` após adicionar a dependência | Recarregar Maven e reiniciar completamente o processo para atualizar o classpath |
| Métricas de threads do Tomcat ausentes | Conferir `server.tomcat.mbeanregistry.enabled` |
| Buckets HTTP ausentes | Conferir se `metrics` está dentro de `management`, reiniciar e gerar uma requisição |
| Consulta com URI não encontra a série | Gerar tráfego após o reinício, aguardar o scrape e conferir os labels sem o filtro |
| Só existe série de `/actuator/prometheus` | As próprias coletas estão sendo medidas; isso não comprova que houve consulta de cotação |
| Throughput retorna `0` | Conferir se houve crescimento do contador durante a janela |
| Consulta não retorna série | Distinguir ausência de dados de um valor numérico zero; conferir target, labels e amostras |
| Média ou p95 retorna `NaN` | Verificar ausência de observações na janela |

Para consultar a rota pelo Actuator, codificamos as chaves e evitamos a expansão de URL do curl:

```powershell
curl.exe -i "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/v1/quotes/%7Bticker%7D"
```

`%7B` representa `{` e `%7D` representa `}`. Isso é necessário no comando HTTP mostrado; na expressão PromQL, usamos normalmente `uri="/v1/quotes/{ticker}"`. [Expansão de URLs do curl](https://curl.se/docs/manpage.html#--globoff).

Para conferir os buckets diretamente:

```powershell
curl.exe -sS http://localhost:8080/actuator/prometheus |
    Select-String '^http_(server|client)_requests_seconds_bucket'
```

## 10. Cuidados ao revisar resultados

- Comparar média, p95, throughput e CPU na mesma janela e sob a mesma execução controlada.
- Separar aquecimento, reinícios e mudanças de configuração das medições que serão comparadas.
- Não concluir que há fila apenas porque a latência aumentou.
- Não concluir que CPU baixa significa aplicação saudável: pode haver espera por HTTP ou recursos.
- Não presumir que o Tomcat será o primeiro gargalo; considerar também cliente HTTP, WireMock e gerador de carga.
- Distinguir o tempo observado pelo k6 do tempo observado pelo servidor e pela integração; são fronteiras de medição distintas.
- Manter as consultas de negócio filtradas: a observabilidade também gera tráfego e usa CPU e memória.
- Evitar labels com valores ilimitados, como identificadores únicos por requisição, que multiplicam séries.

Os valores registrados nesta nota são observações da exploração guiada. Os cenários de carga, o dashboard Grafana e as conclusões experimentais ainda serão desenvolvidos.
