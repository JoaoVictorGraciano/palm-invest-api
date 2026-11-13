# Regras do projeto palm-invest-api

API Spring Boot 4 / Java 21 da carteira Palm Invest. O front está em `../palm-invest` (React + Vite); leia-o quando
uma mudança afetar o contrato (campos, valores de enum, status de erro). O `README.md` descreve rotas, campos,
variáveis de ambiente e como rodar.

## Segurança

- **Nunca** coloque senha, token, chave JWT ou credencial de banco no código, em `application*.properties`, em
  migração, em teste ou em commit. Tudo vem de variável de ambiente (`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
  `JWT_EXPIRATION_HOURS`, `CORS_ORIGINS`). Nos testes a chave é gerada aleatoriamente em `IntegracaoTestBase`.
- Não imprima valores de segredo em logs, comandos ou respostas; verifique só o formato (ex.: tamanho do Base64).
- Rotas novas são protegidas por padrão (`anyRequest().authenticated()`); liberar uma rota é decisão explícita em
  `SecurityConfigurations`.
- Recurso de outro usuário responde `404`, nunca `403`, para não revelar que existe. Toda consulta de transação filtra
  pelo `Usuario` do token (`@AuthenticationPrincipal`); o dono nunca vem do corpo da requisição.

## Dados e banco

- Schema só muda por migração Flyway em `src/main/resources/db/migration` (`V<n>__descricao.sql`). `ddl-auto` é
  `validate` em todos os perfis. Nunca peça ao usuário para rodar `ALTER TABLE` à mão.
- Migrações usam SQL comum a PostgreSQL e H2 (os testes as executam no H2 em modo PostgreSQL). Enum no banco é
  `VARCHAR` + `CHECK`, não tipo nativo.
- Migração que mexe em dados existentes deve ser idempotente, explicar a regra em comentário e falhar de propósito
  diante de valor inesperado, em vez de adivinhar.
- Dinheiro e quantidade são `BigDecimal`; compare com `compareTo`, nunca com `equals` nem passando por `double`.
- O banco local do usuário tem dados reais dele. Antes de propor uma migração de dados, peça uma consulta
  **somente leitura** que mostre o resultado esperado e espere a confirmação.

## Código

- Controllers só fazem HTTP; regra de negócio fica em `service` (ex.: `TransactionService`, transacional).
- Entidades nunca saem nem entram pela API: entrada é `Dados*DTO` com Bean Validation (sem `id`), saída é
  `DadosDetalhamento*DTO`. Normalizações (e-mail, `assetSymbol`) ficam no construtor compacto do record de entrada.
- Erros passam pelo `TratadorDeErros`: validação `400 [{campo, mensagem}]`, conflito `409`, regra de negócio
  `422` (`RegraDeNegocioException`), inesperado `500` genérico com detalhe só no log. Mantenha o formato
  `{"mensagem"}`, que o front já lê.
- Valores de enum (`BUY/SELL`, `STOCK/CRYPTO`) são os mesmos do front; não traduza nem aceite sinônimos.
- Comentários em português, no tom dos existentes; código em inglês onde já é inglês (entidade `Transaction`) e
  português onde já é português (`Usuario`, DTOs). Não misture dentro do mesmo arquivo.

## Testes

- `.\mvnw.cmd test` precisa passar sem PostgreSQL e sem variáveis de ambiente. Todo teste de integração estende
  `IntegracaoTestBase` (H2, MockMvc, helpers de cadastro/login/transação).
- Toda regra nova vem com teste de integração que exercita a rota, inclusive o caso de outro usuário.
- Se um teste falhar, pare e mostre a saída; não comente, não apague e não afrouxe a asserção.

## Fluxo de trabalho

- Proponha um plano em passos pequenos e espere aprovação antes de implementar. Para cada passo: arquivos, como
  testar e se exige algo no banco do usuário.
- Um commit por passo, mensagem em português no imperativo (`feat:`, `fix:`, `test:`, `chore:`, `build:`), rodando
  a suíte antes de cada commit. Nunca faça `push`, merge ou abra PR sem pedido explícito.
- Pare e avise quando: um teste falhar, um passo exigir decisão do usuário, ou algo precisar ser conferido no banco
  real.
- Ao mudar o contrato da API, liste no final o que o front precisa acompanhar.
