# palm-invest-api

API REST da carteira de investimentos **Palm Invest**. Guarda usuários e suas transações de compra e venda
(ações da B3 e criptomoedas); o front em `../palm-invest` deriva carteira, custo médio e histórico a partir delas.

Stack: Java 21, Spring Boot 4, Spring Security + JWT (jjwt), Spring Data JPA, Flyway, PostgreSQL. Testes com JUnit 5,
MockMvc e H2 em memória.

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

Rodam sem PostgreSQL e sem variáveis de ambiente: o perfil `test` usa H2 em memória (schema criado pelo Flyway) e uma
`JWT_SECRET` aleatória gerada a cada execução. A classe base `IntegracaoTestBase` recusa limpar o banco se a conexão
não for `jdbc:h2:mem:`.

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
| `DELETE` | `/api/transactions?assetSymbol=PETR4` | apaga **todas** as transações do ativo numa única transação de banco; `200 {assetSymbol, excluidas}` ou `404` |

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
| `assetClass` | `STOCK` ou `CRYPTO`, obrigatório |
| `quantity`, `price` | maiores que zero, até 8 casas decimais (`numeric(19,8)`) |
| `transactionDate` | data/hora local sem fuso, não pode ser futura |
| `broker` | opcional, até 100 |
| `fee` | opcional, zero ou mais, até 2 casas |
| `observation` | opcional, até 255 |

**Regra de saldo:** o servidor percorre o histórico do ativo em ordem de data (compra antes de venda no mesmo
instante) como ele ficaria após a operação e recusa com `422` qualquer criação, edição ou exclusão que deixe a
quantidade em carteira negativa em algum ponto. Ex.: vender sem ter comprado, vender antes da data da compra, reduzir
ou excluir uma compra que sustenta uma venda posterior.

### Erros

| Status | Quando | Corpo |
|---|---|---|
| `400` | validação, JSON inválido, enum fora dos valores, tipo errado no path, parâmetro ausente | `[{"campo", "mensagem"}]` ou `{"mensagem"}` |
| `401` | sem token, token inválido/expirado, login errado | `{"mensagem"}` (vazio quando é o filtro que barra) |
| `404` | recurso inexistente ou de outro usuário, rota inexistente | `{"mensagem"}` ou vazio |
| `405` | método não suportado | `{"mensagem"}` |
| `409` | e-mail já cadastrado | `{"mensagem"}` |
| `422` | regra de saldo violada | `{"mensagem"}` |
| `500` | falha inesperada; o detalhe fica só no log | `{"mensagem": "Erro interno no servidor"}` |

## CORS

Só a origem configurada em `CORS_ORIGINS` pode chamar a API pelo navegador, com `GET, POST, PUT, DELETE, OPTIONS` e
os headers `Authorization` e `Content-Type`. O preflight é respondido antes da autenticação.
