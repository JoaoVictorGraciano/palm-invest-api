# palm-invest-api

[![CI](https://github.com/JoaoVictorGraciano/palm-invest-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/JoaoVictorGraciano/palm-invest-api/actions/workflows/ci.yml)

API REST da carteira de investimentos **Palm Invest**. Guarda usuários, suas transações de compra e venda
(ações, FIIs, ETFs e BDRs da B3 e criptomoedas) e os proventos recebidos; o front em `../palm-invest` deriva carteira,
custo médio e histórico a partir delas.

Também busca ativos e cotações na Brapi e na CoinGecko, com cache em memória, para o front não chamar esses provedores
direto do navegador.

Stack: Java 21, Spring Boot 4, Spring Security + JWT (jjwt), Spring Data JPA, Flyway, PostgreSQL, RestClient e
Caffeine. Testes com JUnit 5, MockMvc, H2 em memória e um servidor HTTP local no lugar dos provedores.

## Pré-requisitos

- JDK 21
- PostgreSQL com um banco chamado `palm_invest` (`CREATE DATABASE palm_invest;`)
- Nada de Maven instalado: use o wrapper `mvnw` / `mvnw.cmd`

## Variáveis de ambiente

Nenhum segredo fica no código ou em arquivo versionado. A API lê tudo do ambiente:

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_USER` | sim | — | usuário do PostgreSQL |
| `DB_PASSWORD` | sim | — | senha do PostgreSQL |
| `JWT_SECRET` | sim | — | chave HMAC do token, em **Base64**, com pelo menos 32 bytes (256 bits). A API não sobe sem ela ou com chave curta |
| `JWT_EXPIRATION_HOURS` | não | `2` | validade do token |
| `CORS_ORIGINS` | não | `http://localhost:5173` | origens liberadas no navegador, separadas por vírgula |
| `BRAPI_TOKEN` | não | — | token da [Brapi](https://brapi.dev) (busca e cotação da B3). Sem ele valem os limites anônimos, que só cotam alguns tickers de teste |
| `COINGECKO_API_KEY` | não | — | chave do plano **Demo** da [CoinGecko](https://www.coingecko.com/en/api) (busca e cotação de cripto), enviada no header `x-cg-demo-api-key`. Sem ela vale o limite público |
| `BRAPI_MAX_PARALELAS` | não | `2` | quantas chamadas à Brapi podem estar em andamento ao mesmo tempo. A Brapi recusa rajadas com `429`; a API também repete o `429` até 2 vezes, com espera curta |
| `CACHE_COTACAO_ACOES` | não | `30m` | tempo em cache da cotação de ações, FIIs, ETFs e BDRs (o plano gratuito da Brapi já tem ~30 min de atraso) |
| `CACHE_COTACAO_CRIPTO` | não | `5m` | tempo em cache da cotação de criptomoedas |
| `CACHE_BUSCA` | não | `10m` | tempo em cache das sugestões da busca de ativos |
| `CACHE_ID_CRIPTO` | não | `24h` | tempo em cache do mapa símbolo → id da CoinGecko |

Os tempos de cache aceitam o formato de duração do Spring (`90s`, `5m`, `2h`). Token e chave vão só em header, nunca
na URL, e nunca são registrados em log.

Gerando uma chave:

```bash
openssl rand -base64 32
```

No Windows, defina as variáveis nas Variáveis de Ambiente do usuário (e abra um terminal novo) ou na sessão:

```powershell
$env:DB_USER = "postgres"; $env:DB_PASSWORD = "..."; $env:JWT_SECRET = "..."
```

## Subir a API

```powershell
.\mvnw.cmd spring-boot:run
```

Sobe em `http://localhost:8080`. Na primeira subida o Flyway cria a tabela `flyway_schema_history` e aplica as
migrações pendentes (ver abaixo).

## Banco de dados e migrações

O schema é versionado pelo **Flyway** em `src/main/resources/db/migration`. O Hibernate roda com `ddl-auto=validate`:
ele só confere se as entidades batem com o banco e falha na subida se não baterem. Nunca altere o banco à mão; crie
`V<n>__descricao.sql`.

- `V1__baseline.sql` é o schema como existia antes do Flyway. Em bancos que já tinham as tabelas, ele é marcado como
  aplicado sem executar (`baseline-on-migrate`); em bancos vazios e no H2 dos testes, ele roda.
- As migrações são escritas no subconjunto de SQL comum a PostgreSQL e H2, porque os testes as executam no H2.
- Toda migração que mexe em dados existentes (ex.: `V4__asset_class.sql`) deve ser idempotente e explicar a regra
  num comentário.

## Testes

```powershell
.\mvnw.cmd test
```

Rodam sem PostgreSQL, sem internet e sem variáveis de ambiente: o perfil `test` usa H2 em memória (schema criado pelo
Flyway) e uma `JWT_SECRET` aleatória gerada a cada execução. A Brapi e a CoinGecko são simuladas por um servidor HTTP
local (`ProvedoresFalsos`), com token e chave aleatórios; `BRAPI_TOKEN` e `COINGECKO_API_KEY` do ambiente nunca são
usados nos testes. A classe base `IntegracaoTestBase` recusa limpar o banco se a conexão não for `jdbc:h2:mem:`.

## Rotas

Todas as rotas, exceto login e cadastro, exigem `Authorization: Bearer <token>`.

### Autenticação (`/auth`)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/auth/cadastrar` | `{nome, email, senha}` (senha de 8 a 72 caracteres) | `201 {id, nome, email}`; `409` se o e-mail já existe |
| `POST` | `/auth/login` | `{email, senha}` | `200 {token, expiresIn}` (`expiresIn` em segundos); `401` se inválido |
| `GET` | `/auth/me` | — | `200 {id, nome, email}` do dono do token |

O e-mail é normalizado (sem espaços nas pontas, minúsculas) no cadastro e no login.

### Transações (`/api/transactions`)

Cada usuário só enxerga as próprias transações. Uma transação de outro usuário responde `404`, nunca `403`.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/transactions` | lista do usuário, da mais recente para a mais antiga |
| `POST` | `/api/transactions` | cria; o dono vem do token e o `id` é gerado pelo banco |
| `PUT` | `/api/transactions/{id}` | substitui todos os campos |
| `DELETE` | `/api/transactions/{id}` | apaga uma transação |
| `DELETE` | `/api/transactions?assetSymbol=PETR4` | apaga **todas** as transações e **todos os proventos** do ativo numa única transação de banco; `200 {assetSymbol, excluidas, proventosExcluidos}` ou `404` se não havia nenhum dos dois |

Corpo do `POST`/`PUT` (sem `id`):

```json
{
  "type": "BUY",
  "assetSymbol": "PETR4",
  "assetClass": "STOCK",
  "quantity": 10.5,
  "price": 38.20,
  "transactionDate": "2025-03-10T13:00:00",
  "broker": "CLEAR",
  "fee": 1.50,
  "observation": "APORTE MENSAL"
}
```

| Campo | Regras |
|---|---|
| `type` | `BUY` ou `SELL` |
| `assetSymbol` | obrigatório, até 20 caracteres; normalizado para maiúsculas sem espaços |
| `assetClass` | `STOCK`, `CRYPTO`, `FII`, `ETF` ou `BDR`, obrigatório |
| `quantity`, `price` | maiores que zero, até 8 casas decimais (`numeric(19,8)`) |
| `transactionDate` | data/hora local sem fuso, não pode ser futura |
| `broker` | opcional, até 100 |
| `fee` | opcional, zero ou mais, até 2 casas |
| `observation` | opcional, até 255 |

**Regra de saldo:** o servidor percorre o histórico do ativo em ordem de data (compra antes de venda no mesmo
instante) como ele ficaria após a operação e recusa com `422` qualquer criação, edição ou exclusão que deixe a
quantidade em carteira negativa em algum ponto. Ex.: vender sem ter comprado, vender antes da data da compra, reduzir
ou excluir uma compra que sustenta uma venda posterior.

### Proventos (`/api/proventos`)

Dividendos, JCP, rendimentos de FII e amortizações recebidos. Como nas transações, cada usuário só enxerga os próprios
proventos e um provento de outro usuário responde `404`.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/proventos?ano=2025&assetSymbol=PETR4` | lista do usuário, do pagamento mais recente para o mais antigo (id desempata o mesmo dia); `ano` e `assetSymbol` são opcionais |
| `POST` | `/api/proventos` | registra; `201` com o provento |
| `PUT` | `/api/proventos/{id}` | substitui todos os campos |
| `DELETE` | `/api/proventos/{id}` | apaga um provento; `204` |
| `GET` | `/api/proventos/resumo?ano=2025` | totais líquidos do ano (ver abaixo); `ano` obrigatório |

Corpo do `POST`/`PUT` (sem `id` e sem `assetClass`):

```json
{
  "assetSymbol": "PETR4",
  "tipo": "JCP",
  "dataPagamento": "2025-03-20",
  "valorBruto": 50.00,
  "irRetido": 7.50,
  "observacao": "JCP 1T25"
}
```

| Campo | Regras |
|---|---|
| `assetSymbol` | obrigatório, até 20 caracteres; normalizado para maiúsculas sem espaços |
| `tipo` | `DIVIDENDO`, `JCP`, `RENDIMENTO` ou `AMORTIZACAO` |
| `dataPagamento` | data (`AAAA-MM-DD`), não pode ser futura |
| `valorBruto` | maior que zero, até 2 casas (`numeric(19,2)`) |
| `irRetido` | opcional (padrão `0`), zero ou mais, até 2 casas e no máximo igual ao `valorBruto` |
| `observacao` | opcional, até 255 |

Resposta: `{id, assetSymbol, assetClass, tipo, dataPagamento, valorBruto, irRetido, valorLiquido, observacao}`.
O `valorLiquido` (`valorBruto - irRetido`) é calculado pela API, não é gravado.

**Regra de carteira:** só é possível registrar (ou editar para) um provento de um ativo em que o usuário tenha alguma
transação com data até o fim do dia do pagamento; senão a resposta é `422 {"mensagem"}`. A `assetClass` do provento é
copiada da transação mais recente do ativo até essa data, e não vem do corpo.

**Resumo** (`GET /api/proventos/resumo?ano=2025`), sempre em valores líquidos:

```json
{
  "ano": 2025,
  "totalBruto": 380.00, "totalIrRetido": 30.00, "totalLiquido": 350.00,
  "porMes":   [{"mes": 1, "totalLiquido": 100.00}, {"mes": 2, "totalLiquido": 0.00}, "... os 12 meses"],
  "porAtivo": [{"assetSymbol": "PETR4", "assetClass": "STOCK", "totalLiquido": 270.00}, "... do maior para o menor"],
  "porTipo":  [{"tipo": "DIVIDENDO", "totalLiquido": 100.00}, "... os 4 tipos, com zero quando não houve"]
}
```

Excluir o ativo inteiro (`DELETE /api/transactions?assetSymbol=X`) apaga também os proventos dele. Excluir uma
transação isolada não apaga proventos já registrados.

### Busca de ativos e cotações

A API consulta a Brapi (ações, FIIs, ETFs e BDRs da B3) e a CoinGecko (criptomoedas) pelo front, que não chama mais
esses provedores direto do navegador.

| Método | Rota | Resposta |
|---|---|---|
| `GET` | `/api/assets/search?q=petr&class=ALL` | `200 {suggestions: [{symbol, name, assetClass}], failedProviders: ["brapi" \| "coingecko"]}` |
| `GET` | `/api/quotes?symbols=PETR4,HGLG11,BTC,ENA` | `200 {quotes: [{symbol, price, currency, provider, updatedAt}], failures: [{symbol, provider, mensagem}]}` |

**Busca** (`q` de 2 a 50 caracteres; `class` = `ALL`, `STOCK`, `FII`, `ETF`, `BDR` ou `CRYPTO`, padrão `ALL`):
- `ALL` consulta os dois provedores; `CRYPTO` só a CoinGecko; as demais só a Brapi, devolvendo apenas a classe pedida.
- A classe dos ativos da Brapi vem de `type`/`subType` (`stock`/`unit` → STOCK, `bdr` → BDR, `fund`+`etf` → ETF, demais
  fundos → FII). Na CoinGecko fica uma moeda por símbolo: a de maior valor de mercado.
- Ordem: símbolo igual ao texto, depois símbolo ou nome começando com o texto, depois o resto; empates mantêm a ordem
  do provedor. No máximo 6 sugestões por provedor.

**Cotações** (até 50 símbolos, sempre em reais, na ordem pedida):
- O provedor de cada símbolo vem da classe que o usuário registrou nas transações dele. Para um símbolo que ele ainda
  não tem, o formato de ticker da B3 (`PETR4`, `HGLG11`, `B3SA3`, `AAPL34`) vai para a Brapi e o resto para a CoinGecko.
- Brapi: uma chamada por ativo, no máximo `BRAPI_MAX_PARALELAS` ao mesmo tempo, com até 2 repetições em `429`.
  CoinGecko: todas as moedas numa única chamada; o id de cada símbolo é o da moeda de maior valor de mercado.
- `updatedAt` é o horário informado pelo provedor.

**Falhas:** um provedor fora do ar não derruba o outro: busca devolve o provedor em `failedProviders` e cotação devolve
os símbolos dele em `failures`. Símbolo inexistente também vai em `failures`, com `200`. Só quando todos os provedores
consultados estão indisponíveis e nada pôde ser devolvido a resposta é `503 {"mensagem"}`.

**Cache em memória**, compartilhado entre todos os usuários (tempos nas variáveis `CACHE_*`): cotação de ações 30 min,
cripto 5 min (só as moedas que faltam são pedidas, numa única chamada), buscas 10 min e símbolo → id da CoinGecko
24 h (alimentado também pelas buscas de cripto). Falhas nunca ficam no cache. Cada chamada real a um provedor é
registrada em log com método, caminho, status, tempo e o total por provedor desde a subida, para acompanhar a cota:

```
Chamada à Brapi nº 12: GET /api/quote/PETR4 -> HTTP 200 em 284 ms | total desde a subida: Brapi 12, CoinGecko 4
```

### Erros

| Status | Quando | Corpo |
|---|---|---|
| `400` | validação, JSON inválido, enum fora dos valores, tipo errado no path, parâmetro ausente ou inválido | `[{"campo", "mensagem"}]` ou `{"mensagem"}` |
| `401` | sem token, token inválido/expirado, login errado | `{"mensagem"}` (vazio quando é o filtro que barra) |
| `404` | recurso inexistente ou de outro usuário, rota inexistente | `{"mensagem"}` ou vazio |
| `405` | método não suportado | `{"mensagem"}` |
| `409` | e-mail já cadastrado | `{"mensagem"}` |
| `422` | regra de saldo violada; provento de ativo que o usuário não tinha na data do pagamento | `{"mensagem"}` |
| `503` | Brapi e CoinGecko indisponíveis e nada a devolver na busca ou na cotação | `{"mensagem"}` |
| `500` | falha inesperada; o detalhe fica só no log | `{"mensagem": "Erro interno no servidor"}` |

## CORS

Só a origem configurada em `CORS_ORIGINS` pode chamar a API pelo navegador, com `GET, POST, PUT, DELETE, OPTIONS` e
os headers `Authorization` e `Content-Type`. O preflight é respondido antes da autenticação.
