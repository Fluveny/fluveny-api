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

🔗 Para acessar a documentação da aplicação: 
```bash
mvn javadoc:javadoc

```
Após isso, basta acessar: target/site/index.html

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

