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

A CORS do perfil `local` aceita o App web do `npx expo start --web` (`http://localhost:8081`), com `EXPO_PUBLIC_API_URL=http://localhost:8080` no App. Para o teste no aparelho, ponha `FLUVENY_WEB_ORIGINS` no ambiente ou no `.env.local` com a origem que o navegador do aparelho usa (por exemplo `http://192.168.0.10:8081`), e aponte o `EXPO_PUBLIC_API_URL` do App para o IP da máquina também (`http://192.168.0.10:8080`). A variável troca o padrão, então liste de novo o `http://localhost:8081` se quiser os dois.

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
- Os dados de desenvolvimento: cada módulo registra um bean `LocalDataSeeder` (named interface `platform :: local`) com `@Profile("local")` e os dados mínimos para testar as próprias telas, gravando por chave para ser idempotente. O de `content` (`ContentLocalSeeder`) publica cada pasta de `content/units/` com `publish=true`, pelo mesmo serviço da rota admin: depois do `mise run up`, `GET /api/v1/units` já lista as Unidades. Ele pula só a compatibilidade de Tipo com o `minAppVersion` (e avisa no log); as outras validações valem, e uma Unidade inválida fica de fora com os erros no log. A pasta vem de `FLUVENY_CONTENT_DIR` (padrão `content`, relativo ao diretório de trabalho).
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
  schema/                         # JSON Schema canônico, render-vectors.json e countries.json
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
- A lista canônica de países fica em `content/schema/countries.json` (`[{code, name}]`, ISO 3166-1 alfa-2, `name` em inglês, em ordem alfabética de `name`); o App a recebe pelo `api:sync`, e o `CountriesTest` confere a forma. Na pergunta do país do Diálogo (`ask: country`), o App guarda o `name` em inglês do país escolhido, e é ele que o placeholder `{country}` renderiza; `{name}` renderiza o que o aluno digitou. A seleção é `[ASSUMPTION]` até Letras validar (Decisão 1 da 1.13).

### Mídia

Opção com imagem, vídeo ou áudio usa só o `mediaKey`. Todo `mediaKey` existe em `content/media.json`: `{"flag-brazil": {"kind": "image", "source": "bundled"}}`. Com `"source": "remote"`, a `url` é obrigatória e precisa ser uma URI (o validador liga a asserção de `format`).

### Segredos

Os campos que o aluno não pode ver (por exemplo, `correctOptionKey` e `justification` da Escolha) são marcados com `"x-fluveny-secret": true` no schema. A marcação é a única fonte: não existe lista de campos secretos no código. A visão do aluno (`*StudentView`) é a Atividade sem esses campos, e o `ActivityFidelityTest` confere isso.

### Validação

```bash
./mvnw -B -ntp test -Dtest=ContentUnitsTest
```

O `ContentUnitsTest` roda o `ContentValidator` sobre `content/` (FR-29) e falha listando todos os erros de uma vez, um por linha: arquivo, ponteiro JSON, regra e chave. As regras: `SCHEMA_VIOLATION`, `INVALID_JSON` (com linha e coluna, ou "Arquivo vazio."), `MISSING_FILE`, `FILE_NAME_MISMATCH`, `STAGE_FILE_MISMATCH`, `UNEXPECTED_FILE` (arquivo fora do lugar em `stages/` ou `units/`), `DUPLICATE_KEY` (também `order` repetido entre Unidades), `MISSING_ACCEPTED_ANSWER`, `GAP_COUNT_MISMATCH` (número de `___` nas linhas das Lacunas, ou do Extra Help da Resposta Livre, diferente do de itens, em alguma variante de pronome), `BANK_MODE_MISMATCH` (`bank` nas Lacunas `typed`, ou faltando nas `bank`), `MEDIA_NOT_FOUND`, `PRONOUN_VARIANTS_MISSING`, `UNKNOWN_PLACEHOLDER` e `INVALID_MARKUP`. Ele roda no `./mvnw test`, portanto na CI. Um erro de schema só sai do relatório quando repete uma regra própria no mesmo ponteiro (o `required` de `correctOptionKey` e o de `modelAnswer` da Resposta Livre; o `oneOf`, o `type` e o `required` de um pronome faltando); os demais ficam.

As restrições "texto ou mídia" da opção, `minItems`/`maxItems`, `pattern` e `minLength` ficam só no JSON Schema: o OpenAPI dos records não as carrega, e quem as garante é o validador.

### Publicação

O conteúdo chega à API como uma Versão de Unidade (AD-4, história 1.7):

- **Pacote:** `POST /api/v1/admin/content/units/{unitKey}/versions?publish=true|false` recebe `{unit, stages[], media{}}`: o `unit.json`, os Estágios na ordem de `unit.stages` e só as entradas de `content/media.json` que a Unidade referencia. O [`scripts/content-bundle.mjs`](scripts/content-bundle.mjs) (sem dependências) monta o pacote da pasta, e o carregador local monta o mesmo (`UnitBundle`).
- **Validação:** as mesmas regras do `ContentUnitsTest` (schema e FR-29), mais as do pacote (`INVALID_BUNDLE`, `UNREFERENCED_MEDIA`). Cada Tipo usado precisa de um `ActivityTypeSource` com `sinceAppVersion` não nulo e ≤ `FLUVENY_MIN_APP_VERSION`. Qualquer erro é `422` (`INVALID_CONTENT`, `UNSUPPORTED_ACTIVITY_TYPE` ou `UNIT_KEY_MISMATCH`), com `errors: [{file, pointer, key, rule, message}]`, e nada é gravado.
- **Hash:** só a API calcula. O id é `sha256:<hex>` do pacote em JSON canônico (RFC 8785, `CanonicalJson`), e a `UnitVersion` guarda o JSON canônico como string, então o hash sempre pode ser recalculado. Publicar o mesmo conteúdo de novo responde `200` com `created: false`; conteúdo novo, `201`.
- **Coleções:** `unit_versions` (`_id` = hash, nunca apagada nem reescrita) e `unit_pointers` (`_id` = `unitKey`, `publishedVersionId`). Os índices são criados na partida (`ContentIndexes`); um `@SpringBootTest` sem Mongo desliga com `fluveny.mongo.indexes-on-startup=false`.
- **Ponteiro:** com `publish=false` a versão é gravada como candidata e o ponteiro não se move.
- **Registro:** `GET /api/v1/admin/content/units/{unitKey}/versions/{versionId}` responde `200` com os metadados ou `404`. A homologação é o registro de hashes: produção só publica um hash que já existe lá.

As duas rotas admin exigem o token de deploy do ambiente (`Role.DEPLOY`, veja "Autorização").

#### Job "Publicar conteúdo"

O [`publish-content.yml`](.github/workflows/publish-content.yml) é manual (`workflow_dispatch`), da `main`, com os inputs `environment` (`homolog` ou `prod`) e `unitKey`. Cada GitHub Environment tem o secret `FLUVENY_DEPLOY_TOKEN` e a variável `FLUVENY_API_URL`.

- `homolog`: envia o pacote com `publish=true`.
- `prod`: grava a versão em produção como candidata (`publish=false`), confere o hash na homologação com o token dela e só então move o ponteiro de produção. Se o hash não estiver na homologação, o job para; a candidata fica em produção, imutável e sem ser servida.

O job `Publicação (API efêmera)` da CI roda os mesmos comandos contra a API e um `mongo:8` no próprio job, e passa o `actionlint` nos workflows.

### Rotas do aluno

Públicas (`@PublicRoute`) e só com `*StudentView`, sobre a versão `published`:

- `GET /api/v1/units`: `[{unitKey, order, title, comingSoon, versionId}]`, por `order`. `comingSoon` é a Unidade publicada sem Estágios. Unidade sem versão publicada fica de fora.
- `GET /api/v1/units/{unitKey}`: o resumo, com os Estágios (`stageKey`, `title`, `activityCount`) e as `usefulExpressions`.
- `GET /api/v1/units/{unitKey}/stages/{stageKey}`: `{versionId, stageKey, title, activities}`, com as Atividades na visão do aluno.

Só o primeiro Estágio da Unidade de menor `order` é liberado; os demais respondem `401 UNAUTHENTICATED` até existir sessão (2.3). Chave fora do kebab-case é `400 INVALID_ID`; Unidade ou Estágio inexistente, `404` (`UNIT_NOT_FOUND`, `STAGE_NOT_FOUND`).

A visão do aluno sai do `StudentViewProjector`, o único projetor: ele percorre a instância com o JSON Schema e remove toda propriedade `x-fluveny-secret`. O construtor recusa um segredo fora de `properties` ou debaixo de `oneOf`, `anyOf`, `additionalProperties` e afins, e a leitura em `ActivityStudentView` recusa propriedade desconhecida. O `ContentPublicationIT` varre todas as respostas das rotas do aluno com uma fixture cheia de segredos, e o `StudentRoutesOpenApiTest` falha se um componente alcançável por rota do aluno tiver uma propriedade secreta.

### Tipo de Atividade novo

1. `content/schema/activities/<tipo>.schema.json`, com `type` como `const`, os campos secretos marcados e o `$defs.answer`. Na união de `stage.schema.json`, o valor no `enum` de `type` e um `if`/`then` para o arquivo.
2. Em `content.activity`: o record da Atividade (`@JsonTypeName`, `type` com `allowableValues`), o da visão do aluno (sem os campos secretos) e o do Answer. Acrescente cada um ao `permits`, ao `oneOf` e ao `discriminatorMapping` das três interfaces.
3. Fixtures em `src/test/resources/content-fixtures/activities/`, com o prefixo exato do Tipo: `<tipo>-activity…`, `<tipo>-student-view…` e `<tipo>-answer…`, com cada campo opcional presente numa e ausente noutra. Nos records, `requireNonNull` nos campos obrigatórios (compact constructor), e número obrigatório como `Integer`.
   - Texto novo debaixo de `oneOf`, `anyOf`, `additionalProperties` ou `prefixItems` faz o `SchemaWalker` falhar o build: as regras de texto não o veriam.
4. A regra de resposta aceita do Tipo em `ACCEPTED_ANSWER_RULES`, no `ContentValidator`. Sem ela o validador não sobe, e o `ContentValidatorTest` falha.
   - Tipo expositivo (como o Diálogo, 1.13): sem `$defs.answer`, sem record de Answer e sem regra de resposta aceita. Ele entra em `EXPOSITORY_TYPES` do `ContentValidator`, e o `ActivityFidelityTest` dispensa o Answer. No `Grader`, o ramo lança `400 INVALID_ANSWER`.
   - Regra que o JSON Schema não expressa (ex. `ask` único no Diálogo) vai em `TYPE_RULES`, no `ContentValidator`. Ex.: o Grammar Time (`grammar`, 1.19), expositivo em três abas (`used`, `rules`, `examples`), exige em cada frase de `used`, em cada variante de pronome, ao menos um trecho `**...**` com a forma destacada (`MISSING_GRAMMAR_HIGHLIGHT`). O destaque vem do conteúdo; o código não o calcula. Os records dele não reaproveitam o `GrammarRule` legado (AD-17).
5. No `learning`, o Tipo no `LearningActivityTypes`, com `sinceAppVersion` nulo até a tag do App, e as conclusões que ele aceita em `completions` (`COMPLETIONS`; fora delas, `409 COMPLETION_NOT_ALLOWED`). A rota de conclusão fecha a Atividade sem correção: o expositivo com `VIEWED` e o Tipo avaliado que aceita pular com `SKIPPED` (a Fala, 1.15). A Resposta Livre (1.18) conclui com `VIEWED`, que devolve a `modelAnswer` no `CompletionResult` (o texto do aluno nunca vem), e a Tentativa dela é o Extra Help: as lacunas do banco, corrigidas pela regra das Lacunas, com `extraHelp: true` no Resultado. A `FirstAttempt` grava `extraHelp: true` só quando a Tentativa do Extra Help é o primeiro registro do aparelho na Atividade; com a conclusão `VIEWED` antes, o registro fica `VIEWED` com `extraHelp: false`. O "Ver resposta" do Extra Help exige que o registro seja uma Tentativa (`GRADED`): a conclusão `VIEWED` não basta (`409 ATTEMPT_REQUIRED`).
6. `./mvnw -B -ntp test`: o `ActivityFidelityTest` falha se schema, `permits`, `@JsonTypeName` e mapping divergirem, ou se propriedades, `required`, tipos e `enum` dos records divergirem do schema.

## Build e testes

```bash
mise run check   # ./mvnw -B -ntp spotless:check verify
```

- Formatação: google-java-format no estilo AOSP, pelo Spotless. O `spotless:check` falha a CI; `mise run format` corrige, e o hook do lefthook formata os arquivos Java staged a cada commit.
- Unitários (`*Test`, surefire), incluindo o `ModularityTest`, que também gera a documentação dos módulos em `target/spring-modulith-docs`.
- Integração (`*IT`, failsafe), contra o `mongo:8` em replica set do `LocalMongoContainer` (Testcontainers), sem precisar de nenhuma variável de ambiente:
  - o `ApplicationIT` sobe o contexto via `@ServiceConnection`, no perfil `test`, e confere os índices de `visitor_attempts` e de `events` criados na partida; o `VisitorAttemptsMongoIT` cobre o índice único, o TTL e a corrida; o `MetricsMongoIT` cobre os campos de cada evento, o índice único parcial do `STAGE_COMPLETED` e que 16 `stageCompleted` simultâneos do mesmo Estágio gravam um só documento;
  - o `MongoTransactionIT` confirma que as transações funcionam (AD-10): commit e rollback;
  - o `LocalDataLoaderIT` confirma que os carregadores rodam na partida, que rodar de novo não duplica e que o reset apaga o banco;
  - o `ContentPublicationIT` cobre a publicação (nova, repetida, candidata, inválida, Tipo não suportado, `unitKey` divergente, token) e as rotas do aluno, com a varredura de segredos; o `ContentLocalSeederIT` confirma que o perfil `local` publica `content/units/` sozinho, inclusive depois do reset;
  - o `MongoUriBindingIT` passa a URI do container como `SPRING_DATA_MONGODB_URI` e confirma que a API conecta por ela, no perfil `prod` padrão;
  - o `ProdProfileIT` confirma que, sem perfil e sem `SPRING_DATA_MONGODB_URI` ou `FLUVENY_RECEIPT_KEY`, a API não sobe e a falha cita a variável, e que com a `FLUVENY_RECEIPT_KEY` inválida ela também não sobe. Ele também confirma que a API não sobe sem `FLUVENY_WEB_ORIGINS` ou com uma origem fora do formato, nem sem `FLUVENY_PROXY_SECRET` ou com um segredo fora do formato. Os testes no perfil `prod` recebem uma `FLUVENY_RECEIPT_KEY` gerada na execução pela extensão `TestReceiptKey`, uma `FLUVENY_WEB_ORIGINS` de teste pela `TestWebOrigins` e um `FLUVENY_PROXY_SECRET` gerado pela `TestProxySecret`. Com o segredo, o filtro de borda liga em todo teste, então a `TestProxySecret` também põe o `X-Fluveny-Proxy` em toda requisição do MockMvc (por `META-INF/spring.factories`); o `ProxySecretTest` monta um MockMvc sem ele para provar a recusa, e cliente HTTP de verdade passa `TestProxySecret.HEADER` com `TestProxySecret.value()`.

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
| `FLUVENY_MIN_APP_VERSION` | Opcional, padrão `0.0.0`. Versão mínima do App, devolvida em `GET /api/v1/meta`. Precisa estar no formato `X.Y.Z`; vazia ou em outro formato, a API não sobe. A publicação recusa Tipo com `sinceAppVersion` acima dela. |
| `FLUVENY_DEPLOY_TOKEN_SHA256` | Opcional. O sha256, em hex, do token de deploy do ambiente (o token fica só no GitHub Environment, como `FLUVENY_DEPLOY_TOKEN`). Sem ela, toda publicação é negada e a API sobe normalmente; fora do formato (64 dígitos hex), a API não sobe. Gere com `printf '%s' "$TOKEN" \| shasum -a 256`. |
| `FLUVENY_RECEIPT_KEY` | Obrigatória no perfil `prod`. Chave HMAC dos recibos do Visitante (história 1.12), em base64 com 32 bytes ou mais; gere com `openssl rand -base64 32`. Fora do formato, a API não sobe. Fora de `prod`, sem ela, a API gera uma chave na partida e avisa no log (os recibos antigos deixam de valer). |
| `FLUVENY_WEB_ORIGINS` | Obrigatória no perfil `prod`. Origens web aceitas pela CORS de `/api/v1/**` (história 1.22), separadas por vírgula: o App do Aluno na web, por exemplo `https://app.fluveny.com`. Cada origem é `scheme://host[:porta]` (`http` ou `https`), sem caminho, sem barra no fim e sem `*`; fora do formato, a API não sobe. A CORS liga `allowCredentials` (a sessão web será por cookie), aceita `GET`, `POST`, `PUT`, `PATCH` e `DELETE` e os cabeçalhos `Content-Type`, `Accept`, `X-App-Version` e `X-Fluveny-Client`, e expõe o `Retry-After`. No perfil `local`, o padrão é `http://localhost:8081`. Fora de `prod` e do `local`, sem ela, nenhuma origem web passa. Requisição sem `Origin` (o App Android, o curl) não muda. |
| `FLUVENY_PROXY_SECRET` | Obrigatória no perfil `prod`. Segredo de proxy do ambiente (AD-18, história 1.6): o Worker da Cloudflare o envia no cabeçalho `X-Fluveny-Proxy`, e sem ele a API responde `403 ACCESS_DENIED` (problem+json) a tudo, exceto `GET /api/v1/meta`, o health check do host. A comparação é em tempo constante, e o segredo nunca vai para o log. Só com o segredo certo a API lê o IP do cliente do `X-Fluveny-Client-IP`, que o Worker preenche com o `CF-Connecting-IP` que recebe do aluno (base do limite de taxa do Visitante). O `CF-Connecting-IP` que chega à API não é lido: o Render também fica atrás do Cloudflare, que o troca pelo IP fixo do Worker na subrequisição. O `X-Forwarded-For` nunca é lido, e a `server.forward-headers-strategy` fica desligada de propósito. De 32 a 256 caracteres ASCII visíveis, sem espaço; gere com `openssl rand -hex 32`. Fora do formato, a API não sobe. Fora de `prod`, sem ela, o filtro fica desligado. O mesmo valor fica no Worker, no host da API e no secret `FLUVENY_PROXY_SECRET` do GitHub Environment. |
| `FLUVENY_CONTENT_DIR` | Só no perfil `local`. Pasta `content` lida pelo carregador local; padrão `content`. |

Os logs saem no console em JSON (ECS), sem corpo de requisição nem cabeçalhos.

## Contrato HTTP

- Sucesso: `{"data": ..., "message"?: "..."}` (`ApiResponse`, em `platform.web`).
- Erro: `application/problem+json` (RFC 9457) com `type`, `title`, `status`, `detail` e `code` em SCREAMING_SNAKE. `title` e `detail` em pt-BR; nunca stack trace nem mensagem de exceção. Erros de validação trazem também `errors: [{field, message}]`, inclusive parâmetro, cabeçalho ou parte obrigatórios que faltam (com o nome em `field`). JSON malformado sai `VALIDATION_FAILED` com `errors: []`, porque não há campo a apontar.
  - Códigos da `platform`: `VALIDATION_FAILED`, `INVALID_ID` (só para `ObjectId` malformado, direto ou em `Optional`; outro tipo de parâmetro inválido é `VALIDATION_FAILED`), `UNAUTHENTICATED`, `ACCESS_DENIED`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNPROCESSABLE`, `TOO_MANY_REQUESTS`, `INTERNAL_ERROR` e outros em `ErrorCode`. O `title` sempre corresponde ao status da resposta (um 401 com `ACCESS_DENIED` sai com o título do 401). Exceção com `@ResponseStatus` e 5xx diferente de 500 mantêm o próprio status; só o 500 vai para o Sentry.
  - Todo 401 leva `WWW-Authenticate: Bearer realm="fluveny"`.
  - Erro de domínio: `throw new ApiException(HttpStatus.CONFLICT, "STAGE_LOCKED", "O estágio está bloqueado.")`. O `code` precisa ser SCREAMING_SNAKE (`[A-Z][A-Z0-9_]*`), e há um construtor com `cause` para o log.
- `GET /api/v1/meta` é público e devolve `minAppVersion` e `activityTypes`. Cada história de Tipo de Atividade registra um bean `ActivityTypeSource`.
- Termo de busca digitado pelo usuário passa por `SearchTerms.literal(term)` e entra só como valor do critério.

### Eventos do Visitante (história 1.20)

A coleção `events` (dona `metrics`, AD-14) é append-only, e só o `MetricsPublisher` (pacote base de `metrics`) escreve nela, com um método por evento. Cada documento leva `type`, `occurredAt` (hora do servidor) e o `deviceId` do Visitante, mais os campos do tipo, e nada além: nenhum nome, país, texto livre, IP ou cabeçalho.

| Evento | Campos | Quem envia |
| --- | --- | --- |
| `VISITOR_SESSION_STARTED` | só o identificador | o App, a cada abertura do player pelo EXPERIMENTAR AGORA |
| `ACTIVITY_STARTED` | `unitKey`, `versionId`, `activityKey` | o App, a cada abertura de Atividade, inclusive ao reabrir |
| `STAGE_COMPLETED` | `unitKey`, `versionId`, `stageKey` | `learning`, uma vez por `(deviceId, unitKey, stageKey)` |

- **Ingestão:** `POST /api/v1/visitor/events` (dona `learning`, pública), com `{type, deviceId, versionId?, activityKey?}`. O `VISITOR_SESSION_STARTED` leva só `type` e `deviceId`; o `ACTIVITY_STARTED` exige `versionId` e `activityKey`, conferidos contra o conteúdo como na Tentativa (`404` se não existem, `401 UNAUTHENTICATED` fora do Estágio liberado), e o `unitKey` vem do conteúdo. Campo desconhecido, `type` fora da lista ou `deviceId` inválido dão `400 VALIDATION_FAILED`. Responde `202` sem corpo e conta nos limites do Visitante, por IP e por `deviceId` (o mesmo contador das Tentativas e conclusões).
- **`STAGE_COMPLETED`:** quando uma Tentativa ou conclusão grava a primeira do aparelho numa Atividade, `learning` confere se as primeiras do `deviceId` (de qualquer versão, porque a `activityKey` é estável) cobrem todas as `activityKey`s do Estágio na versão da gravação. Se cobrem, publica. O `stageCompletionKey` (`deviceId|unitKey|stageKey`), só desse tipo, tem índice único parcial (`MetricsIndexes`), e a duplicata, inclusive concorrente, é ignorada. Uma falha ao publicar vai para o log e o Sentry e não muda a resposta.
- O índice é criado na partida e pelo carregador local de `metrics`, como os de `learning`.

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
  - **Tipo de Atividade novo** no `oneOf` da resposta (`response-property-one-of-added`) também é permitido: o App que não conhece o Tipo mostra o cartão "Atividade em preparação", e o `sinceAppVersion` de `/api/v1/meta` controla a publicação (AD-11). O `openapi/oasdiff-levels.txt` rebaixa só essa regra para aviso.
  - **Quebra (error no oasdiff, falha a CI):** remover rota, remover ou renomear campo **obrigatório** da resposta, mudar o tipo de um campo, tornar obrigatório um campo da requisição e valor novo de `enum` na resposta (`response-property-enum-value-added`; o App instalado não conhece o valor).

## Autorização

Toda rota é negada, a não ser que o handler declare quem pode chamá-la (deny-by-default, AD-12):

```java
@PublicRoute                       // pública
@RequiresRole(Role.CONTENT_CREATOR) // CONTENT_CREATOR ou ADMIN (hierarquia ADMIN > CONTENT_CREATOR)
@RequiresRole(Role.STUDENT)        // só STUDENT; STUDENT fica fora da hierarquia
@RequiresRole(Role.DEPLOY)         // só o job de publicação, pelo token de deploy
```

`Role.DEPLOY` é o papel do principal de máquina do job "Publicar conteúdo", fora da hierarquia (nenhum usuário o alcança). O `DeployTokenFilter` lê `Authorization: Bearer <token>`, calcula o sha256 e compara em tempo constante com `FLUVENY_DEPLOY_TOKEN_SHA256`. Sem o cabeçalho, uma rota com papel responde `401`; com um Bearer que ninguém reconhece (token errado, ou a variável ausente), `403 ACCESS_DENIED`. Rotas públicas ignoram o Bearer.

A anotação vale no método ou na classe do controller, e a do método vence. Handler sem declaração, com `@PublicRoute` e `@RequiresRole` juntos ou com `@RequiresRole({})` responde `ACCESS_DENIED` (401 anônimo, 403 autenticado), e o `RouteDeclarationScanTest` falha o build nomeando o handler. Handler que não é método de controller (um `RouterFunction`, por exemplo) também é negado, porque não tem onde declarar papel. As únicas exceções por caminho são as da documentação: `/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/**`, `/swagger-ui.html` e `/swagger-ui/**`. Ainda não há login de usuário: sessões, Bearer e cookie chegam nas histórias 2.3 e 4.2, e até lá toda rota não pública responde 401 (exceto as de `Role.DEPLOY`, com o token de deploy).

Os outros módulos usam a `platform` pelas named interfaces `web`, `security` e `meta`, declaradas como `"platform :: *"` no `package-info.java`.

## Imagem

```bash
docker build -t fluveny-api:local .
docker run --rm -p 8080:8080 -e SPRING_DATA_MONGODB_URI=... -e FLUVENY_RECEIPT_KEY=... -e FLUVENY_WEB_ORIGINS=... -e FLUVENY_PROXY_SECRET=... fluveny-api:local
```

A API escuta na porta 8080 e roda com um usuário sem root. Em todo PR a CI constrói a imagem sem publicar, e o smoke confere que ela sobe no perfil `prod`, que `/api/v1/meta` responde 200 e que `/api/v1/units` responde 403 sem o segredo de proxy e 200 com ele. A cada merge na `main`, publica `ghcr.io/fluveny/fluveny-api:main` e `ghcr.io/fluveny/fluveny-api:sha-<sha>`, e o job "Deploy em homologação" pede ao Render a `sha-<sha>` do commit (veja "Homologação").

A JVM roda no padrão da imagem, sem ajuste: medida em 17/11 com `--memory=512m` (o limite do Render free), ela ficou em 255 MB em repouso e com pico de 414 MB sob carga, sem falta de memória.

A documentação da API fica no Swagger UI, em http://localhost:8080/swagger-ui/index.html, e o OpenAPI em `/v3/api-docs`. As duas são públicas em todos os ambientes pela borda (o Worker, com o segredo de proxy); direto no host da API, no perfil `prod`, respondem 403.

## Homologação

A API de homologação roda no [Render](https://render.com) free, a partir da imagem do GHCR, ligada a um cluster Atlas M0 só dela, atrás de um Worker da Cloudflare que serve o App web e faz proxy de `/api/*` (AD-18). Tudo a custo zero (NFR22). O Worker e o deploy do App ficam no `fluveny-app`.

- **Deploy:** a cada merge na `main`, depois do push da imagem, o job "Deploy em homologação" do `ci.yml` chama o deploy hook do Render (secret `RENDER_DEPLOY_HOOK_URL` do Environment `homolog`) com `imgURL=ghcr.io/fluveny/fluveny-api:sha-<sha>`. Sem o secret, o job avisa e não falha.
- **Borda:** sem o `X-Fluveny-Proxy` certo, a URL direta do Render responde 403, exceto `GET /api/v1/meta` (veja `FLUVENY_PROXY_SECRET` em "Variáveis de ambiente").
- **Partida a frio:** o Render free dorme depois de ~15 min parado, e a partida medida foi de 140 s. O workflow "Manter homologação acordada" (`keep-warm.yml`) chama `GET /api/v1/meta` a cada 10 min das 8h às 23h de Brasília, sem segredo; fora desse horário a API dorme. Ele usa a variável de repositório `FLUVENY_HOMOLOG_API_URL`, sem Environment (para não criar um deployment a cada ping), e pode ser rodado à mão (Actions → Run workflow). O GitHub só agenda na branch padrão e desliga agendamentos de repositório sem atividade por 60 dias.
- **Bruno:** o ambiente `homolog` de [`api-collection/`](api-collection/) aponta para o Render; a variável secreta `proxySecret` fica só no seu Bruno, e o `collection.bru` a manda em toda requisição.

### Montar o ambiente (uma vez)

Contas e termos são aceitos por quem administra o projeto, e cada conta tem pelo menos 2 administradores (NFR18). Nenhum segredo vai para arquivo: gere no terminal e cole direto no painel.

1. **Atlas:** no projeto `homolog` da organização do Fluveny, crie o cluster M0 `fluveny-homolog` na AWS N. Virginia (us-east-1), a mesma região do Render, e um usuário do banco só para a API (`fluveny-api-homolog`), só com `readWrite` no banco `fluveny`. Em Network Access, libere as faixas de saída do Render (no serviço, Connect → Outbound; em 17/11, `74.220.49.0/24` e `74.220.57.0/24`, compartilhadas com outros clientes do Render: o usuário e a senha protegem o banco). Copie a URI `mongodb+srv://...` com o nome do banco (`/fluveny`).
2. **Imagem pública:** em GitHub → Packages → `fluveny-api` → Package settings, deixe o pacote público, para o Render baixar sem credencial.
3. **Render:** New → Web Service → Existing image, `ghcr.io/fluveny/fluveny-api:main`, plano Free, região Virginia (US East), a mesma do Atlas (o Render não tem região no Brasil). Nome `fluveny-api-homolog`, URL `https://fluveny-api-homolog.onrender.com`. Health check path: `/api/v1/meta`. Auto-deploy desligado (quem faz o deploy é a CI). Variáveis de ambiente:
   - `PORT=8080` (a porta que o Render encaminha);
   - `SPRING_DATA_MONGODB_URI` com a URI do Atlas;
   - `FLUVENY_RECEIPT_KEY` (`openssl rand -base64 32`);
   - `FLUVENY_PROXY_SECRET` (`openssl rand -hex 32`; o mesmo valor vai para o Worker e para o GitHub);
   - `FLUVENY_WEB_ORIGINS` com a origem do Worker de homologação (por exemplo `https://fluveny-homolog.<conta>.workers.dev`);
   - `FLUVENY_DEPLOY_TOKEN_SHA256` (sha256 do token de deploy de homologação, veja o `CONTRIBUTING.md`);
   - `SENTRY_ENVIRONMENT=homolog` e, se houver, `SENTRY_DSN`.

   Depois, em Settings → Deploy Hook, copie a URL do hook. Ela é um segredo.
4. **GitHub:** em Settings → Environments, crie (ou abra) o `homolog`, com os secrets `FLUVENY_DEPLOY_TOKEN` e `FLUVENY_PROXY_SECRET` (o `RENDER_DEPLOY_HOOK_URL` pode ficar ali ou como secret do repositório; o job `deploy-homolog` enxerga os dois) e a variável `FLUVENY_API_URL` com a URL do Render (sem barra no fim). Em Settings → Secrets and variables → Actions → Variables, crie também a variável de repositório `FLUVENY_HOMOLOG_API_URL`, com a mesma URL, para o `keep-warm.yml`.
5. **Conferir:** rode a CI na `main` (ou faça um merge) e veja, no painel do Render, a imagem `sha-<sha>` do commit no ar. Depois:

   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://fluveny-api-homolog.onrender.com/api/v1/meta   # 200
   curl -s -o /dev/null -w '%{http_code}\n' https://fluveny-api-homolog.onrender.com/api/v1/units  # 403
   read -rs FLUVENY_PROXY_SECRET && export FLUVENY_PROXY_SECRET
   curl -s -o /dev/null -w '%{http_code}\n' -H "X-Fluveny-Proxy: $FLUVENY_PROXY_SECRET" \
     https://fluveny-api-homolog.onrender.com/api/v1/units                                          # 200
   ```

   Prove a publicação sem publicar nada (Decisão 2 da história 1.6): com `FLUVENY_DEPLOY_TOKEN` e `FLUVENY_PROXY_SECRET` exportados só na sessão, `node scripts/content-bundle.mjs publish unit-1 --api https://fluveny-api-homolog.onrender.com --content src/test/resources/content-fixtures/valid` precisa sair com `422` e `UNSUPPORTED_ACTIVITY_TYPE`: token, segredo de proxy e URL estão certos, e a API recusa a fixture. A primeira publicação real vem com o conteúdo da Unit 1, pelo workflow "Publicar conteúdo".

   Rode o "Manter homologação acordada" à mão e veja o ping passar. Acompanhe a memória no painel do Render (Metrics) enquanto percorre o App: ela não pode reiniciar por falta de memória.

Se a API não couber nos 512 MB do Render, o plano B da spine é uma VM Oracle Always Free (Arm, com imagem multi-arch).

## Contribuindo

Fluxo de branches, commits e PR no [`CONTRIBUTING.md`](CONTRIBUTING.md).
