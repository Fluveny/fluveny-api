<!-- bmad:context -->
<!-- Verified 2026-11-14 against a55eb65. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## fluveny-api

API do Fluveny: Java, Spring Boot e MongoDB, e o alvo é um monólito modular com Spring Modulith. As decisões ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)), clonado como pasta pai deste: `../_bmad-output/planning-artifacts/`. A spine (`architecture/architecture-Fluveny-2026-11-13/ARCHITECTURE-SPINE.md`) vence qualquer código deste repositório.

## Policy

- Nunca dê push na `main`. Trabalhe numa branch curta, abra PR e faça squash na `main`. O merge exige CI verde e 1 aprovação humana (AD-2). Nada no GitHub obriga isso ainda, então a regra vale pela palavra.
- Não estenda, não conserte nem porte o domínio legado: Módulo, GrammarRule, exercícios, JWT, `RunOnStartup`, login por username e coleções `fl_*` (AD-17). Trabalho novo vai nos pacotes de módulo da AD-3.
- Até a história 1.1 mesclar a `sprint9`, só a 1.1 parte da `main`, porque a `main` está 27 commits atrás da `sprint9`. Qualquer outra história espera a 1.1.
- Nunca grave segredo, senha ou token em código, `.properties` ou `docker-compose`. Não copie as credenciais de `src/test/resources/application-test.properties`; passe a URI por `SPRING_DATA_MONGODB_URI`.
- Implemente histórias com `bmad-build <número>` e revise com `bmad-code-review` num contexto novo. Preencha a linha `- História:` do template de PR: o workflow `story-status.yml` usa essa linha para avançar a história no `sprint-status.yaml`, e o card do Project acompanha. Não mova cards nem commite o `sprint-status.yaml` à mão.

## Running and verifying

- Os testes precisam de um Mongo em `localhost:27017`. Suba com `docker compose -f docker-compose-dev.yml up -d`. Os comandos do README estão errados: citam `docker-compose.dev.yml`, `docker-compose.test.yml`, `run.sh` e `run.ps1`, e nenhum desses arquivos existe.
- O build usa a versão de Java declarada em `java.version` no `pom.xml`. Não use a versão instalada na máquina.
- A CI (`.github/workflows/ci.yml`) roda `./mvnw -B -ntp verify` contra um Mongo sem senha, via `SPRING_DATA_MONGODB_URI`. Para reproduzir uma falha da CI, use a mesma variável.

## Conventions that differ from defaults

- Código, identificadores e chaves em inglês. Mensagens ao usuário em pt-BR.
- Coleções Mongo em snake_case no plural, sem prefixo. Erros em RFC 9457 com `code` em SCREAMING_SNAKE.

## Known pitfalls

- A CI fica vermelha na `main` até a 1.1: são 26 falhas e 3 erros em testes do domínio legado. Não conserte esses testes. A 1.1 apaga esse código.

<!-- /bmad:context -->
