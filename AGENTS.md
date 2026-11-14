<!-- bmad:context -->
<!-- Verified 2026-11-14 against feat/1-2-platform (história 1.2). Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## fluveny-api

API do Fluveny: Java, Spring Boot e MongoDB, e o alvo é um monólito modular com Spring Modulith. As decisões ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)), clonado como pasta pai deste: `../_bmad-output/planning-artifacts/`. A spine (`architecture/architecture-Fluveny-2026-11-13/ARCHITECTURE-SPINE.md`) vence qualquer código deste repositório.

## Policy

- Nunca dê push na `main`. Trabalhe numa branch curta, abra PR e faça squash na `main`. O merge exige CI verde e 1 aprovação humana (AD-2). Nada no GitHub obriga isso ainda, então a regra vale pela palavra.
- Não estenda, não conserte nem porte o domínio legado: Módulo, GrammarRule, exercícios, JWT, `RunOnStartup`, login por username e coleções `fl_*` (AD-17).
- Trabalho novo vai num dos 9 pacotes de módulo (`account`, `auth`, `team`, `content`, `learning`, `metrics`, `survey`, `mail`, `platform`). As dependências permitidas ficam no `package-info.java` de cada módulo e seguem o grafo da AD-3; mudar o grafo é mudar a spine.
- Nunca grave segredo, senha ou token em código, `.properties` ou `docker-compose`. A URI do Mongo vem só de `SPRING_DATA_MONGODB_URI`, lida em `spring.mongodb.uri` (o Boot 4 ignora a `spring.data.mongodb.uri`); o `MongoUriBindingIT` prova o caminho. Achados antigos do gitleaks ficam na `.gitleaksignore`; não acrescente achados novos lá, remova o segredo.
- Implemente histórias com `bmad-build <número>` e revise com `bmad-code-review` num contexto novo. Depois do merge, a história muda de status em `../_bmad-output/implementation-artifacts/sprint-status.yaml` e no card do [Project](https://github.com/orgs/Fluveny/projects/12).

## Running and verifying

- Build e testes: `./mvnw -B -ntp verify`, com JDK 25 e Docker ligados. Roda os unitários (surefire), o `ModularityTest` (verificação do Spring Modulith) e os `*IT` (failsafe). Os ITs usam um `mongo:8` do Testcontainers via `@ServiceConnection`; não precisam de Mongo local nem de variável.
- Sem JDK 25 na máquina, rode o build num container `maven:3-eclipse-temurin-25` (comando no README).
- O build usa a versão de Java declarada em `java.version` no `pom.xml`. Não use a versão instalada na máquina.
- Imagem: `docker build -t fluveny-api:local .`. A CI publica no GHCR a cada push na `main`.
- O ambiente local (perfil `local`, replica set, `LocalDataLoader`) é da história 1.4.

## Conventions that differ from defaults

- Código, identificadores e chaves em inglês. Mensagens ao usuário em pt-BR.
- Coleções Mongo em snake_case no plural, sem prefixo. Erros em RFC 9457 com `code` em SCREAMING_SNAKE.
- Sucesso sai em `ApiResponse.of(data)` (`{data, message?}`). Erro de domínio é `throw new ApiException(status, "CODE", "detalhe em pt-BR")`; o `ProblemHandler` da `platform` faz o resto. Nunca devolva mensagem de exceção ao cliente.
- Toda rota declara `@PublicRoute` ou `@RequiresRole(Role.X)` no método ou na classe (o método vence). Sem declaração a rota é negada e o `RouteDeclarationScanTest` falha o build. A hierarquia `ADMIN > CONTENT_CREATOR` mora só no `RoleHierarchyConfig`; `STUDENT` fica fora.
- Os módulos usam a `platform` pelas named interfaces `web`, `security` e `meta`, e declaram `"platform :: *"` em `allowedDependencies`. `platform.config` e `platform.observability` são internos.
- Sem `SPRING_PROFILES_ACTIVE` o perfil é `prod`, que exige `SPRING_DATA_MONGODB_URI`. Variáveis opcionais: `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `FLUVENY_MIN_APP_VERSION`. Teste `@SpringBootTest` sem Mongo passa uma URI local falsa (veja `PlatformWebTest`); o cliente não conecta na partida.
- Logs em JSON (ECS). Não logue corpo de requisição, cabeçalhos de autenticação nem PII.

## Known pitfalls

- O `mongo:8` não sobe em kernels Linux 6.19 a 7.0.13 (SERVER-121912), como o do Docker Desktop recente: o `ApplicationIT` falha localmente com "Container exited with code 1". Na CI (runner Ubuntu) ele roda. Não troque a imagem do teste; atualize o Docker Desktop ou rode o IT só na CI.

<!-- /bmad:context -->
