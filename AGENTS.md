<!-- bmad:context -->
<!-- Verified 2026-11-16 against c77899794c538bcb197f4824bdb3c780c2e455b7 (main). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## fluveny-api

API do Fluveny: Java, Spring Boot e MongoDB, e o alvo é um monólito modular com Spring Modulith. As decisões ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)), clonado como pasta pai deste: `../_bmad-output/planning-artifacts/`. A spine (`architecture/architecture-Fluveny-2026-11-13/ARCHITECTURE-SPINE.md`) vence qualquer código deste repositório.

## Policy

- Nunca dê push na `main`. Trabalhe numa branch curta, abra PR e faça squash na `main`. A proteção exige os checks `Segredos`, `Build e testes` e `Imagem` verdes e a branch atualizada, mas admins podem passar por cima. Nunca use esse bypass (`gh pr merge --admin`) para mesclar com a CI vermelha (AD-2). Com a CI verde, mescle sem esperar pedido.
- Não estenda, não conserte nem porte o domínio legado: Módulo, GrammarRule, exercícios, JWT, `RunOnStartup`, login por username e coleções `fl_*` (AD-17).
- Trabalho novo vai num dos 9 pacotes de módulo (`account`, `auth`, `team`, `content`, `learning`, `metrics`, `survey`, `mail`, `platform`). As dependências permitidas ficam no `package-info.java` de cada módulo e seguem o grafo da AD-3; mudar o grafo é mudar a spine.
- Nunca grave segredo, senha ou token em código, `.properties` ou `docker-compose`. A URI do Mongo vem só de `SPRING_DATA_MONGODB_URI`, lida em `spring.mongodb.uri` (o Boot 4 ignora a `spring.data.mongodb.uri`); o `MongoUriBindingIT` prova o caminho. Achados antigos do gitleaks ficam na `.gitleaksignore`; não acrescente achados novos lá, remova o segredo.
- Implemente histórias com `bmad-build <número>` e revise com `bmad-code-review` num contexto novo. Depois do merge, a história muda de status em `../_bmad-output/implementation-artifacts/sprint-status.yaml` e no card do [Project](https://github.com/orgs/Fluveny/projects/12).

## Running and verifying

- Antes de abrir PR: `mise run check` (`./mvnw -B -ntp spotless:check verify`), com Docker ligado. É o que a CI roda: formatação, unitários (surefire), o `ModularityTest` e os `*IT` (failsafe). Os ITs usam o `mongo:8` em replica set do `LocalMongoContainer`; não precisam de Mongo local nem de variável. Formato fora do padrão: `mise run format`.
- JDK 25 e lefthook vêm do `mise.toml` (`mise install`). Sem o mise, rode o build num container `maven:3-eclipse-temurin-25` (comando no README).
- O build usa a versão de Java declarada em `java.version` no `pom.xml`. Não use a versão instalada na máquina.
- Imagem: `docker build -t fluveny-api:local .`. A CI publica no GHCR a cada push na `main`.
- API local: `mise run up` (perfil `local`, `./mvnw spring-boot:test-run` pela `LocalFluvenyApplication` dos testes). O Mongo é efêmero e nasce vazio a cada `up`. `mise run seed` e `mise run reset` chamam `POST /local/*` e só funcionam com a API no ar.

## Conventions that differ from defaults

- Código, identificadores e chaves em inglês. Idioma (1.23): o que chega ao Aluno é em inglês (assunto e corpo dos e-mails, textos do Conteúdo Estruturado); o Painel da equipe é em pt-BR; o `detail` do problem+json, comentários e a documentação OpenAPI continuam em pt-BR (o App mostra texto próprio pelo `code`, AD-11). O texto jurídico (política e termos) vale em pt-BR.
- Coleções Mongo em snake_case no plural, sem prefixo. Erros em RFC 9457 com `code` em SCREAMING_SNAKE.
- Sucesso sai em `ApiResponse.of(data)` (`{data, message?}`). Erro de domínio é `throw new ApiException(status, "CODE", "detalhe em pt-BR")`; o `ProblemHandler` da `platform` faz o resto. Nunca devolva mensagem de exceção ao cliente.
- Toda rota declara `@PublicRoute` ou `@RequiresRole(Role.X)` no método ou na classe (o método vence). Sem declaração a rota é negada e o `RouteDeclarationScanTest` falha o build. A hierarquia `ADMIN > CONTENT_CREATOR` mora só no `RoleHierarchyConfig`; `STUDENT` fica fora.
- Os módulos usam a `platform` pelas named interfaces `web`, `security`, `meta` e `local`, e declaram `"platform :: *"` em `allowedDependencies`. `platform.config` e `platform.observability` são internos. `content` expõe as named interfaces `activity` (famílias da AD-6) e `render` (renderer da AD-8), além do pacote base para a `application`; `learning` declara `"content"`, `"content :: activity"` e `"content :: render"` (AD-3).
- Sem `SPRING_PROFILES_ACTIVE` o perfil é `prod`, que exige `SPRING_DATA_MONGODB_URI`. Variáveis opcionais: `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `FLUVENY_MIN_APP_VERSION`. Teste `@SpringBootTest` sem Mongo passa uma URI local falsa (veja `PlatformWebTest`); o cliente não conecta na partida.
- O contrato é o `openapi/openapi.json` (AD-11), e o App gera os tipos dele. Mudou rota ou DTO, gere de novo com `./mvnw -B -ntp test -Dtest=OpenApiContractTest -Dopenapi.write=true` e commite junto; nunca edite o arquivo nem resolva conflito nele à mão. No `/api/v1`, só mudança aditiva: o job `Contrato` (`oasdiff breaking` contra a `main`) falha em remoção de rota ou de campo obrigatório da resposta, troca de tipo, campo obrigatório novo na requisição e valor novo de `enum` na resposta. Variante nova no `oneOf` da resposta (Tipo de Atividade novo) só gera aviso, pelo `openapi/oasdiff-levels.txt`: o App mostra o cartão PENDING para Tipo desconhecido (AD-11). Remover campo opcional da resposta passa (info no oasdiff 1.38), então o App trata opcional como opcional.
- Conteúdo Estruturado em `content/units/<unitKey>/` (`unit.json` + `stages/<stageKey>.json`), mídia em `content/media.json`, formato no JSON Schema de `content/schema/` (fonte única, AD-4/AD-6; o prefixo dos `$id`, `https://fluveny.github.io/content-schema/v1/`, é `[ASSUMPTION]`). Chaves em kebab-case, nunca reaproveitadas. Texto é string ou `{he, she, they}`; só `**negrito**`, `[[texto|expressionKey]]` e os placeholders de `x-fluveny-placeholder`. O `ContentUnitsTest` valida `content/` em todo `./mvnw test` e lista arquivo, ponteiro, chave e regra de cada erro.
- Campo que o aluno não vê é `"x-fluveny-secret": true` no schema; nunca escreva lista de campos secretos no código. As famílias `Activity`, `ActivityStudentView` e `Answer` ficam em `content.activity` (named interface `activity`), um record por Tipo; o `ActivityFidelityTest` trava schema, records e fixtures. Tipo novo: siga "Tipo de Atividade novo" no README.
- O `ContentText` sai no OpenAPI pelo `ContentTextModelConverter` (por causa do `@JsonValue`); no teste, use `new ModelConverters(true)` com esse conversor.
- Entidade nova estende o carregador local do seu módulo: um bean `LocalDataSeeder` (`platform :: local`) com `@Profile("local")`, idempotente (grava por chave, com upsert), com os dados mínimos para testar a própria tela (AD-18). Senha de usuário de seed só pela `LocalSeedPassword` (`LOCAL_SEED_PASSWORD` no `.env.local`); nunca crie seed fora do perfil `local`.
- O perfil `local` recusa subir junto de `prod` e com Mongo que não seja `localhost`, `127.0.0.1`, `::1` ou `mongo` (`LocalProfileGuard`). Rotas do perfil `local` ficam em `/local/*`, fora de `/api/v1` e do contrato.
- Logs em JSON (ECS), exceto no perfil `local`, que loga texto. Não logue corpo de requisição, cabeçalhos de autenticação nem PII.

## Known pitfalls

- O `mongo:8` não sobe em kernels Linux 6.19 a 7.0.13 (SERVER-121912), como o do Docker Desktop recente: os ITs e o `mise run up` falham localmente com "Container exited with code 1". Na CI (runner Ubuntu) eles rodam. Não troque a imagem do `LocalMongoContainer`; atualize o Docker Desktop ou rode os ITs só na CI.
- O `MongoDBContainer` do Testcontainers 2.0 não sobe replica set sem `.withReplicaSet()`, e aí as transações falham. Para Mongo em teste, importe o `LocalMongoContainer` em vez de criar outro container.

<!-- /bmad:context -->

## Publicação de conteúdo (história 1.7)

- A publicação é `POST /api/v1/admin/content/units/{unitKey}/versions` com o pacote `{unit, stages[], media{}}`, pelo `PublishUnitVersion`. Só a API calcula o hash (`CanonicalJson`, RFC 8785); nunca calcule no job nem no App. `UnitVersion` é imutável: nada apaga nem reescreve `unit_versions`.
- As rotas admin de `content` declaram `@RequiresRole(Role.DEPLOY)`. O token de deploy fica no GitHub Environment (`FLUVENY_DEPLOY_TOKEN`); a API guarda só o sha256 (`FLUVENY_DEPLOY_TOKEN_SHA256`). Teste com token gerado na execução (`DeployTokenTest.sha256Hex`), nunca fixo no código.
- Rota do aluno devolve só `*StudentView`, montada pelo `StudentViewProjector` (o único projetor, guiado pelo `x-fluveny-secret` do schema). O `ContentPublicationIT` e o `StudentRoutesOpenApiTest` falham se um segredo vazar.
- Os schemas entram no classpath em `content/schema/` (o `pom.xml` copia); em código de aplicação use `ContentSchemas.loadClasspath()` ou o bean `ContentSchemas`, não a pasta do repositório.
- `@SpringBootTest` sem Mongo passa também `fluveny.mongo.indexes-on-startup=false`, senão os índices da partida tentam conectar.
- O workflow `publish-content.yml` e o job `Publicação (API efêmera)` da CI usam os mesmos comandos do `scripts/content-bundle.mjs`; mudou um, mude o outro.
