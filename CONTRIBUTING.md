# Contribuindo com a API do Fluveny

As decisões de arquitetura ficam no repositório de planejamento ([Fluveny/fluveny-planejamento](https://github.com/Fluveny/fluveny-planejamento)), e a gestão no [GitHub Projects](https://github.com/orgs/Fluveny/projects/12). Cada história do `epics.md` tem um card lá.

## Primeira vez

Precisa de [mise](https://mise.jdx.dev) (`brew install mise`) e do Docker ligado. Nada de JDK, Maven ou Mongo instalados à mão.

```bash
git clone https://github.com/Fluveny/fluveny-api.git && cd fluveny-api
mise trust && mise install   # JDK 25 e lefthook
mise run setup               # hooks do git
mise run up                  # API em http://localhost:8080, perfil local
```

Abra http://localhost:8080/swagger-ui/index.html. Se o `mise run up` falhar com "Container exited with code 1", o kernel do Docker está na faixa 6.19 a 7.0.13, em que o `mongo:8` não sobe (SERVER-121912): confira com `docker info --format '{{.KernelVersion}}'` e atualize o Docker Desktop.

O `.env.local` é opcional. Quando algum carregador criar usuários, copie o `.env.local.example` para `.env.local` e preencha a `LOCAL_SEED_PASSWORD`. O resto do ambiente local está no [README](README.md#ambiente-local).

## Fluxo: trunk-based com squash

- A `main` é o tronco e está sempre pronta para deploy. Nunca dê push direto nela.
- Cada história (ou parte dela) vira uma branch curta a partir da `main`, um PR e um squash na `main`. Rebaseie na `main` em vez de fazer merge dela na branch.
- O merge exige a CI verde e 1 aprovação humana (AD-2).
- Depois do merge, apague a branch, mude o status da história no `sprint-status.yaml` do planejamento e mova o card no Project.

### Nome das branches

`<tipo>/<história>-<resumo-curto>`, em minúsculas e kebab-case, com a história no formato da chave do `sprint-status.yaml` (`1-4`, não `1.4`):

| Tipo | Quando | Exemplo |
| --- | --- | --- |
| `feat` | funcionalidade de uma história | `feat/1-4-ambiente-local` |
| `fix` | correção de bug | `fix/2-3-sessao-expirada` |
| `chore` | build, CI, dependências, ferramentas | `chore/1-1-base-nova` |
| `docs` | só documentação | `docs/readme-ambiente-local` |
| `refactor` | mudança interna sem mudar comportamento | `refactor/platform-problems` |

Trabalho fora de uma história fica sem o número.

### Commits e título do PR

- O título do PR vira a mensagem do squash: `<tipo>: <o que muda> (história X.Y)`, em português, por exemplo `feat: ambiente local em um comando (história 1.4)`.
- Dentro da branch, os commits são livres. Formatação em massa vai num commit só de formatação, separado do resto.
- O lefthook formata os arquivos Java staged a cada commit. A CI roda `mise run check`, que falha se algum arquivo estiver fora do formato (`mise run format` corrige).

### Template de PR

O [`.github/pull_request_template.md`](.github/pull_request_template.md) já vem no corpo de todo PR. Preencha:

- **História:** a chave do `sprint-status.yaml` e o link do card.
- **O que muda:** duas ou três frases.
- **Critérios de aceite:** copiados do `epics.md`, com o estado de cada um e o que ficou de fora.
- **Como testar:** os passos para quem revisa.
- **Checklist:** CI verde, `bmad-code-review` num contexto novo, nada contra as AD-1 a AD-19, nenhum segredo, README atualizado.

## Antes de abrir o PR

```bash
mise run check
```

É exatamente o que a CI roda (NFR13). Se mudou rota ou DTO, gere o contrato de novo (`./mvnw -B -ntp test -Dtest=OpenApiContractTest -Dopenapi.write=true`) e commite junto; nunca edite o `openapi/openapi.json` à mão.
