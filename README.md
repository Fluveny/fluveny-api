# Fluveny | API

API do Fluveny: monólito modular em Java 25, Spring Boot 4.1 e Spring Modulith 2.1, sobre MongoDB. As decisões de arquitetura ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)).

## Requisitos

- **JDK 25** (por exemplo, `brew install --cask temurin@25`). O Maven vem pelo wrapper (`./mvnw`).
- **Docker** ligado, para os testes de integração (Testcontainers) e para gerar a imagem.

Sem JDK 25 na máquina, dá para rodar o build num container:

```bash
docker run --rm -v "$PWD:/w" -w /w \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3-eclipse-temurin-25 ./mvnw -B -ntp verify
```

## Módulos

O código fica em `src/main/java/com/fluveny/fluveny_backend/`, um pacote por módulo: `account`, `auth`, `team`, `content`, `learning`, `metrics`, `survey`, `mail` e `platform`. As dependências permitidas entre eles ficam no `package-info.java` de cada um e seguem o grafo da AD-3. O `ModularityTest` falha o build em caso de ciclo ou acesso fora do grafo.

## Build e testes

```bash
./mvnw -B -ntp verify
```

- Unitários (`*Test`, surefire), incluindo o `ModularityTest`, que também gera a documentação dos módulos em `target/spring-modulith-docs`.
- Integração (`*IT`, failsafe), contra um `mongo:8` do Testcontainers, sem precisar de nenhuma variável de ambiente:
  - o `ApplicationIT` sobe o contexto via `@ServiceConnection`, no perfil `test`;
  - o `MongoUriBindingIT` passa a URI do container como `SPRING_DATA_MONGODB_URI` e confirma que a API conecta por ela, no perfil `prod` padrão;
  - o `ProdProfileIT` confirma que, sem perfil e sem `SPRING_DATA_MONGODB_URI`, a API não sobe e a falha cita a variável.

> **Docker Desktop recente:** o `mongo:8` não sobe em kernels Linux 6.19 a 7.0.13 (SERVER-121912), e os ITs falham com "Container exited with code 1". Na CI eles rodam normalmente. Para rodar só os unitários (incluindo o `ModularityTest`), use `./mvnw -B -ntp test`.

## Variáveis de ambiente

Toda configuração vem do ambiente. Nenhum segredo fica em arquivo.

| Variável | Uso |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB, com credenciais e o nome do banco. Obrigatória no perfil `prod`: sem ela a API não sobe, e o log cita a variável. É lida em `spring.mongodb.uri`, porque o Boot 4 ignora a `spring.data.mongodb.uri`. |
| `SPRING_PROFILES_ACTIVE` | Opcional. Sem ela, o perfil é `prod` (AD-18). |
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

## Ambiente local

O ambiente local (Mongo em replica set, perfil `local` e dados de desenvolvimento) chega com a história 1.4. Até lá, não há compose nem script de partida neste repositório.
