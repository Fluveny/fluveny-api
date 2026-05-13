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
- [Executando via IDE](#executando-via-ide)

A aplicação roda na porta 8080.

🔗 Acesse a documentação da API via Swagger:  
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## Ambiente de Execução

Este ambiente é destinado a desenvolvedores que irão realizar modificações ou desenvolvimento na aplicação. Utilizaremos o Docker apenas para rodar o banco de dados e executaremos a API localmente.

### 1. Dependências Necessárias

- **JDK 21** ou superior
- **Docker** e **Docker-Compose**
- Arquivo `.env` na raiz do projeto contendo as variáveis de ambiente necessárias.

### 2. Subindo o Banco de Dados

Para iniciar o MongoDB em um contêiner Docker:

```bash
docker-compose -f docker-compose-dev.yml up -d
```

_Isso deixará o banco rodando em background._

### 3. Executando a API via Script

Para rodar a API rapidamente, sem precisar configurar a IDE, você pode utilizar o nosso script facilitador:

```bash
./start-dev.sh
```

O script fará o seguinte:

- Carregará as variáveis de ambiente do `.env`.
- Definirá o profile da aplicação para `dev`.
- Matará eventuais processos antigos que estejam travando a porta 8080.
- Iniciará a API em background utilizando o Maven Wrapper.

Para acompanhar os logs em tempo real, execute:

```bash
tail -f api.log
```

### Executando via IDE

Se preferir rodar direto da sua IDE (como o IntelliJ IDEA):

1. Certifique-se de que o banco de dados já foi iniciado via `docker-compose-dev.yml`.
2. Configure a variável de ambiente do profile na sua IDE de preferência: `SPRING_PROFILES_ACTIVE=dev` (nas configurações de Run/Debug).
3. Execute a classe principal `FluvenyBackendApplication`.
