# Fluveny | Back-End

Repositório destinado ao desenvolvimento do back-end da plataforma **Fluveny**.  

- ![Java](https://img.shields.io/badge/Java-21-blue?logo=java)
- ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)
- ![MongoDB](https://img.shields.io/badge/MongoDB-6.x-green?logo=mongodb) 
- ![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker) 
- ![Swagger](https://img.shields.io/badge/Swagger-UI-yellow?logo=swagger) 
- ![JUnit](https://img.shields.io/badge/JUnit-5-red?logo=java) 
  
Caso queira executar a aplicação em sua máquina, você poderá escolher alguma das opções:

- [Ambiente de Execução](#ambiente-de-execução) 
- [Ambiente de Desenvolvimento](#ambiente-de-desenvolvimento)
- [Ambiente de Testes](#ambiente-de-testes)

A aplicação roda na porta 8080. 

🔗 Acesse a documentação da API via Swagger:  
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)


## Ambiente de Execução
O Ambiente de Execução destina-se a quem deseja rodar a aplicação em sua máquina sem realizar modificações ou desenvolvimento. Para facilitar esse processo, dockerizamos tanto a API quanto o banco de dados MongoDB, garantindo que a criação e execução sejam mais simples, rápidas e controladas.

### Dependências
Para rodar, certifique-se de ter, em sua máquina:
- **JDK 21** ou superior
- **Maven** 
- **Docker-Compose**

### Execução
Para executar, basta utilizar um dos comandos, na pasta raiz, de acordo com seu sistema operacional: 

- Linux

Execute o seguinte comando no terminal, na raiz do projeto:

```bash
./run.sh

```

- Windows

Execute o seguinte comando no Poweshell, na raiz do projeto:
```
./run.ps1
```

Ambos os comandos irão buildar o jar com maven, subir os conteineres e iniciar a aplicação.

## Ambiente de Desenvolvimento
O Ambiente de Desenvolvimento destina-se a quem deseja rodar a aplicação para poder realizar modificações ou desenvolvimento. Para facilitar esse processo, optamos apenas por dockerizar o banco de dados (para que não seja necessário o criar com todas as credencias em sua máquina). 

### Dependências
Para rodar, certifique-se de ter, em sua máquina:
- **JDK 21** ou superior
- **IntelliJ IDEA**
- **Docker-Compose**

### Execução
Para executar, é necessário que você suba o conteinere do docker-compose.dev, com o seguinte comando:
```
docker-compose -f docker-compose.dev.yml up -d
```
Após isso, basta mudar sua variável de perfil do intelliJ para **dev** e rodar a aplicação normalmente.

## Ambiente de Testes

O Ambiente de Testes destina-se a quem deseja rodar a aplicação para poder realizar os Testes e os Modificar. Para facilitar esse processo, optamos apenas por dockerizar o banco de dados (para que não seja necessário o criar com todas as credencias em sua máquina). 

### Dependências
Para rodar, certifique-se de ter, em sua máquina:
- **JDK 21** ou superior
- **IntelliJ IDEA**
- **Docker-Compose**

### Execução
Para executar, é necessário que você suba o conteinere do docker-compose.dev, com o seguinte comando:
```
docker-compose -f docker-compose.test.yml up -d
```
Após isso, basta mudar sua variável de perfil do intelliJ para **test** e rodar a aplicação normalmente.

## Documentação do projeto

### Entidades principais

<table>
  <thead>
    <tr>
      <th>Nome</th>
      <th>Descrição</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Module</td>
      <td>Macroentidade da aplicação, que agrupa todas as outras. Responsável por ditar qual é o tema de cada aula.</td>
    </tr>
    <tr>
      <td>Introduction</td>
      <td>Seção inicial de um módulo, onde terá uma explicação breve do que o mesmo se trata.</td>
    </tr>
    <tr>
      <td>Grammar Rule</td>
      <td>Entidade das regras gramaticais, que ditará quais destas serão abordadas dentro de um módulo</td>
    </tr>
    <tr>
      <td>Grammar Rule Exercise</td>
      <td>Entidade de um exercicio de uma regra gramatical, parte prática do módulo.</td>
    </tr>
    <tr>
      <td>Grammar Rule Presentation</td>
      <td>Entidade de um apresentação de uma regra gramatical, parte explicativa do módulo.</td>
    </tr>
    <tr>
      <td>Grammar Rule Presentation</td>
      <td>Entidade de um apresentação de uma regra gramatical, parte explicativa do módulo.</td>
    </tr>
    <tr>
      <td>Level</td>
      <td>Entidade que compõe parte da gamificação da aplicação, que terá informações como "experiência ganha" por terminar determinados níveis do curso.</td>
    </tr>
  </tbody>
</table>



### Main

Afim de facilitar a leitura desta documentação, considere o caminho **src/main/java/com/fluveny/fluveny_backend/** como o diretório base para essa parte.


#### Api

Seção do código onde se encontram os controllers (gerenciadores de endpoints), DTOs de Response e Request e seus respectivos mappers.
```
.
api
├── ApiResponseFormat.java
├── controller
│   ├── GrammarRuleController.java
│   ├── GrammarRuleExerciseController.java
│   ├── GrammarRulePresentationController.java
│   ├── IntroductionController.java
│   ├── LevelController.java
│   └── ModuleController.java
├── dto
│   ├── ContentResponseDTO.java
│   ├── ExerciseRequestDTO.java
│   ├── ExerciseResponseDTO.java
│   ├── GrammarRuleModuleRequestDTO.java
│   ├── GrammarRuleModuleResponseDTO.java
│   ├── GrammarRuleModuleTinyDTO.java
│   ├── GrammarRuleRequestDTO.java
│   ├── GrammarRuleResponseDTO.java
│   ├── IntroductionRequestDTO.java
│   ├── IntroductionResponseDTO.java
│   ├── ModuleRequestDTO.java
│   ├── ModuleResponseDTO.java
│   ├── PresentationRequestDTO.java
│   ├── PresentationResponseDTO.java
│   ├── TextBlockRequestDTO.java
│   └── TextBlockResponseDTO.java
├── mapper
│   ├── ContentMapper.java
│   ├── ExerciseMapper.java
│   ├── GrammarRuleMapper.java
│   ├── GrammarRuleModuleMapper.java
│   ├── IntroductionMapper.java
│   ├── ModuleMapper.java
│   └── PresentationMapper.java
└── response
    ├── exercise
    │   └── ExerciseResponse.java
    ├── grammarrule
    │   ├── GrammarRuleResponse.java
    │   └── GrammarRulesResponse.java
    ├── level
    │   └── LevelsResponse.java
    ├── module
    │   ├── ContentsResponse.java
    │   ├── GrammarRuleModulesResponse.java
    │   ├── GrammarRuleModulesTinyResponse.java
    │   ├── IntroductionResponse.java
    │   ├── ModuleResponse.java
    │   └── ModulesReponse.java
    └── presentation
        └── PresentationResponse.java

```

#### Business

Seção do código responsável por aplicar as regras de negócio do sistema. Nela, estão todos os services utilizados na aplicação.

```
business
└── service
├── ContentManagerService.java
├── ExerciseService.java
├── GrammarRuleModuleService.java
├── GrammarRuleService.java
├── IntroductionService.java
├── LevelService.java
├── ModuleService.java
├── PresentationService.java
└── SaveContentManager.java
```

#### Infrastructure

Seção do código responsável por mapear as entidades da aplicação no banco não-relacional MongoDB. Nela, estão descritas as entidades e os repositórios de cada uma delas.
```
infraestructure/
├── entity
│   ├── ContentEntity.java
│   ├── ExerciseEntity.java
│   ├── GrammarRuleEntity.java
│   ├── GrammarRuleModuleEntity.java
│   ├── LevelEntity.java
│   ├── ModuleEntity.java
│   ├── PresentationEntity.java
│   ├── ResolvedContent.java
│   └── TextBlockEntity.java
├── enums
│   └── ContentType.java
└── repository
    ├── ExerciseRepository.java
    ├── GrammarRuleModuleRepository.java
    ├── GrammarRuleRepository.java
    ├── LevelRepository.java
    ├── ModuleRepository.java
    ├── PresentationRepository.java
    └── TextBlockRepository.java
```

### Test

De forma análoga ao diretório main da aplicação, considere o caminho base para a parte destes o caminho **src/test/java/com/fluveny/fluveny_backend**

Os arquivos descritos abaixo possuem testes de unidade para cada endpoint, regra de negócio e queries do sistema.

```
.
├── api
│   └── controller
│       ├── GrammarRuleExerciseControllerTest.java
│       ├── IntroductionControllerTest.java
│       └── ModuleControllerTest.java
├── business
│   └── service
│       ├── ExerciseServiceTest.java
│       ├── GrammarRuleModuleServiceTest.java
│       └── ModuleServiceTest.java
├── FluvenyBackendApplicationTests.java
└── infraestructure
    └── repository
        ├── GrammarRuleModuleRepositoryTest.java
        ├── GrammarRuleRepositoryTest.java
        └── ModuleRepositoryTest.java

```
