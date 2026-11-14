# Fluveny | API

API do Fluveny: monólito modular em Java 25, Spring Boot 4.1 e Spring Modulith 2.1, sobre MongoDB. As decisões de arquitetura ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)).

## Requisitos

- **JDK 25** (por exemplo, `brew install --cask temurin@25`). O Maven vem pelo wrapper (`./mvnw`).
- **Docker** ligado, para os testes de integração (Testcontainers) e para gerar a imagem.

Sem JDK 25 na máquina, dá para rodar o build num container:

```bash
docker run --rm -v "$PWD:/w" -w /w \
  -v /var/run/docker.sock:/var/run/docker.sock \
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
- Integração (`*IT`, failsafe): o `ApplicationIT` sobe o contexto contra um `mongo:8` do Testcontainers, sem precisar de nenhuma variável de ambiente.

## Variáveis de ambiente

Toda configuração vem do ambiente. Nenhum segredo fica em arquivo.

| Variável | Uso |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB, com credenciais. Obrigatória para rodar a API fora dos testes. |

## Imagem

```bash
docker build -t fluveny-api:local .
docker run --rm -p 8080:8080 -e SPRING_DATA_MONGODB_URI=... fluveny-api:local
```

A API escuta na porta 8080 e roda com um usuário sem root. A cada merge na `main`, a CI publica `ghcr.io/fluveny/fluveny-api:main` e `ghcr.io/fluveny/fluveny-api:sha-<sha>`.

## Ambiente local

O ambiente local (Mongo em replica set, perfil `local` e dados de desenvolvimento) chega com a história 1.4.
