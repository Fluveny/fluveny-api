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
- O carregador de `account` (história 2.1) grava, por e-mail, dois Alunos adultos de teste, com a senha de `LOCAL_SEED_PASSWORD`: `aluno@fluveny.local` (nome Ana, pronomes `SHE`) e `aluno.sem.pronomes@fluveny.local` (sem nome nem pronomes, vale they/them). A senha precisa seguir a regra de senha (8 a 64 caracteres, com letra e número). Sem a variável, ou com ela fora da regra, os Alunos não são criados, um aviso vai para o log e a API sobe do mesmo jeito. Os carregadores de `account` e `auth` recriam também os índices de `users` e `sessions`. Desde a história 2.2, `aluno@fluveny.local` fica com o e-mail confirmado e `aluno.sem.pronomes@fluveny.local`, por confirmar; o carregador de `mail` recria os índices de `mail_outbox`. Desde a 2.3, há também `aluno.desativado@fluveny.local` (`DEACTIVATED`), e o carregador de `auth` recria os índices de `login_throttle`. Desde a 2.8, há `aluno.atrasado@fluveny.local` (`ACTIVE`, sem confirmar o e-mail, com o `createdAt` de 8 dias atrás na criação), para a faixa `emailConfirmationOverdue`.
- **E-mails no local:** sem SMTP, nenhum e-mail sai e todos ficam `PENDING` em `mail_outbox`. `GET http://localhost:8080/local/mail` lista os 20 últimos, com o texto: copie o token do link `http://localhost:8081/confirmar-email?token=…` e confirme com `POST /api/v1/account/email-confirmation`. O link de redefinição de senha (`…/redefinir-senha?token=…`) sai do mesmo jeito: copie o token e use `POST /api/v1/auth/password-reset`. É a única rota que mostra o texto do e-mail, e só existe no perfil `local`.
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
    stages/<stageKey>.json        # stageKey, title, completion?, activities
```

- `completion`, opcional, é a frase do que o aluno aprendeu no resumo do fim do Estágio ("Você já sabe se apresentar e dizer de onde é."). É escrita em português, a língua da interface, e não em inglês como o curso. É texto do conteúdo, com as mesmas regras de formatação e Variáveis do Aluno.
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
- `GET /api/v1/units/{unitKey}/stages/{stageKey}`: `{versionId, stageKey, title, activities, completion?}`, com as Atividades na visão do aluno. `completion` só vem quando o Estágio tem a frase.

Sem sessão, só o primeiro Estágio da Unidade de menor `order` é liberado; os demais respondem `401 UNAUTHENTICATED`, também com sessão vencida ou de conta fora de `ACTIVE`. Com sessão de Aluno (`STUDENT`, cookie ou Bearer), todos os Estágios publicados abrem (história 2.4), inclusive os que o painel mostra bloqueados: a sequência (`409 STAGE_LOCKED`, história 2.5) vale na Tentativa, no "Ver resposta" e na conclusão, onde o Progresso é gravado, porque `content` não lê `learning` (AD-3) e a visão do aluno não tem segredo. A rota continua `@PublicRoute` e lê a sessão opcional por `SessionPrincipal.find()`. Chave fora do kebab-case é `400 INVALID_ID`; Unidade ou Estágio inexistente, `404` (`UNIT_NOT_FOUND`, `STAGE_NOT_FOUND`).

A visão do aluno sai do `StudentViewProjector`, o único projetor: ele percorre a instância com o JSON Schema e remove toda propriedade `x-fluveny-secret`. O construtor recusa um segredo fora de `properties` ou debaixo de `oneOf`, `anyOf`, `additionalProperties` e afins, e a leitura em `ActivityStudentView` recusa propriedade desconhecida. O `ContentPublicationIT` varre todas as respostas das rotas do aluno com uma fixture cheia de segredos, e o `StudentRoutesOpenApiTest` falha se um componente alcançável por rota do aluno tiver uma propriedade secreta.

### Tipo de Atividade novo

1. `content/schema/activities/<tipo>.schema.json`, com `type` como `const`, os campos secretos marcados e o `$defs.answer`. Na união de `stage.schema.json`, o valor no `enum` de `type` e um `if`/`then` para o arquivo.
2. Em `content.activity`: o record da Atividade (`@JsonTypeName`, `type` com `allowableValues`), o da visão do aluno (sem os campos secretos) e o do Answer. Acrescente cada um ao `permits`, ao `oneOf` e ao `discriminatorMapping` das três interfaces.
3. Fixtures em `src/test/resources/content-fixtures/activities/`, com o prefixo exato do Tipo: `<tipo>-activity…`, `<tipo>-student-view…` e `<tipo>-answer…`, com cada campo opcional presente numa e ausente noutra. Nos records, `requireNonNull` nos campos obrigatórios (compact constructor), e número obrigatório como `Integer`.
   - Texto novo debaixo de `oneOf`, `anyOf`, `additionalProperties` ou `prefixItems` faz o `SchemaWalker` falhar o build: as regras de texto não o veriam.
4. A regra de resposta aceita do Tipo em `ACCEPTED_ANSWER_RULES`, no `ContentValidator`. Sem ela o validador não sobe, e o `ContentValidatorTest` falha.
   - Tipo expositivo (como o Diálogo, 1.13): sem `$defs.answer`, sem record de Answer e sem regra de resposta aceita. Ele entra em `EXPOSITORY_TYPES` do `ContentValidator`, e o `ActivityFidelityTest` dispensa o Answer. No `Grader`, o ramo lança `400 INVALID_ANSWER`.
   - Regra que o JSON Schema não expressa (ex. `ask` único no Diálogo) vai em `TYPE_RULES`, no `ContentValidator`. Ex.: o Grammar Time (`grammar`, 1.19), expositivo em três abas (`used`, `rules`, `examples`), exige em cada frase de `used`, em cada variante de pronome, ao menos um trecho `**...**` com a forma destacada (`MISSING_GRAMMAR_HIGHLIGHT`). O destaque vem do conteúdo; o código não o calcula. Os records dele não reaproveitam o `GrammarRule` legado (AD-17).
5. No `learning`, o Tipo no `LearningActivityTypes`, com `sinceAppVersion` nulo até a tag do App que o renderiza (os 9 Tipos do Estágio 1 têm `0.1.0`, a tag `v0.1.0` do `fluveny-app`; a publicação num ambiente pede `FLUVENY_MIN_APP_VERSION` igual ou acima dela), e as conclusões que ele aceita em `completions` (`COMPLETIONS`; fora delas, `409 COMPLETION_NOT_ALLOWED`). A rota de conclusão fecha a Atividade sem correção: o expositivo com `VIEWED` e o Tipo avaliado que aceita pular com `SKIPPED` (a Fala, 1.15). A Resposta Livre (1.18) conclui com `VIEWED`, que devolve a `modelAnswer` no `CompletionResult` (o texto do aluno nunca vem), e a Tentativa dela é o Extra Help: as lacunas do banco, corrigidas pela regra das Lacunas, com `extraHelp: true` no Resultado. A `FirstAttempt` grava `extraHelp: true` só quando a Tentativa do Extra Help é o primeiro registro do aparelho na Atividade; com a conclusão `VIEWED` antes, o registro fica `VIEWED` com `extraHelp: false`. O "Ver resposta" do Extra Help exige que o registro seja uma Tentativa (`GRADED`): a conclusão `VIEWED` não basta (`409 ATTEMPT_REQUIRED`).
6. `./mvnw -B -ntp test`: o `ActivityFidelityTest` falha se schema, `permits`, `@JsonTypeName` e mapping divergirem, ou se propriedades, `required`, tipos e `enum` dos records divergirem do schema.

## Build e testes

```bash
mise run check   # ./mvnw -B -ntp spotless:check verify
```

- Formatação: google-java-format no estilo AOSP, pelo Spotless. O `spotless:check` falha a CI; `mise run format` corrige, e o hook do lefthook formata os arquivos Java staged a cada commit.
- Unitários (`*Test`, surefire), incluindo o `ModularityTest`, que também gera a documentação dos módulos em `target/spring-modulith-docs`.
- Integração (`*IT`, failsafe), contra o `mongo:8` em replica set do `LocalMongoContainer` (Testcontainers), sem precisar de nenhuma variável de ambiente:
  - o `ApplicationIT` sobe o contexto via `@ServiceConnection`, no perfil `test`, e confere os índices de `visitor_attempts` e de `events` criados na partida; o `VisitorAttemptsMongoIT` cobre o índice único, o TTL e a corrida; o `MetricsMongoIT` cobre os campos de cada evento, o índice único parcial do `STAGE_COMPLETED` e que 16 `stageCompleted` simultâneos do mesmo Estágio gravam um só documento;
  - o `MongoTransactionIT` confirma que as transações funcionam (AD-10): commit e rollback, com o `MongoTransactionManager` da aplicação (`platform.config`, história 2.6);
  - o `VisitorMigrationMongoIT` cobre a migração do Visitante no cadastro (história 2.6): o Estágio 1 feito pelas rotas do Visitante e migrado até o painel (`COMPLETED`, `xpTotal`, `STUDENT_REGISTERED` com `fromVisitor` e `VISITOR_MIGRATED` sem PII nem `deviceId`), a falha injetada na última inserção em `activity_results` (nada gravado em nenhuma coleção, nonces livres, e o cadastro repetido migra tudo), o e-mail repetido com recibos (nonces livres) e a corrida de dois cadastros com os mesmos recibos (os dois `201`, cada nonce numa só conta, pela retentativa do `TransientTransactionError`);
  - o `LocalDataLoaderIT` confirma que os carregadores rodam na partida, que rodar de novo não duplica e que o reset apaga o banco;
  - o `ContentPublicationIT` cobre a publicação (nova, repetida, candidata, inválida, Tipo não suportado, `unitKey` divergente, token) e as rotas do aluno, com a varredura de segredos; o `ContentLocalSeederIT` confirma que o perfil `local` publica `content/units/` sozinho, inclusive depois do reset;
  - o `MongoUriBindingIT` passa a URI do container como `SPRING_DATA_MONGODB_URI` e confirma que a API conecta por ela, no perfil `prod` padrão;
  - o `StudentProgressMongoIT` cobre `activity_results` (história 2.4): o índice único sem TTL, a leitura do que foi gravado, 16 primeiras gravações simultâneas no store e pela rota (um documento, um `ACTIVITY_COMPLETED`, `xpTotal` 5), os campos gravados, os do evento (com `metricsId`, sem `userId`, e-mail ou nome) e o Estágio 2 aberto com sessão; da história 2.5, o índice único `userId_unitKey` de `unit_progress` sem TTL, a conclusão gravada uma vez (16 gravações simultâneas no store e 16 últimas conclusões pela rota: uma conclusão, um `STAGE_COMPLETED`), o `STAGE_LOCKED` e o painel com o `xpTotal` do `/progress`; o `MetricsMongoIT` também cobre os campos do `ACTIVITY_STARTED`, do `ACTIVITY_COMPLETED` e do `STAGE_COMPLETED` do Aluno, e o `ApplicationIT`, os índices de `activity_results` e `unit_progress` na partida;
  - o `SignupIT` e o `AccountMongoIT` cobrem o cadastro e a sessão (história 2.1); o `EmailConfirmationIT` cobre o que o cadastro grava em `one_time_tokens` e `mail_outbox`, os índices único e TTL dos tokens, a confirmação, o reuso, o vencimento, o reenvio e o consumo atômico sob concorrência; o `MailOutboxMongoIT` cobre os índices de `mail_outbox` (TTL do `purgeAt`), a ordem do despacho e a marca atômica do lote com vários despachos ao mesmo tempo (história 2.2);
  - o `ProdProfileIT` confirma que, sem perfil e sem `SPRING_DATA_MONGODB_URI` ou `FLUVENY_RECEIPT_KEY`, a API não sobe e a falha cita a variável, e que com a `FLUVENY_RECEIPT_KEY` inválida ela também não sobe. Ele também confirma que a API não sobe sem `FLUVENY_WEB_ORIGINS` ou com uma origem fora do formato, nem sem `FLUVENY_PROXY_SECRET` ou com um segredo fora do formato, nem sem `FLUVENY_APP_URL`. Os testes no perfil `prod` recebem uma `FLUVENY_RECEIPT_KEY` gerada na execução pela extensão `TestReceiptKey`, uma `FLUVENY_WEB_ORIGINS` de teste pela `TestWebOrigins`, um `FLUVENY_PROXY_SECRET` gerado pela `TestProxySecret` e uma `FLUVENY_APP_URL` de teste pela `TestAppUrl`. Com o segredo, o filtro de borda liga em todo teste, então a `TestProxySecret` também põe o `X-Fluveny-Proxy` em toda requisição do MockMvc (por `META-INF/spring.factories`); o `ProxySecretTest` monta um MockMvc sem ele para provar a recusa, e cliente HTTP de verdade passa `TestProxySecret.HEADER` com `TestProxySecret.value()`.

Para rodar só os unitários (incluindo o `ModularityTest`), sem Docker: `./mvnw -B -ntp test`.

## Variáveis de ambiente

Toda configuração vem do ambiente. Nenhum segredo fica em arquivo.

| Variável | Uso |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB, com credenciais e o nome do banco. Obrigatória no perfil `prod`: sem ela a API não sobe, e o log cita a variável. É lida em `spring.mongodb.uri`, porque o Boot 4 ignora a `spring.data.mongodb.uri`. |
| `SPRING_PROFILES_ACTIVE` | Opcional. Sem ela, o perfil é `prod` (AD-18). O `local` é só para a máquina do dev (veja "Ambiente local"). |
| `LOCAL_SEED_PASSWORD` | Só no perfil `local`, no `.env.local`. Senha dos usuários criados pelos carregadores locais (os Alunos de teste da 2.1); segue a regra de senha (8 a 64 caracteres, com letra e número). |
| `SENTRY_DSN` | Opcional. Sem ela, o Sentry fica desligado e nada é enviado. Com ela, os erros 500 vão para o Sentry sem PII (usuário, IP, cookies, query string, cabeçalhos de autenticação e corpo da requisição são removidos). |
| `SENTRY_ENVIRONMENT` | Opcional. Nome do ambiente no Sentry (por exemplo, `homolog` ou `prod`). |
| `FLUVENY_MIN_APP_VERSION` | Opcional, padrão `0.0.0`. Versão mínima do App, devolvida em `GET /api/v1/meta`. Precisa estar no formato `X.Y.Z`; vazia ou em outro formato, a API não sobe. A publicação recusa Tipo com `sinceAppVersion` acima dela. |
| `FLUVENY_DEPLOY_TOKEN_SHA256` | Opcional. O sha256, em hex, do token de deploy do ambiente (o token fica só no GitHub Environment, como `FLUVENY_DEPLOY_TOKEN`). Sem ela, toda publicação é negada e a API sobe normalmente; fora do formato (64 dígitos hex), a API não sobe. Gere com `printf '%s' "$TOKEN" \| shasum -a 256`. |
| `FLUVENY_RECEIPT_KEY` | Obrigatória no perfil `prod`. Chave HMAC dos recibos do Visitante (história 1.12), em base64 com 32 bytes ou mais; gere com `openssl rand -base64 32`. Fora do formato, a API não sobe. Fora de `prod`, sem ela, a API gera uma chave na partida e avisa no log (os recibos antigos deixam de valer). |
| `FLUVENY_RECEIPT_PREVIOUS_KEY` | Opcional (história 2.6). Na rotação da `FLUVENY_RECEIPT_KEY`, a chave anterior, no mesmo formato: os recibos assinados com ela ainda migram no cadastro, mas ela nunca assina. Fora do formato ou igual à `FLUVENY_RECEIPT_KEY`, a API não sobe; enquanto ela existe, a partida avisa no log. Retire depois de 90 dias (o TTL de `visitor_attempts`). |
| `FLUVENY_WEB_ORIGINS` | Obrigatória no perfil `prod`. Origens web aceitas pela CORS de `/api/v1/**` (história 1.22), separadas por vírgula: o App do Aluno na web, por exemplo `https://app.fluveny.com`. Cada origem é `scheme://host[:porta]` (`http` ou `https`), sem caminho, sem barra no fim e sem `*`; fora do formato, a API não sobe. A CORS liga `allowCredentials` (a sessão web será por cookie), aceita `GET`, `POST`, `PUT`, `PATCH` e `DELETE` e os cabeçalhos `Content-Type`, `Accept`, `X-App-Version` e `X-Fluveny-Client`, e expõe o `Retry-After`. No perfil `local`, o padrão é `http://localhost:8081`. Fora de `prod` e do `local`, sem ela, nenhuma origem web passa. Requisição sem `Origin` (o App Android, o curl) não muda. |
| `FLUVENY_PROXY_SECRET` | Obrigatória no perfil `prod`. Segredo de proxy do ambiente (AD-18, história 1.6): o Worker da Cloudflare o envia no cabeçalho `X-Fluveny-Proxy`, e sem ele a API responde `403 ACCESS_DENIED` (problem+json) a tudo, exceto `GET /api/v1/meta`, o health check do host. A comparação é em tempo constante, e o segredo nunca vai para o log. Só com o segredo certo a API lê o IP do cliente do `X-Fluveny-Client-IP`, que o Worker preenche com o `CF-Connecting-IP` que recebe do aluno (base do limite de taxa do Visitante). O `CF-Connecting-IP` que chega à API não é lido: o Render também fica atrás do Cloudflare, que o troca pelo IP fixo do Worker na subrequisição. O `X-Forwarded-For` nunca é lido, e a `server.forward-headers-strategy` fica desligada de propósito. De 32 a 256 caracteres ASCII visíveis, sem espaço; gere com `openssl rand -hex 32`. Fora do formato, a API não sobe. Fora de `prod`, sem ela, o filtro fica desligado. O mesmo valor fica no Worker, no host da API e no secret `FLUVENY_PROXY_SECRET` do GitHub Environment. |
| `FLUVENY_APP_URL` | Obrigatória no perfil `prod`. Endereço do App do Aluno, sem barra no fim (por exemplo `https://app.fluveny.com`): base do link do e-mail de confirmação, `{FLUVENY_APP_URL}/confirmar-email?token=…` (história 2.2). No perfil `local`, o padrão é `http://localhost:8081`. |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Opcionais. SMTP genérico do outbox de e-mail (história 2.2), lido pelo Boot (`spring.mail.*`). O provedor (Brevo `[ASSUMPTION]`) só muda essas variáveis. Sem `SPRING_MAIL_HOST`, nada sai: os e-mails ficam `PENDING` em `mail_outbox` e a partida avisa no log. Conexão, leitura e escrita com 10 s de timeout. A senha é segredo: só no painel do host. |
| `FLUVENY_MAIL_FROM` | Opcional. Remetente dos e-mails, por exemplo `Fluveny <nao-responda@fluveny.com>`. Sem ele, mesmo com o SMTP, nada sai e a partida avisa no log. |
| `FLUVENY_MAIL_DAILY_QUOTA` | Opcional, padrão `300` `[ASSUMPTION: plano grátis do Brevo]`. Quantos e-mails saem por dia, contados pelos `SENT` do dia em `America/Sao_Paulo`. |
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

A coleção `events` (dona `metrics`, AD-14) é append-only, e só o `MetricsPublisher` (pacote base de `metrics`) escreve nela, com um método por evento. Cada documento leva `type`, `occurredAt` (hora do servidor) e um identificador, e só um: o `deviceId` do Visitante ou o `metricsId` do Aluno (história 2.1). Além deles, só os campos do tipo: nenhum e-mail, nome, data de nascimento, país, texto livre, IP ou cabeçalho.

| Evento | Campos | Quem envia |
| --- | --- | --- |
| `VISITOR_SESSION_STARTED` | só o identificador | o App, a cada abertura do player pelo EXPERIMENTAR AGORA |
| `ACTIVITY_STARTED` | `unitKey`, `versionId`, `activityKey` | o App, a cada abertura de Atividade, inclusive ao reabrir |
| `STAGE_COMPLETED` | `unitKey`, `versionId`, `stageKey` | `learning`, uma vez por `(deviceId, unitKey, stageKey)` |
| `STUDENT_REGISTERED` | `metricsId`, `fromVisitor` | `account`, a cada conta criada, depois do commit; `fromVisitor = true` sempre que o cadastro traz o bloco `visitor`, mesmo com todos os recibos descartados (história 2.6) |
| `VISITOR_MIGRATED` | só o `metricsId` | `learning`, depois do commit do cadastro, se ao menos um recibo do Visitante migrou (história 2.6) |
| `ACTIVITY_STARTED` do Aluno | `metricsId`, `unitKey`, `versionId`, `activityKey` | o App do Aluno, por `POST /api/v1/students/me/events` (história 2.4) |
| `ACTIVITY_COMPLETED` | `metricsId`, `unitKey`, `versionId`, `activityKey`, `outcome`, `activityType`, `resultPct` (só no `GRADED`), `firstAttempt`, `extraHelp`, `studyTimeMs` | `learning`, só quem grava o `ActivityResult` (história 2.4) com `firstAttempt: true`; no refazer (`replay: true`, história 2.7), com `firstAttempt: false`, sem gravar |
| `STAGE_COMPLETED` do Aluno | `metricsId`, `unitKey`, `versionId` (a publicada), `stageKey` | `learning`, só quem grava a conclusão em `unit_progress` (história 2.5); uma vez por `(metricsId, unitKey, stageKey)` |

O `POST /api/v1/students/me/events` responde `500` se a gravação do `ACTIVITY_STARTED` falhar, porque o evento é o próprio pedido; nas Tentativas e conclusões, a falha ao publicar o `ACTIVITY_COMPLETED` não muda a resposta.

- **Ingestão:** `POST /api/v1/visitor/events` (dona `learning`, pública), com `{type, deviceId, versionId?, activityKey?}`. O `VISITOR_SESSION_STARTED` leva só `type` e `deviceId`; o `ACTIVITY_STARTED` exige `versionId` e `activityKey`, conferidos contra o conteúdo como na Tentativa (`404` se não existem, `401 UNAUTHENTICATED` fora do Estágio liberado), e o `unitKey` vem do conteúdo. Campo desconhecido, `type` fora da lista ou `deviceId` inválido dão `400 VALIDATION_FAILED`. Responde `202` sem corpo e conta nos limites do Visitante, por IP e por `deviceId` (o mesmo contador das Tentativas e conclusões).
- **`STAGE_COMPLETED`:** quando uma Tentativa ou conclusão grava a primeira do aparelho numa Atividade, `learning` confere se as primeiras do `deviceId` (de qualquer versão, porque a `activityKey` é estável) cobrem todas as `activityKey`s do Estágio na versão da gravação. Se cobrem, publica. O `stageCompletionKey` (`identificador|unitKey|stageKey`, com o `deviceId` do Visitante ou o `metricsId` do Aluno), só desse tipo, tem índice único parcial (`MetricsIndexes`), e a duplicata, inclusive concorrente, é ignorada. Uma falha ao publicar vai para o log e o Sentry e não muda a resposta.
- O índice é criado na partida e pelo carregador local de `metrics`, como os de `learning`.

### Conta e sessão (história 2.1)

- **`users`** (dona `account`): `email` normalizado (trim e minúsculas, índice único `email_unique`), `passwordHash` (BCrypt), `birthDate` (texto `yyyy-MM-dd`), `role = STUDENT`, `status = ACTIVE`, `metricsId` (UUID aleatório, índice único `metricsId_unique`, o único identificador que vai para `events`), `name` e `pronounSet` (`HE`, `SHE` ou `THEY`; nulo vale they/them) opcionais, `termsVersion`, `privacyVersion`, `termsAcceptedAt` e `createdAt`. Os campos da AD-13 (`emailVerifiedAt`, `pendingEmail`, `deletionRequestedAt`) existem nulos. O e-mail repetido esbarra no índice único, inclusive em cadastros simultâneos, e a `DuplicateKeyException` (cuja mensagem traz o e-mail) é traduzida no `MongoUserStore`, sem ir para o log, o Sentry ou a resposta.
- **Regra de senha** única, na `PasswordPolicy` de `account` (anotação `@StudentPassword` na borda): 8 a 64 caracteres (e até 72 bytes em UTF-8, o limite do BCrypt: senhas com muitos acentos ou emoji podem precisar ser mais curtas), com ao menos uma letra e um número `[ASSUMPTION]`.
- **`sessions`** (dona `auth`): `tokenHash` (sha256 em hex do token, que são 32 bytes aleatórios em base64url; índice único `tokenHash_unique`), `userId`, `role`, `createdAt` e `expiresAt` (índice TTL `expiresAt_ttl`). A sessão do Aluno dura 30 dias e desliza: quando passou mais de 1 dia desde a última renovação, o vencimento volta a ser agora + 30 dias. O token nunca é gravado nem vai para o log.
- **`POST /api/v1/auth/signup`** (pública, tag `auth`): `{email, password, birthDate, name?, acceptedTermsVersion, acceptedPrivacyVersion, country?, visitor?}` (`country` e `visitor` na 2.6, abaixo); as versões não vazias (até 16 caracteres) são o aceite. Exige `X-Fluveny-Client: web` ou `android` (senão `400 CLIENT_HEADER_REQUIRED`). Responde `201` com `{data: {student: {name, email, country, pronounSet, emailConfirmed, emailConfirmationOverdue}, sessionToken?}}` e já entra: no `web`, o token vai só no cookie; no `android`, só no `sessionToken`. Erros: `400 VALIDATION_FAILED` (senha, aceite, e-mail, data no futuro ou antes de 1900, campo desconhecido), `422 AGE_BELOW_MINIMUM` (menor de 12, `[ASSUMPTION]`), `422 GUARDIAN_CONSENT_REQUIRED` (12 a 17, até a 2.11), `409 EMAIL_ALREADY_REGISTERED`. A idade é contada no fuso `America/Sao_Paulo`. As rotas `/api/v1/auth/**` contam no mesmo limite por IP das rotas do Visitante (`VisitorRateLimitFilter`, `fluveny.visitor.rate-limit.ip-per-minute`), porque o cadastro roda o BCrypt; acima dele, `429 TOO_MANY_REQUESTS` com `Retry-After`. Desde a 2.6, numa transação Mongo (abaixo).
- **Cookie:** `fluveny_session`, `HttpOnly; Secure; SameSite=Lax; Path=/api; Max-Age` de 30 dias, reenviado com o novo prazo quando a sessão desliza.
- **Autenticação** (`platform.security`): o `SessionAuthenticationFilter`, depois do `DeployTokenFilter`, lê o `Authorization: Bearer` (Android) ou, sem ele, o cookie (web), e chama a porta `SessionAuthenticator`, que `auth` implementa (`Sessions`) conferindo a sessão, o vencimento e o `status = ACTIVE` da conta em `account`. O principal é o `SessionPrincipal` (`userId` e `role`); nas rotas, o `userId` vem só de `SessionPrincipal.current()`. Credencial que ninguém reconhece, vencida ou de conta inativa deixa a requisição anônima, e a rota com papel responde `401` com `WWW-Authenticate`.
- **`X-Fluveny-Client`** (`ClientHeader`): toda escrita (`POST`, `PUT`, `PATCH`, `DELETE`) autenticada pelo cookie sem `web` ou `android` no cabeçalho responde `403 CLIENT_HEADER_REQUIRED`. É a proteção contra CSRF da sessão web (o CSRF do Spring fica desligado); com Bearer, o cabeçalho não é exigido.
- **Perfil mínimo** (dona `account`, `@RequiresRole(Role.STUDENT)`): `GET /api/v1/students/me` devolve `{name, email, pronounSet}`, e `PATCH /api/v1/students/me` com `{pronounSet}` grava (nulo volta a they/them). Desde a 2.9, o `PATCH` aceita também `name` e `country` (abaixo).
- O perfil (`GET /api/v1/students/me` e a resposta do cadastro) traz também `emailConfirmed` (história 2.2).
- O perfil traz também `country` (história 2.6), nulo se o Aluno não informou.

### Cadastro com o Progresso do Visitante (história 2.6)

- **Corpo:** `country?` é o `name` em inglês de um país de `content/schema/countries.json` (o que o App grava no Diálogo, 1.13), lido do classpath por `account` (o build copia o arquivo); fora da lista, `400 VALIDATION_FAILED` no campo `country`. A comparação diferencia maiúsculas de minúsculas (`brazil` é recusado); espaços nas pontas são ignorados, e `country: ""` vale como sem país (nulo). Fica em `users.country` (dado pessoal) e no perfil. `visitor?: {deviceId, receipts}` (corpo fechado) vem só do convite do Visitante: de 1 a 200 recibos, cada um até 1024 caracteres; fora disso, `400 VALIDATION_FAILED`.
- **Transação:** o `Signup` de `auth` abre uma transação Mongo (`MongoTransactionManager` em `platform.config`) que cobre `users`, a migração inteira e `sessions`; o `RegisterStudent.register` é `@Transactional` (`REQUIRED`). Qualquer falha desfaz tudo (`500`, nada gravado). Um erro com o rótulo `TransientTransactionError` (a corrida de dois cadastros com os mesmos recibos dá `WriteConflict`) refaz a transação inteira, até 3 vezes, com uma espera curta e aleatória entre elas; o e-mail que o outro cadastro gravou vira `409 EMAIL_ALREADY_REGISTERED` na nova tentativa. Senha, idade e país são conferidos e o BCrypt roda uma vez, antes da transação (`RegisterStudent.prepare`). Se o commit terminar com `UnknownTransactionCommitResult`, o cliente pode receber `500` com a conta criada: repetir o cadastro dá `409`, e o Aluno entra pelo Entrar. O `STUDENT_REGISTERED`, o `VISITOR_MIGRATED` e a emissão da confirmação de e-mail (2.2) rodam depois do commit (`AfterCommit`), com log e Sentry em falha, sem mudar a resposta; desfeita a transação, nenhum deles acontece. As coleções da transação são criadas na partida pelos índices de cada módulo: desde o MongoDB 4.4 a transação poderia criá-las, mas sem os índices únicos de que a deduplicação depende, e duas transações que criam a mesma coleção dão conflito. As rotas do Aluno e do Visitante seguem sem transação.
- **Evento de domínio:** `account` publica o `StudentRegistered` `{userId, metricsId, visitor?}` (named interface `platform :: events`, sem e-mail, nome, país nem data) logo depois de gravar `users`; `learning` trata com `@EventListener` síncrono, na mesma thread e transação. Os tipos de evento no sentido inverso do grafo ficam em `platform.events`, para não fechar ciclo (o `AccountDeletionRequested` da 2.10 iria de `account` para `mail`).
- **Migração** (`VisitorMigration.migrate(userId, deviceId, receipts)` em `learning`, `@Transactional(MANDATORY)`, reutilizável na 2.11): para cada recibo, descarta sem lançar se o `ReceiptVerifier` recusa (formato, HMAC, JSON), se o `deviceId` do payload não é o do bloco, se o `issuedAt` passou de 90 dias (o TTL de `visitor_attempts`) ou está mais de 5 min no futuro, se a versão ou a Atividade não existem no conteúdo, ou se o nonce não é o do documento de `visitor_attempts` ou já foi consumido. O consumo é um update condicional (`deviceId`, `activityKey`, `nonce`, sem `consumedAt`) que grava `consumedAt`, sem depender de chave duplicada (que abortaria a transação); assim o recibo reemitido e a mesma Atividade repetida na lista ficam com um só. Cada recibo migrado vira um `ActivityResult` por `insert` simples (a conta é nova): `outcome`, `resultPct` e `xpAwarded` do payload (`VIEWED`/`SKIPPED` com XP 0 e sem `resultPct`), `attemptId`, `studyTimeMs` e `extraHelp` do documento do Visitante, `stageKey` e Tipo do conteúdo. Na mesma transação, as conclusões de Estágio cobertas vão para `unit_progress` pela reconciliação da 2.5, sem publicar `STAGE_COMPLETED` nem `ACTIVITY_COMPLETED` (o Visitante já contou pelo `deviceId`). Recibo migrado não passa pela trava `STAGE_LOCKED`. O log leva só as contagens.
- **Rotação da chave dos recibos:** `FLUVENY_RECEIPT_PREVIOUS_KEY`, opcional, só confere (nunca assina) e não pode ser igual à `FLUVENY_RECEIPT_KEY`; enquanto ela existe, a partida avisa no log. O formato `v1` não muda.
- **Depois da migração:** se o Visitante jogar de novo no mesmo aparelho, as rotas do Visitante devolvem o recibo já gravado, com o nonce consumido: ele não migra de novo para nenhuma conta.
- Para o App: mandar `visitor` só vindo do convite, limpar a loja do Visitante depois do `201` (mesmo com recibos descartados), mapear o `400` de `country` e ir ao painel.

### Progresso e XP do Aluno (história 2.4)

- **Rotas** (dona `learning`, `@RequiresRole(Role.STUDENT)`, tag `students`; o `userId` vem só da sessão): `POST /api/v1/students/me/attempts` e `/attempts/reveal` com `{attemptId, versionId, activityKey, type, answer, studyTimeMs}`, `POST /api/v1/students/me/completions` com `{attemptId, versionId, activityKey, outcome, studyTimeMs}`, `POST /api/v1/students/me/events` com `{type: ACTIVITY_STARTED, versionId, activityKey}` (`202` sem corpo) e `GET /api/v1/students/me/progress`. Corpos fechados: campo desconhecido (como `userId` ou `deviceId`) dá `400 VALIDATION_FAILED`. `studyTimeMs` de 0 a 86 400 000 (24 h). As respostas são os mesmos `AttemptResult` e `CompletionResult` do Visitante, sem `receipt`. Com a sessão do navegador (cookie), as escritas exigem `X-Fluveny-Client` (`403 CLIENT_HEADER_REQUIRED`).
- **Correção:** a mesma do Visitante (`AttemptGrading` sobre o `Grader.grade`), com o pronome da conta (`StudentIdentities`, leitura de `account` sem PII); o App continua trocando nome e país pelos tokens. Tipo divergente ou expositivo, `400 INVALID_ANSWER`; conclusão fora do Tipo, `409 COMPLETION_NOT_ALLOWED`; "Ver resposta" sem registro (na Resposta Livre, sem uma Tentativa `GRADED` do Extra Help), `409 ATTEMPT_REQUIRED`; versão ou Atividade inexistente, `404`. Qualquer versão existente vale; a sequência dos Estágios entra na 2.5 (`409 STAGE_LOCKED`, abaixo).
- **`activity_results`** (dona `learning`): um documento por `(userId, unitKey, activityKey)`, índice único `userId_unitKey_activityKey` (`LearningIndexes`), sem TTL. Guarda `userId`, `unitKey`, `activityKey`, `stageKey`, `versionId`, `activityType`, `attemptId`, `outcome`, `resultPct` (ausente em `VIEWED`/`SKIPPED`), `xpAwarded`, `studyTimeMs`, `extraHelp` e `createdAt`. Só a primeira Tentativa ou conclusão grava, por upsert `$setOnInsert`; a corrida perdida lê o vencedor. É dado pessoal ligado ao `userId`: a exclusão da 2.10 apaga por `userId`.
- **XP único:** o `xpAwarded` gravado é o do `grade()` (proporcional ao Resultado, até o `xp` do conteúdo); `VIEWED` e `SKIPPED` dão 0. O reenvio do mesmo `attemptId` com o mesmo `outcome` e `resultPct` devolve o XP gravado; outro `attemptId` ("Tentar de novo", refazer) ou outro corpo devolve o Feedback normal com `xpAwarded` 0, sem gravar nem publicar.
- **Progresso:** `GET /progress` devolve `{xpTotal, results: [{unitKey, activityKey, outcome, resultPct?, xpAwarded, studyTimeMs}]}`, na ordem de gravação; `xpTotal` é somado de `activity_results` a cada leitura, sem cache, inclusive de Atividades removidas.
- **Eventos:** quem grava o `ActivityResult` publica o `ACTIVITY_COMPLETED` (`firstAttempt: true`); o refazer da 2.7 publica o de `firstAttempt: false` (abaixo). Sem transação (na 2.6, só o cadastro abre uma): uma falha ao publicar vai para o log e o Sentry e não muda a resposta nem desfaz a gravação.
- **Limite:** 60 escritas por minuto por Aluno nas quatro rotas `POST`, num só contador (`fluveny.student.rate-limit.per-minute`, em memória, por instância, janela fixa); acima, `429 TOO_MANY_REQUESTS` com `Retry-After`.
- **Carregador local:** o de `learning` só recria os índices; não há Progresso de exemplo.

### Resumo do Estágio e refazer (história 2.7)

- **`GET /api/v1/students/me/units/{unitKey}/stages/{stageKey}/summary`** (dona `learning`, `@RequiresRole(Role.STUDENT)`, tag `students`, fora do limite de escritas): `StudentStageSummary` `{unitKey, stageKey, xpAwarded, averageResultPct?, skippedCount, studyTimeMs}`. Entram os `ActivityResult` cujas `activityKey`s estão no Estágio na versão publicada, gravados em qualquer versão: soma do XP, média arredondada dos `GRADED` (a mesma conta do painel; ausente sem nenhum), contagem dos `SKIPPED` e soma do `studyTimeMs` em milissegundos (a rota não arredonda). Os números são os da primeira conclusão; refazer não muda o resumo. A leitura reconcilia `unit_progress` da Unidade como o painel. Estágio não concluído: `409 STAGE_NOT_COMPLETED`; Unidade ou Estágio fora da versão publicada: `404 NOT_FOUND`.
- **`replay`:** campo opcional (boolean, padrão `false`) em `StudentAttempt` e `StudentCompletion`. O App manda `true` só na primeira resposta (e nos reenvios dela) de cada abertura de Atividade que já tinha registro; "Tentar de novo" vai sem ele; o `/attempts/reveal` aceita e ignora. Com registro existente, `attemptId` diferente do gravado e `replay: true`, a resposta é o Feedback normal com `xpAwarded` 0 e sai um `ACTIVITY_COMPLETED` `firstAttempt: false` com `versionId`, `outcome`, Tipo, `resultPct` (só no `GRADED`), `extraHelp` e `studyTimeMs` desta resposta; nada é gravado em `activity_results` nem em `unit_progress`. Sem registro, `replay: true` conta como a primeira. O reenvio de uma resposta de refazer publica de novo (limite aceito, sem deduplicar).

### Painel, sequência e retomada (história 2.5)

- **`GET /api/v1/students/me/dashboard`** (dona `learning`, `@RequiresRole(Role.STUDENT)`, tag `students`, fora do limite de escritas): `{name?, xpTotal, badgeCount, units: [{unitKey, versionId, order, title, comingSoon, started, stagesCompleted, stagesTotal, averageResultPct?, stages: [{stageKey, title, state}], next?: {stageKey, activityKey, position, total}}]}`, com `state` em `COMPLETED`, `CURRENT` ou `LOCKED`. `name` vem de `account` (ausente sem nome); `badgeCount` é 0 até a 3.9. Tudo é calculado a cada leitura, sem cache. A leitura reconcilia as conclusões (abaixo): pode gravar a conclusão de um Estágio coberto, com `completedAt` na hora da leitura, e publicar o `STAGE_COMPLETED`.
- **Cálculo, sobre a versão publicada de cada Unidade** (`PublishedStructures`, a estrutura ordenada que `content` expõe pelo pacote base, sem campo secreto): uma Atividade está concluída se há `ActivityResult` dela (qualquer `outcome`, de qualquer versão, porque a `activityKey` é estável). `stagesTotal` são os Estágios da versão publicada; `stagesCompleted`, os deles gravados em `unit_progress`. `CURRENT` é o primeiro não concluído; os seguintes não concluídos são `LOCKED`. `next` é a primeira Atividade sem registro do `CURRENT`, com `position` (a partir de 1) e `total` do Estágio. `started` diz se há registro em alguma Atividade da versão publicada da Unidade (registro de Atividade removida não conta). `averageResultPct` é a média arredondada do `resultPct` dos `GRADED` da versão publicada; puladas e expositivas não entram. Registro de Atividade removida só conta no `xpTotal` (FR-29).
- **`unit_progress`** (dona `learning`): um documento por `(userId, unitKey)`, índice único `userId_unitKey` (`LearningIndexes`), sem TTL, com `stages.<stageKey>: {versionId, completedAt}`. Um Estágio conclui quando os registros do Aluno cobrem todas as `activityKey`s dele na versão publicada. Cada conclusão é gravada uma vez (update condicional à ausência do Estágio, com `upsert`; a corrida que perde a criação do documento tenta de novo sem `upsert`) e nunca reescrita nem apagada; uma Atividade nova num Estágio concluído fica opcional. A conclusão é conferida depois de cada primeira gravação em `activity_results`, na leitura do painel e na trava (`StageSequence`), então um Estágio que ficou coberto porque uma versão nova removeu a Atividade pendente é gravado sem nova Tentativa. A conclusão da Unidade, o Badge e o `UNIT_COMPLETED` ficam para a 3.9. É dado pessoal ligado ao `userId`: a exclusão da 2.10 apaga por `userId`.
- **`STAGE_COMPLETED` do Aluno:** só quem gravou a conclusão publica, com o `metricsId` e a versão publicada; o índice único de `events` segura a duplicata. Uma falha ao publicar vai para o log e o Sentry e não muda a resposta nem desfaz a gravação.
- **Trava (AD-7):** um Estágio está liberado se é o primeiro da versão publicada, se o anterior nela está concluído ou se ele mesmo já está concluído. Tentativa, "Ver resposta" e conclusão em Estágio bloqueado dão `409 STAGE_LOCKED`, sem gravar, sem XP e sem evento (na Tentativa e no "Ver resposta", antes de corrigir). O Estágio é o da Atividade na versão publicada, mesmo com `versionId` antigo; Atividade ou Estágio ausente da versão publicada não trava. O `POST /students/me/events` e o `getStage` não travam. Unidades não se travam entre si até a 3.9.

### Entrar e sair (história 2.3)

- **`POST /api/v1/auth/login`** (pública, tag `auth`): `{email, password}`, corpo fechado. Exige `X-Fluveny-Client: web` ou `android` (senão `400 CLIENT_HEADER_REQUIRED`). Responde `200` no mesmo formato do cadastro (`{data: {student, sessionToken?}}`, cookie no `web`, token no `android`), pela mesma montagem (`SessionResponses`). O e-mail é normalizado como no cadastro. O login bem-sucedido encerra a sessão que a requisição já trazia (cookie ou Bearer), como o logout.
- **Credenciais** (`Credentials`, em `account`): e-mail desconhecido, senha errada, conta `INVITED` (sem senha utilizável) e, até a 2.11, `PENDING_GUARDIAN_CONSENT` dão o mesmo `401 INVALID_CREDENTIALS` ("E-mail ou senha incorretos."). O e-mail desconhecido roda o BCrypt contra um hash fixo tantas vezes quanto a senha errada rodaria (duas se a senha recebida não está em NFC), para o tempo não revelar a conta. Só com a senha certa a conta `DEACTIVATED` dá `403 ACCOUNT_DEACTIVATED`, sem sessão.
- **Senha em NFC:** cadastro, regra (`PasswordPolicy`) e conferência (`Passwords`) normalizam a senha em NFC antes do BCrypt. A conferência tenta a forma NFC e, se falhar, a recebida, para as contas criadas antes da 2.3.
- **`login_throttle`** (dona `auth`, `LoginThrottle`): três contadores de falha, por origem (IP), por conta + origem e por conta. Cada documento tem só `keyHash` (sha256 em hex de um prefixo fixo com o e-mail normalizado e/ou o IP; índice único `keyHash_unique`), `kind` (`ip`, `account_ip`, `account`), `failures`, `lastFailureAt` e `expiresAt` (1 h depois da última falha, índice TTL `expiresAt_ttl`); nenhum e-mail nem IP em claro. A origem tem 20 falhas livres (uma rede compartilhada, como o laboratório de uma turma, soma os erros de digitação de todos) e conta + origem e conta têm 3; depois das `n` falhas livres, espera de `2^(falhas-n)` s desde a última falha, com teto de 15 min para a origem e conta + origem e de 30 s para a conta `[ASSUMPTION]` (o titular, de outra origem, espera no máximo 30 s por causa de terceiros). Tentativa antes do fim da espera: `429 LOGIN_THROTTLED` com `Retry-After` (o maior dos três), sem conferir a senha e sem contar falha. O sucesso zera conta + origem e conta, nunca a origem. Sem bloqueio rígido. O IP é o `getRemoteAddr()` que o `ProxySecretFilter` resolve; `X-Forwarded-For` é ignorado. A origem é o IPv4 inteiro ou o prefixo /64 do IPv6 (endereço canônico por `InetAddress.ofLiteral`, sem resolver nome). Conferir o atraso, conferir a senha e registrar a falha ou o sucesso rodam sob um lock da origem e depois da conta (stripes por tipo de chave), então uma rajada em paralelo não escapa do atraso; o lock vale por instância (no Render há uma só). O limite por IP do `VisitorRateLimitFilter` continua valendo em `/api/v1/auth/**`.
- **`POST /api/v1/auth/logout`** (pública): exige `X-Fluveny-Client`; apaga a sessão do token recebido (cookie ou Bearer), se houver, e ela deixa de autenticar na hora. Sempre `204`; no `web`, com o `Set-Cookie` que apaga o cookie (`Max-Age=0`, mesmos atributos).
- **Conta fora de `ACTIVE`:** a sessão de conta `DEACTIVATED` ou `INVITED` dá `401` com `WWW-Authenticate` em toda rota `@RequiresRole(STUDENT)`, no web e no Android.
- **Carregador local:** além dos dois Alunos ativos, `aluno.desativado@fluveny.local` (`DEACTIVATED`, mesma `LOCAL_SEED_PASSWORD`), para o `403 ACCOUNT_DEACTIVATED`.

### Dados da conta e pronomes (história 2.9)

- **`PATCH /api/v1/students/me`** (dona `account`, `STUDENT`): `{name?, country?, pronounSet?}`, corpo fechado, ao menos um campo. Ausente não muda; `null` (ou nome/país em branco) limpa, e os pronomes voltam a they/them. `name` até 60 caracteres; `country` é o `name` em inglês de um país de `countries.json` (as regras do cadastro). Fora disso, corpo vazio ou campo desconhecido: `400 VALIDATION_FAILED`, sem gravar nada. O corpo só com `pronounSet` da 2.1 segue valendo. Grava por `StudentProfiles.updateProfile(userId, ProfileChange)`, a única porta de escrita de nome, país e pronomes, e devolve o `StudentProfile`. O corretor lê o pronome a cada Tentativa: a troca vale na Fala seguinte.
- **`PUT /api/v1/students/me/dialogue-answers`** (dona `learning`, `STUDENT`, no limite de escritas do Aluno): `{versionId, activityKey, name?, country?}`, corpo fechado. Só aceita o campo que o Diálogo daquela versão pergunta (fala com `ask`); Atividade inexistente, que não é Diálogo, sem a fala do campo ou sem nenhum campo: `400 VALIDATION_FAILED`. Grava por `account.updateProfile`; `204`. O pré-preenchimento sai do `GET /students/me`.
- **`POST /api/v1/students/me/password`** (dona `auth`, `STUDENT`): `{currentPassword, newPassword}`, corpo fechado; `newPassword` na regra única. A conferência e a gravação ficam em `AccountPassword` (`account`), com gravação condicional ao hash atual. Senha atual errada: `400 CURRENT_PASSWORD_INVALID` ("A senha atual está incorreta."), nunca `401`. Depois de 5 erros por conta em 15 minutos (em memória, por instância, somando a troca de e-mail), `429 TOO_MANY_REQUESTS` com `Retry-After` até a janela acabar, mesmo com a senha certa. Sucesso: hash novo, apaga os tokens `PASSWORD_RESET` e encerra todas as outras sessões, mantendo a da requisição (`Sessions.revokeAllExcept`; o token vem do Bearer ou, sem ele, do cookie); `204`. Sem transação, como na 2.8.
- **`POST /api/v1/students/me/email`** (dona `account`, `STUDENT`): `{newEmail, currentPassword}`, corpo fechado, com a mesma conferência e o mesmo limite da troca de senha. Normaliza (trim, minúsculas). Igual ao atual: `400 VALIDATION_FAILED`; de outra conta: `409 EMAIL_ALREADY_REGISTERED`. Senão grava `pendingEmail`, apaga os tokens `EMAIL_CHANGE` anteriores (um pedido novo substitui o anterior), emite outro (hash, `email` = o novo, 24 h) e enfileira, com `HIGH`, "Confirme seu novo e-mail do Fluveny" com `{FLUVENY_APP_URL}/confirmar-novo-email?token=…` ao novo e um aviso sem link ao antigo ("Pediram para trocar o e-mail da sua conta para n***@d***. Se não foi você, troque sua senha."). `202`. O perfil traz `pendingEmail` até a confirmação.
- **`POST /api/v1/account/email-change/confirm`** (pública): `{token}`. Consome o token e, numa só atualização condicional (`UserStore.promotePendingEmail`), troca `email` pelo `pendingEmail` se ele ainda é o do token, zera `pendingEmail` e grava `emailVerifiedAt`. O índice único `email_unique` decide a corrida: e-mail tomado nesse meio-tempo dá `409 EMAIL_ALREADY_REGISTERED` e limpa `pendingEmail`. Apaga os tokens `EMAIL_CHANGE`, `EMAIL_CONFIRMATION` e `PASSWORD_RESET` da conta; as sessões seguem. Token inválido, vencido, usado ou de pedido substituído: `410 EMAIL_CHANGE_LINK_INVALID`; `204`.
- **Carregador local:** `aluno@fluveny.local` ganha o país `Brazil`; cada `seed` zera o `pendingEmail` e volta nome, país e pronomes dos Alunos de teste. No local, o link da troca sai em `GET /local/mail` como os outros.
- Senha, token, link e e-mail nunca vão para o log, o Sentry ou o `toString`. Sair de todos os aparelhos não entra (a troca de senha já cobre).

### Recuperação de senha e faixa do e-mail não confirmado (história 2.8)

- **`POST /api/v1/auth/password-recovery`** (pública, tag `auth`): `{email}`, corpo fechado (`400 VALIDATION_FAILED` com e-mail inválido ou campo desconhecido). Sempre `202` sem corpo, para e-mail cadastrado ou não, e sem `X-Fluveny-Client` (com o cookie de uma sessão válida, o `SessionAuthenticationFilter` continua exigindo). A rota só valida o corpo e os limites e responde: a busca da conta, o token e o outbox rodam depois, no `PasswordRecoveryExecutor` de `auth` (1 thread, fila de 100; fila cheia descarta o pedido com um aviso no log, sem o e-mail; no encerramento da API, os pedidos na fila são descartados com a contagem no log, e um pedido depois disso tem um aviso próprio). Uma falha na tarefa vai para o log e o Sentry sem a mensagem original. Assim, resposta, código e tempo não dependem de a conta existir; um pedido perdido numa reinicialização custa só pedir de novo.
- **Quem recebe:** só conta `ACTIVE` com `emailVerifiedAt` (FR-2 `[ASSUMPTION]`: sem confirmar, não recupera). Desconhecido, não confirmado, `INVITED`, `PENDING_GUARDIAN_CONSENT` ou `DEACTIVATED`: nada é gravado nem enviado. O e-mail é normalizado como no cadastro.
- **Limites** (`PasswordRecovery`, em memória e por instância, além dos 300/min por IP do `VisitorRateLimitFilter`): 20 pedidos por hora por IP (a origem do `LoginThrottle`: IPv4 inteiro ou /64 do IPv6) dão `429 TOO_MANY_REQUESTS` com `Retry-After`, igual para qualquer e-mail; em silêncio (segue `202` e nada é enfileirado, porque um `429` só para contas existentes revelaria a conta), com chaves sha256, nunca o e-mail em claro: 1 por minuto por e-mail normalizado, 5 por dia por e-mail e origem e 20 por dia por e-mail. Assim, um terceiro que saiba o e-mail não esgota sozinho a cota do titular.
- **Token** (`PasswordReset`, em `account`): `purpose = PASSWORD_RESET` em `one_time_tokens`, com o hash, o `email` da conta e validade de 1 h. Um pedido novo não apaga os links anteriores, que seguem válidos até vencer (um terceiro que saiba o e-mail não invalida o link que o titular já recebeu); o uso de um apaga os que sobrarem.
- **E-mail** (texto puro, `HIGH`): "Redefina sua senha do Fluveny", saudação pelo nome (só letras, senão "Olá!"), o link `{FLUVENY_APP_URL}/redefinir-senha?token=…`, "O link vale por 1 hora e só funciona uma vez." e "Se você não pediu, ignore este e-mail. Sua senha continua a mesma.".
- **`POST /api/v1/auth/password-reset`** (pública): `{token, password}`, corpo fechado. A senha segue a regra única (`@StudentPassword`, NFC): fora dela, `400 VALIDATION_FAILED`, sem gastar o token. Consome o token (`findAndRemove` por hash, propósito e prazo: uso único, inclusive em paralelo), grava o hash novo só se a conta ainda está `ACTIVE`, confirmada e com o e-mail do token (`UserStore.updatePasswordHash`), e depois apaga todas as sessões da conta (`Sessions.revokeAll`, índice `userId` em `sessions`). Responde `204` sem corpo e não abre sessão: o App leva a Entrar. Token desconhecido, vencido, usado ou de conta que mudou depois do pedido (desativada, e-mail trocado): `410 PASSWORD_RESET_LINK_INVALID` ("Este link de redefinição não vale mais. Peça outro."). O `login_throttle` não é zerado.
- **Sem transação:** a senha é gravada antes de apagar as sessões. Se o Mongo cair entre as duas, a senha nova vale e as sessões antigas ficam até o TTL.
- **Faixa:** o perfil (`GET /api/v1/students/me`, login e cadastro) traz `emailConfirmationOverdue`, `true` quando o e-mail segue sem confirmar e `createdAt + 7 dias <= agora` (relógio do servidor); no cadastro sai sempre `false`. O perfil é montado num lugar só (`StudentProfiles.toProfile`). O Reenviar é o `POST /api/v1/students/me/email-confirmation` da 2.2.
- Senha, token, link, hash e e-mail nunca vão para o log, o Sentry ou a resposta (fora o `GET /local/mail` do perfil `local`). Aviso de "senha alterada", troca de senha logada e sessões múltiplas ficam para a 2.9.

### E-mail e confirmação (história 2.2)

- **Porta `MailSender`** (named interface `mail :: sender`, AD-15): `enqueue(to, subject, text, priority)` só grava em `mail_outbox` e volta; nada é enviado na requisição. `account` declara `"mail :: sender"` (a mesma seta account → mail da AD-3), porque o Spring Modulith tira os tipos de uma named interface do pacote base. `HIGH` (redefinição de senha, consentimento) sai antes de `NORMAL` (confirmação). Só texto puro, sem HTML nem SDK do provedor.
- **`mail_outbox`** (dona `mail`): `to`, `subject`, `text`, `priority`, `status` (`PENDING`, `SENT`, `FAILED`), `attempts`, `nextAttemptAt`, `createdAt`, `sentAt` e `purgeAt` (índice TTL `purgeAt_ttl`: o e-mail some 30 dias depois de `SENT` ou `FAILED`; o `PENDING` fica). Índices `dispatch` (`status`, `priority`, `createdAt`) e `status_sentAt` (a cota).
- **Despacho** (`MailDispatcher`, agendado a cada 30 s com `@EnableScheduling`; `fluveny.mail.dispatch.enabled=false` desliga, como nos testes): com o SMTP configurado, conta os `SENT` do dia em `America/Sao_Paulo`, marca de forma atômica (`findAndModify`, um por vez, adiando o `nextAttemptAt` por 10 min) um lote de até 50 `PENDING` vencidos que caiba na cota, por prioridade e `createdAt`, e envia. Dois despachos ao mesmo tempo não pegam o mesmo e-mail; se a instância cair no meio do envio, ele volta à fila depois dos 10 min. Cota esgotada: nada é marcado nem gasta tentativa, e o resto espera a virada do dia. Falha: a tentativa conta e a próxima vem em 1, 2, 4… minutos, até 1 h; na 8ª, `FAILED`, com log e Sentry só com o id e a classe da exceção (a mensagem do SMTP pode trazer o endereço). No Render free, o despacho roda enquanto a instância está acordada; o cadastro a acorda, então o e-mail sai em até ~30 s.
- **`one_time_tokens`** (dona `account`): `tokenHash` (sha256 em hex, índice único `tokenHash_unique`), `userId`, `purpose = EMAIL_CONFIRMATION`, `email` (o que o token confirma), `createdAt` e `expiresAt` (7 dias `[ASSUMPTION]`, índice TTL `expiresAt_ttl`). O token é gerado e guardado como hash pela `OpaqueTokens` (`platform.security`), a mesma das sessões. O consumo é um `findAndRemove` por hash, propósito e prazo: uso único, inclusive com duas confirmações ao mesmo tempo.
- **Cadastro:** depois de gravar a conta e publicar o evento, o `RegisterStudent` cria o token e enfileira o e-mail "Confirme seu e-mail no Fluveny" (saudação pelo nome ou "Olá!", o link, a validade de 7 dias e "Se você não criou uma conta, ignore este e-mail."). Uma falha aqui vai para o log e o Sentry sem a mensagem original e não desfaz o cadastro; o Aluno usa o reenvio.
- **`POST /api/v1/account/email-confirmation`** (pública, tag `account`): `{token}` (corpo fechado). Consome o token e grava o `emailVerifiedAt` só se o e-mail do token ainda é o da conta; responde `200` com `{data: null, message}`. Token desconhecido, vencido ou já usado: `410 CONFIRMATION_LINK_INVALID`. Conta no limite por IP do `VisitorRateLimitFilter` (`/api/v1/account/**`).
- **`POST /api/v1/students/me/email-confirmation`** (`STUDENT`): apaga os tokens anteriores do Aluno, cria outro e enfileira; `202` sem corpo. Já confirmado: `409 EMAIL_ALREADY_CONFIRMED`. No máximo 1 por minuto e 5 por dia por Aluno (em memória, por instância, janelas fixas): `429 TOO_MANY_REQUESTS` com `Retry-After`.
- O token, o link e o endereço nunca vão para o log, o Sentry, o evento ou a resposta; só para o texto do e-mail (e para o `GET /local/mail` do perfil `local`).

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

`Role.DEPLOY` é o papel do principal de máquina do job "Publicar conteúdo", fora da hierarquia (nenhum usuário o alcança). O `DeployTokenFilter` lê `Authorization: Bearer <token>`, calcula o sha256 e compara em tempo constante com `FLUVENY_DEPLOY_TOKEN_SHA256`. Sem o cabeçalho, ou com um Bearer que ninguém reconhece (token errado, a variável ausente, ou sessão vencida), uma rota com papel responde `401 UNAUTHENTICATED` (até a 2.1, o Bearer desconhecido dava `403`). Rotas públicas ignoram o Bearer.

A anotação vale no método ou na classe do controller, e a do método vence. Handler sem declaração, com `@PublicRoute` e `@RequiresRole` juntos ou com `@RequiresRole({})` responde `ACCESS_DENIED` (401 anônimo, 403 autenticado), e o `RouteDeclarationScanTest` falha o build nomeando o handler. Handler que não é método de controller (um `RouterFunction`, por exemplo) também é negado, porque não tem onde declarar papel. As únicas exceções por caminho são as da documentação: `/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/**`, `/swagger-ui.html` e `/swagger-ui/**`. O Aluno entra pela sessão criada no cadastro (história 2.1) ou no login (história 2.3), por Bearer ou cookie: veja "Conta e sessão" e "Entrar e sair". A sessão da equipe chega na 4.2.

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
   - `FLUVENY_MIN_APP_VERSION=0.1.0` (a tag `v0.1.0` do `fluveny-app`; abaixo dela a publicação recusa os Tipos do Estágio 1 com `422 UNSUPPORTED_ACTIVITY_TYPE`);
   - `FLUVENY_APP_URL` com a origem do App de homologação (a mesma do Worker, sem barra no fim), para o link do e-mail de confirmação;
   - `SENTRY_ENVIRONMENT=homolog` e, se houver, `SENTRY_DSN`;
   - quando houver credencial SMTP: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e `FLUVENY_MAIL_FROM`. Até lá, os e-mails ficam `PENDING` em `mail_outbox` e saem quando as variáveis entrarem (respeitando a cota do dia).

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

   Prove a publicação sem publicar nada (Decisão 2 da história 1.6): com `FLUVENY_DEPLOY_TOKEN` e `FLUVENY_PROXY_SECRET` exportados só na sessão, `node scripts/content-bundle.mjs publish unit-1 --api https://fluveny-api-homolog.onrender.com --content src/test/resources/content-fixtures/missing-accepted-answer` precisa sair com `422` e `INVALID_CONTENT` (regra `MISSING_ACCEPTED_ANSWER`): token, segredo de proxy e URL estão certos, e a API recusa o pacote antes de olhar os Tipos, seja qual for o `FLUVENY_MIN_APP_VERSION`. Não use a fixture `content-fixtures/valid` nessa prova: com `FLUVENY_MIN_APP_VERSION=0.1.0` (história 1.20), a API aceita os Tipos dela e o comando publicaria a fixture por cima da `unit-1` de homologação. A publicação real vem pelo workflow "Publicar conteúdo", que também serve de prova.

   Rode o "Manter homologação acordada" à mão e veja o ping passar. Acompanhe a memória no painel do Render (Metrics) enquanto percorre o App: ela não pode reiniciar por falta de memória.

Se a API não couber nos 512 MB do Render, o plano B da spine é uma VM Oracle Always Free (Arm, com imagem multi-arch).

## Contribuindo

Fluxo de branches, commits e PR no [`CONTRIBUTING.md`](CONTRIBUTING.md).
