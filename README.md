# Fluveny | API

API do Fluveny: monólito modular em Java 25, Spring Boot 4.1 e Spring Modulith 2.1, sobre MongoDB. As decisões de arquitetura ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)).

## Requisitos

- **[mise](https://mise.jdx.dev)** (`brew install mise`). Ele instala o JDK 25 e o lefthook nas versões do `mise.toml`; o Maven vem pelo wrapper (`./mvnw`).
- **Docker** ligado. O Mongo local, o dos testes de integração (Testcontainers) e a imagem rodam nele. Nada de Mongo instalado na máquina.

> **Docker Desktop:** o `mongo:8` não sobe em kernels Linux 6.19 a 7.0.13 (SERVER-121912), e aí o `mise run up` e os ITs falham com "Container exited with code 1". Confira com `docker info --format '{{.KernelVersion}}'` e, se estiver na faixa, atualize o Docker Desktop para uma versão com kernel fora dela.

Sem o mise, dá para rodar o build num container:

```bash
docker run --rm -v "$PWD:/w" -w /w \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3-eclipse-temurin-25 ./mvnw -B -ntp verify
```

## Ambiente local

```bash
mise trust && mise install   # uma vez: JDK 25 e lefthook
mise run setup               # uma vez: hooks do git
mise run up                  # API em http://localhost:8080, perfil local
```

O `mise run up` sobe a API no perfil `local` com um `mongo:8` em replica set de um nó, do Testcontainers (`./mvnw spring-boot:test-run`, pela `LocalFluvenyApplication` dos testes). Não precisa de `.env` nem de Mongo instalado. O Swagger UI fica em http://localhost:8080/swagger-ui/index.html.

O Mongo é efêmero: cada `up` começa com o banco vazio, e o `LocalDataLoader` roda os carregadores dos módulos quando a API fica pronta. Com a API no ar, em outro terminal:

| Tarefa | O que faz |
| --- | --- |
| `mise run seed` | Roda os carregadores de novo (`POST /local/seed`). Idempotente: não duplica nada. |
| `mise run reset` | Apaga o banco e roda os carregadores (`POST /local/reset`). |
| `mise run check` | Formatação, build e todos os testes. É o que a CI roda. |
| `mise run format` | Formata o código Java (google-java-format, estilo AOSP). |

Com a API fora do ar, o `seed` e o `reset` avisam e saem com erro. A coleção [Bruno](https://www.usebruno.com) em [`api-collection/`](api-collection/) tem o ambiente `local` com o `/api/v1/meta`, o `/local/seed` e o `/local/reset`.

### Perfil `local`

- Só existe na máquina do dev (AD-18). A API não sobe com `local` junto de `prod`, nem com um Mongo que não seja local: `mongodb+srv://` é recusado, e o host precisa ser `localhost`, `127.0.0.1`, `::1` ou `mongo` (o serviço do compose do App). A mensagem cita os perfis ou o host.
- As rotas `/local/*` ficam fora de `/api/v1` e do contrato OpenAPI, e só existem nesse perfil.
- Os dados de desenvolvimento: cada módulo registra um bean `LocalDataSeeder` (named interface `platform :: local`) com `@Profile("local")` e os dados mínimos para testar as próprias telas, gravando por chave para ser idempotente. Ainda não há nenhum: o primeiro chega com a 1.7 (`content`) ou a 2.1 (`account`).
- Senha de usuário de seed vem de `LOCAL_SEED_PASSWORD`, lida pela `LocalSeedPassword`. Copie o [`.env.local.example`](.env.local.example) para `.env.local` (fora do git) e preencha; o perfil `local` lê o arquivo sozinho. Nenhum usuário é criado por seed fora do perfil `local`.
- Os logs saem em texto, não em JSON.

## Módulos

O código fica em `src/main/java/com/fluveny/fluveny_backend/`, um pacote por módulo: `account`, `auth`, `team`, `content`, `learning`, `metrics`, `survey`, `mail` e `platform`. As dependências permitidas entre eles ficam no `package-info.java` de cada um e seguem o grafo da AD-3. O `ModularityTest` falha o build em caso de ciclo ou acesso fora do grafo.

## Conteúdo Estruturado

O conteúdo do curso fica em [`content/`](content/), em JSON, e o formato é o JSON Schema (draft 2020-12) de [`content/schema/`](content/schema/) (AD-4, AD-6). Os `$id` dos schemas começam com `https://fluveny.github.io/content-schema/v1/` `[ASSUMPTION]`: o domínio não foi confirmado, e os `$id` só precisam ser estáveis (os `$ref` se resolvem sem rede).

### Arquivos

```text
content/
  media.json                      # manifesto de mídia: mediaKey → {kind, source, url?}
  schema/                         # JSON Schema canônico e render-vectors.json
  units/<unitKey>/
    unit.json                     # unitKey, order, schemaVersion (1), title, stages, usefulExpressions
    stages/<stageKey>.json        # stageKey, title, activities
```

- O nome da pasta é o `unitKey`, e o de cada arquivo de Estágio é o `stageKey`. Todo Estágio listado em `unit.json.stages` tem arquivo, e todo arquivo está na lista. Em `stages/` só ficam arquivos `<stageKey>.json`, e em `units/` só pastas de Unidade; entradas que começam com `.` são ignoradas.
- O `order` de cada Unidade é único no curso.
- Chaves (`unitKey`, `stageKey`, `activityKey`, `itemKey`, `optionKey`, `expressionKey`, `mediaKey`) em kebab-case (`s1-meet-alex`). Nunca reaproveite uma chave (AD-5). `activityKey` e `itemKey` são únicos na Unidade, `optionKey` no Item e `expressionKey` na Unidade. A posição na lista é só ordem.
- `usefulExpressions` traz as Expressões Úteis: `{expressionKey, term, meaning, examples}`.

### Texto

Todo texto é uma string ou as três variantes de pronome, sem conjugar nada no código:

```json
"question": { "he": "He is from {country}.", "she": "She is from {country}.", "they": "They are from {country}." }
```

- Placeholders, e só estes: `{name}`, `{country}`, `{avatar.<campo>}` e `{likes.<item>}` (regex em `x-fluveny-placeholder`, no `text.schema.json`).
- Formatação aceita: `**negrito**` e `[[texto|expressionKey]]`, com um `expressionKey` de `usefulExpressions`. Três ou mais `_` seguidos (`I ___ from Brazil.`) são uma lacuna e ficam como texto.
- O validador recusa (`INVALID_MARKUP`), e só isto: `**` sem par, negrito vazio (`****`), negrito que cruza o limite de um `[[...]]`, `[[...]]` sem `|`, sem rótulo, sem chave ou com chave inexistente, `[[` ou `]]` soltos, `{` ou `}` sem par, `*` solto (`*x*`), `__` (`__x__`), `~~`, crase, tag HTML (`<b>`) e link markdown (`[x](url)`). Placeholder fora da gramática é `UNKNOWN_PLACEHOLDER`.
- O `ContentRenderer` (named interface `content :: render`) escolhe a variante (sem escolha do aluno, `they`), troca os placeholders e deixa a formatação como está. Placeholder sem valor fica como está. Os casos de referência ficam em `content/schema/render-vectors.json`, rodados aqui e no App.

### Mídia

Opção com imagem, vídeo ou áudio usa só o `mediaKey`. Todo `mediaKey` existe em `content/media.json`: `{"flag-brazil": {"kind": "image", "source": "bundled"}}`. Com `"source": "remote"`, a `url` é obrigatória e precisa ser uma URI (o validador liga a asserção de `format`).

### Segredos

Os campos que o aluno não pode ver (por exemplo, `correctOptionKey` e `justification` da Escolha) são marcados com `"x-fluveny-secret": true` no schema. A marcação é a única fonte: não existe lista de campos secretos no código. A visão do aluno (`*StudentView`) é a Atividade sem esses campos, e o `ActivityFidelityTest` confere isso.

### Validação

```bash
./mvnw -B -ntp test -Dtest=ContentUnitsTest
```

O `ContentUnitsTest` roda o `ContentValidator` sobre `content/` (FR-29) e falha listando todos os erros de uma vez, um por linha: arquivo, ponteiro JSON, regra e chave. As regras: `SCHEMA_VIOLATION`, `INVALID_JSON` (com linha e coluna, ou "Arquivo vazio."), `MISSING_FILE`, `FILE_NAME_MISMATCH`, `STAGE_FILE_MISMATCH`, `UNEXPECTED_FILE` (arquivo fora do lugar em `stages/` ou `units/`), `DUPLICATE_KEY` (também `order` repetido entre Unidades), `MISSING_ACCEPTED_ANSWER`, `MEDIA_NOT_FOUND`, `PRONOUN_VARIANTS_MISSING`, `UNKNOWN_PLACEHOLDER` e `INVALID_MARKUP`. Ele roda no `./mvnw test`, portanto na CI. Um erro de schema só sai do relatório quando repete uma regra própria no mesmo ponteiro (o `required` de `correctOptionKey`; o `oneOf`, o `type` e o `required` de um pronome faltando); os demais ficam.

As restrições "texto ou mídia" da opção, `minItems`/`maxItems`, `pattern` e `minLength` ficam só no JSON Schema: o OpenAPI dos records não as carrega, e quem as garante é o validador.

### Tipo de Atividade novo

1. `content/schema/activities/<tipo>.schema.json`, com `type` como `const`, os campos secretos marcados e o `$defs.answer`. Na união de `stage.schema.json`, o valor no `enum` de `type` e um `if`/`then` para o arquivo.
2. Em `content.activity`: o record da Atividade (`@JsonTypeName`, `type` com `allowableValues`), o da visão do aluno (sem os campos secretos) e o do Answer. Acrescente cada um ao `permits`, ao `oneOf` e ao `discriminatorMapping` das três interfaces.
3. Fixtures em `src/test/resources/content-fixtures/activities/`, com o prefixo exato do Tipo: `<tipo>-activity…`, `<tipo>-student-view…` e `<tipo>-answer…`, com cada campo opcional presente numa e ausente noutra. Nos records, `requireNonNull` nos campos obrigatórios (compact constructor), e número obrigatório como `Integer`.
   - Texto novo debaixo de `oneOf`, `anyOf`, `additionalProperties` ou `prefixItems` faz o `SchemaWalker` falhar o build: as regras de texto não o veriam.
4. A regra de resposta aceita do Tipo em `ACCEPTED_ANSWER_RULES`, no `ContentValidator`. Sem ela o validador não sobe, e o `ContentValidatorTest` falha.
5. `./mvnw -B -ntp test`: o `ActivityFidelityTest` falha se schema, `permits`, `@JsonTypeName` e mapping divergirem, ou se propriedades, `required`, tipos e `enum` dos records divergirem do schema.

## Build e testes

```bash
mise run check   # ./mvnw -B -ntp spotless:check verify
```

- Formatação: google-java-format no estilo AOSP, pelo Spotless. O `spotless:check` falha a CI; `mise run format` corrige, e o hook do lefthook formata os arquivos Java staged a cada commit.
- Unitários (`*Test`, surefire), incluindo o `ModularityTest`, que também gera a documentação dos módulos em `target/spring-modulith-docs`.
- Integração (`*IT`, failsafe), contra o `mongo:8` em replica set do `LocalMongoContainer` (Testcontainers), sem precisar de nenhuma variável de ambiente:
  - o `ApplicationIT` sobe o contexto via `@ServiceConnection`, no perfil `test`;
  - o `MongoTransactionIT` confirma que as transações funcionam (AD-10): commit e rollback;
  - o `LocalDataLoaderIT` confirma que os carregadores rodam na partida, que rodar de novo não duplica e que o reset apaga o banco;
  - o `MongoUriBindingIT` passa a URI do container como `SPRING_DATA_MONGODB_URI` e confirma que a API conecta por ela, no perfil `prod` padrão;
  - o `ProdProfileIT` confirma que, sem perfil e sem `SPRING_DATA_MONGODB_URI`, a API não sobe e a falha cita a variável.

Para rodar só os unitários (incluindo o `ModularityTest`), sem Docker: `./mvnw -B -ntp test`.

## Variáveis de ambiente

Toda configuração vem do ambiente. Nenhum segredo fica em arquivo.

| Variável | Uso |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB, com credenciais e o nome do banco. Obrigatória no perfil `prod`: sem ela a API não sobe, e o log cita a variável. É lida em `spring.mongodb.uri`, porque o Boot 4 ignora a `spring.data.mongodb.uri`. |
| `SPRING_PROFILES_ACTIVE` | Opcional. Sem ela, o perfil é `prod` (AD-18). O `local` é só para a máquina do dev (veja "Ambiente local"). |
| `LOCAL_SEED_PASSWORD` | Só no perfil `local`, no `.env.local`. Senha dos usuários criados pelos carregadores locais. |
| `SENTRY_DSN` | Opcional. Sem ela, o Sentry fica desligado e nada é enviado. Com ela, os erros 500 vão para o Sentry sem PII (usuário, IP, cookies, query string, cabeçalhos de autenticação e corpo da requisição são removidos). |
| `SENTRY_ENVIRONMENT` | Opcional. Nome do ambiente no Sentry (por exemplo, `homolog` ou `prod`). |
| `FLUVENY_MIN_APP_VERSION` | Opcional, padrão `0.0.0`. Versão mínima do App, devolvida em `GET /api/v1/meta`. Precisa estar no formato `X.Y.Z`; vazia ou em outro formato, a API não sobe. |

Os logs saem no console em JSON (ECS), sem corpo de requisição nem cabeçalhos.

## Contrato HTTP

- Sucesso: `{"data": ..., "message"?: "..."}` (`ApiResponse`, em `platform.web`).
- Erro: `application/problem+json` (RFC 9457) com `type`, `title`, `status`, `detail` e `code` em SCREAMING_SNAKE. `title` e `detail` em pt-BR; nunca stack trace nem mensagem de exceção. Erros de validação trazem também `errors: [{field, message}]`, inclusive parâmetro, cabeçalho ou parte obrigatórios que faltam (com o nome em `field`). JSON malformado sai `VALIDATION_FAILED` com `errors: []`, porque não há campo a apontar.
  - Códigos da `platform`: `VALIDATION_FAILED`, `INVALID_ID` (só para `ObjectId` malformado, direto ou em `Optional`; outro tipo de parâmetro inválido é `VALIDATION_FAILED`), `UNAUTHENTICATED`, `ACCESS_DENIED`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNPROCESSABLE`, `TOO_MANY_REQUESTS`, `INTERNAL_ERROR` e outros em `ErrorCode`. O `title` sempre corresponde ao status da resposta (um 401 com `ACCESS_DENIED` sai com o título do 401). Exceção com `@ResponseStatus` e 5xx diferente de 500 mantêm o próprio status; só o 500 vai para o Sentry.
  - Todo 401 leva `WWW-Authenticate: Bearer realm="fluveny"`.
  - Erro de domínio: `throw new ApiException(HttpStatus.CONFLICT, "STAGE_LOCKED", "O estágio está bloqueado.")`. O `code` precisa ser SCREAMING_SNAKE (`[A-Z][A-Z0-9_]*`), e há um construtor com `cause` para o log.
- `GET /api/v1/meta` é público e devolve `minAppVersion` e `activityTypes`. Cada história de Tipo de Atividade registra um bean `ActivityTypeSource`.
- Termo de busca digitado pelo usuário passa por `SearchTerms.literal(term)` e entra só como valor do critério.

### Contrato OpenAPI

O contrato da API é o OpenAPI 3.1 commitado em [`openapi/openapi.json`](openapi/openapi.json) (AD-11). O App copia esse arquivo, num sha da `main`, e gera os tipos dele.

- O `OpenApiContractTest` lê o `/v3/api-docs` e compara com o arquivo. A saída é estável: chaves ordenadas, `servers` fixo (`/`) e `application/json` como media type padrão.
- Mudou um controller ou um DTO? Gere o arquivo de novo e commite junto:

  ```bash
  ./mvnw -B -ntp test -Dtest=OpenApiContractTest -Dopenapi.write=true
  ```

  Ninguém edita o arquivo nem resolve conflito nele à mão: rebaseie e gere de novo. Rodar o comando sem mudar o código não produz diff.
- Dentro de `/api/v1`, só mudanças aditivas. O job `Contrato` da CI roda o `oasdiff breaking` entre o arquivo da `main` (em push, o do commit anterior) e o do PR, e falha em quebra.
  - **Permitido:** rota nova, campo novo na resposta, campo opcional novo na requisição, valor novo de `enum` na requisição e remover um campo **opcional** da resposta (o oasdiff 1.38 classifica `response-optional-property-removed` como info). Por isso o App trata todo campo opcional da resposta como opcional de verdade.
  - **Quebra (error no oasdiff, falha a CI):** remover rota, remover ou renomear campo **obrigatório** da resposta, mudar o tipo de um campo, tornar obrigatório um campo da requisição e valor novo de `enum` na resposta (`response-property-enum-value-added`; o App instalado não conhece o valor).

## Autorização

Toda rota é negada, a não ser que o handler declare quem pode chamá-la (deny-by-default, AD-12):

```java
@PublicRoute                       // pública
@RequiresRole(Role.CONTENT_CREATOR) // CONTENT_CREATOR ou ADMIN (hierarquia ADMIN > CONTENT_CREATOR)
@RequiresRole(Role.STUDENT)        // só STUDENT; STUDENT fica fora da hierarquia
```

A anotação vale no método ou na classe do controller, e a do método vence. Handler sem declaração, com `@PublicRoute` e `@RequiresRole` juntos ou com `@RequiresRole({})` responde `ACCESS_DENIED` (401 anônimo, 403 autenticado), e o `RouteDeclarationScanTest` falha o build nomeando o handler. Handler que não é método de controller (um `RouterFunction`, por exemplo) também é negado, porque não tem onde declarar papel. As únicas exceções por caminho são as da documentação: `/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/**`, `/swagger-ui.html` e `/swagger-ui/**`. Ainda não há login: sessões, Bearer e cookie chegam nas histórias 2.3 e 4.2, e até lá toda rota não pública responde 401.

Os outros módulos usam a `platform` pelas named interfaces `web`, `security` e `meta`, declaradas como `"platform :: *"` no `package-info.java`.

## Imagem

```bash
docker build -t fluveny-api:local .
docker run --rm -p 8080:8080 -e SPRING_DATA_MONGODB_URI=... fluveny-api:local
```

A API escuta na porta 8080 e roda com um usuário sem root. Em todo PR a CI constrói a imagem sem publicar. A cada merge na `main`, publica `ghcr.io/fluveny/fluveny-api:main` e `ghcr.io/fluveny/fluveny-api:sha-<sha>`.

A documentação da API fica no Swagger UI, em http://localhost:8080/swagger-ui/index.html, e o OpenAPI em `/v3/api-docs`. As duas são públicas em todos os ambientes.

## Contribuindo

Fluxo de branches, commits e PR no [`CONTRIBUTING.md`](CONTRIBUTING.md).
