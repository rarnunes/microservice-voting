# Voting API

Backend REST para cadastro de pautas, abertura de sessões e votação. Recebe e retorna JSON; não inclui frontend, aplicativo mobile ou integração de CPF.

## Tecnologias

- Java 21
- Spring Boot 3.5 / Spring Web
- Spring Data MongoDB
- MongoDB Atlas
- Bean Validation
- Maven Wrapper
- springdoc-openapi / Swagger
- JUnit 5, Mockito, MockMvc e Spring Boot Test
- Flapdoodle Embedded MongoDB somente nos testes

## Como executar

Instale um JDK 21 e configure `JAVA_HOME`. A URI do Atlas deve ser fornecida externamente. Na raiz do projeto, em Linux/macOS:

```bash
export MONGODB_URI="<mongodb-atlas-connection-string>"
./mvnw spring-boot:run
```

Windows (cmd):

```bat
set MONGODB_URI=<mongodb-atlas-connection-string>
mvnw.cmd spring-boot:run
```

PowerShell:

```powershell
$env:MONGODB_URI="<mongodb-atlas-connection-string>"
.\mvnw.cmd spring-boot:run
```

Os valores entre `<...>` são placeholders. Obtenha a URI no Atlas e configure o acesso de rede do ambiente que executará a aplicação, além de um usuário com acesso ao banco `votingdb` e permissão para criar seus índices. Não publique a URI nem a salve em arquivos versionados.

A porta padrão é 8080; configure `PORT` para usar outra porta. O Wrapper baixa Maven e dependências na primeira execução. A aplicação exige `MONGODB_URI` para iniciar; não existe valor padrão para essa variável.

Para gerar e executar o JAR:

```bash
./mvnw clean package
java -jar target/microservice-voting-0.0.1-SNAPSHOT.jar
```

## Database

**MongoDB Atlas.** Os dados precisam sobreviver aos restarts da aplicação, e o serviço será executado em ambiente de nuvem. O armazenamento fica no MongoDB, separado do processo da API.

A configuração está em `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: voting-api
  data:
    mongodb:
      uri: ${MONGODB_URI}
      database: votingdb
      auto-index-creation: true

server:
  port: ${PORT:8080}
```

Nunca versione credenciais. `.env`, suas variantes e `application-local.yml`/`.yaml`/`.properties` estão no `.gitignore` e no `.dockerignore`. A aplicação não carrega `.env` automaticamente: exporte a variável no shell ou configure-a no ambiente de execução.

As coleções são `agendas`, `voting_sessions` e `votes`. IDs são UUIDs gerados pela aplicação e armazenados como texto. Referências entre documentos usam `agendaId`; não há DBRef. O serviço verifica a existência da pauta e da sessão antes de aceitar um voto.

### Índices

| Coleção | Nome | Chaves | UNIQUE |
| --- | --- | --- | --- |
| votes | `uk_vote_agenda_associate` | `{ agendaId: 1, associateId: 1 }` | Sim |
| votes | `idx_vote_agenda_choice` | `{ agendaId: 1, choice: 1 }` | Não |
| voting_sessions | `uk_session_agenda` | `{ agendaId: 1 }` | Sim |

MongoDB também cria o índice único de `_id` de cada coleção. Os índices acima são declarados nos documentos e criados na inicialização com `auto-index-creation: true`. A aplicação não deve iniciar ignorando uma falha de criação de índice. Se já houver dados duplicados em uma coleção existente, eles precisarão ser corrigidos antes que o índice UNIQUE possa ser criado.

O índice composto de votos garante um voto por associado/pauta, mesmo com requisições concorrentes. Consultar se o voto existe antes de inserir não seria suficiente. O adapter usa `insert` e converte `DuplicateKeyException` em `DuplicateVoteException`, retornando 409. O índice de sessão oferece a mesma garantia para abertura simultânea de sessões, convertida em `VotingSessionAlreadyExistsException`.

Os índices de votos começam por `agendaId`, atendendo também a consultas somente por pauta. O índice `(agendaId, choice)` atende à contagem por pauta e escolha, sem criar outro índice redundante somente em `agendaId`.

Cada caso de escrita insere um único documento. Não há necessidade de transações envolvendo múltiplos documentos ou configuração de transaction manager.

## Como executar testes

```bash
./mvnw test
```

Os testes não usam o Atlas e não precisam de `MONGODB_URI`. Mesmo que a variável exista no ambiente, os testes de integração substituem a URI e o nome do banco pela configuração da instância temporária local.

- **Unitários:** Mockito nos ports e `Clock` fixo para testar criação de pauta, duração explícita/padrão, votos YES/NO, ausência e encerramento da sessão, duplicidade e resultados YES/NO/TIE.
- **Integração:** aplicação completa e MockMvc conectados a um processo MongoDB real, temporário, iniciado e encerrado pelos testes. Verificam endpoints, validações, índices UNIQUE de votos e sessões, tratamento de duplicidade, contagem e concorrência.
- **Reinício:** fecha e reabre o contexto da aplicação mantendo o mesmo MongoDB local, verificando que pautas, sessões e votos continuam disponíveis.

Docker não estava disponível no ambiente desta implementação. Por isso, foi usado Flapdoodle exclusivamente em `test`, sem alterações na aplicação de produção. Ele baixa o executável MongoDB 7.0 compatível com o sistema, inicia um processo local com porta e diretório temporários e encerra o processo ao terminar. Não é uma simulação do banco e não depende de um MongoDB instalado manualmente.

A primeira execução precisa de acesso à internet para baixar dependências e o binário MongoDB. Os executáveis baixados ficam no cache `~/.embedmongo`; os dados de teste são temporários. É necessário que o sistema operacional seja suportado pelo binário. Em um ambiente com Docker, essa inicialização pode ser substituída por Testcontainers, mantendo os mesmos testes de persistência.

## Docker e cloud

O Dockerfile usa build multi-stage com Maven/JDK 21 e runtime Java 21, executado com usuário sem privilégios de root. A imagem contém o JAR da aplicação; nenhuma credencial é copiada ou definida no build.

```bash
docker build -t voting-api .
docker run --rm -p 8080:8080 -e MONGODB_URI voting-api
```

O segundo comando repassa a variável já exportada no terminal, sem escrevê-la no Dockerfile. Para mudar a porta:

```bash
docker run --rm -p 9090:9090 -e MONGODB_URI -e PORT=9090 voting-api
```

No provedor cloud, configure `MONGODB_URI` como segredo/variável do serviço e `PORT` conforme necessário. O Atlas deve permitir o acesso de rede desse ambiente. Não há estado de sessão HTTP ou arquivos de banco no container, e diferentes instâncias da API podem usar o mesmo banco.

O build da imagem empacota somente a aplicação e não executa os testes. Rode `./mvnw test` antes de construir/publicar a imagem. Não há Kubernetes, filas, cache distribuído ou provisionamento de infraestrutura nesta entrega.

## Swagger

- Interface: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Os controllers têm uma descrição por operação. As respostas de erro são documentadas em `OpenApiConfiguration` para evitar repetir annotations.

## Endpoints e regras

| Método | URL | Sucesso |
| --- | --- | --- |
| POST | `/api/v1/agendas` | 201: pauta criada, com header `Location` |
| GET | `/api/v1/agendas/{agendaId}` | 200: pauta |
| GET | `/api/v1/agendas` | 200: lista, sem paginação |
| POST | `/api/v1/agendas/{agendaId}/sessions` | 201: sessão criada |
| POST | `/api/v1/agendas/{agendaId}/votes` | 201: voto registrado |
| GET | `/api/v1/agendas/{agendaId}/result` | 200: contagem atual e status da sessão |

- `title` é obrigatório, não pode conter apenas espaços e aceita até 255 caracteres. `description` é opcional.
- A sessão dura 1 minuto quando o body, `durationMinutes` ou seu valor não são informados. Uma duração informada deve ser um inteiro positivo.
- Cada pauta aceita uma única sessão, mesmo após o encerramento.
- As datas usam `Instant`, serializado em UTC. A sessão está aberta quando `now >= openedAt && now < closesAt`. Exatamente em `closesAt`, já está fechada.
- A validade do voto é verificada no serviço antes da inserção. A escrita pode terminar depois dessa verificação.
- A API e o domínio usam somente `YES` e `NO`. Outros textos e valores numéricos são rejeitados.
- `associateId` é obrigatório, não pode conter apenas espaços e aceita até 100 caracteres. É um identificador opaco, comparado exatamente como recebido; não é normalizado nem validado como CPF.
- Um associado pode votar em pautas diferentes, mas apenas uma vez em cada pauta.
- O resultado pode ser consultado durante ou depois da sessão. Sem sessão, retorna 404; com sessão e sem votos, retorna `TIE`, total zero. O status calculado será `OPEN` ou `CLOSED`.
- Requests inválidos retornam 400; pauta/sessão inexistente, 404; voto repetido, segunda sessão ou sessão encerrada, 409.

Exemplo de erro:

```json
{
  "timestamp": "2026-09-24T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Associate has already voted on this agenda",
  "path": "/api/v1/agendas/6f250b73-ffcb-4d12-bab6-0903ff343f58/votes"
}
```

## Exemplos curl

Criar pauta:

```bash
curl -i -X POST http://localhost:8080/api/v1/agendas \
  -H 'Content-Type: application/json' \
  -d '{"title":"Aprovação das contas de 2026","description":"Votação referente à aprovação das contas."}'
```

Copie o `id` retornado:

```bash
AGENDA_ID='substitua-pelo-uuid-retornado'
```

Abrir sessão:

```bash
curl -i -X POST "http://localhost:8080/api/v1/agendas/$AGENDA_ID/sessions" \
  -H 'Content-Type: application/json' \
  -d '{"durationMinutes":5}'
```

Use `{}` ou omita o body para a duração padrão.

Votar:

```bash
curl -i -X POST "http://localhost:8080/api/v1/agendas/$AGENDA_ID/votes" \
  -H 'Content-Type: application/json' \
  -d '{"associateId":"associate-123","choice":"YES"}'
```

Consultar resultado:

```bash
curl "http://localhost:8080/api/v1/agendas/$AGENDA_ID/result"
```

```json
{
  "agendaId": "6f250b73-ffcb-4d12-bab6-0903ff343f58",
  "yesVotes": 1,
  "noVotes": 0,
  "totalVotes": 1,
  "result": "YES",
  "sessionStatus": "OPEN"
}
```

## Decisões técnicas e performance

1. **Arquitetura hexagonal simples.** O fluxo é `Controller → VotingUseCase → VotingService → output ports → persistence adapters → MongoRepository`. O serviço reúne os casos de uso relacionados e não importa MongoRepository, MongoTemplate ou documentos. Domínio, documentos e requests/responses são separados; as conversões são explícitas.
2. **IDs.** UUIDs são gerados no serviço. Os adapters convertem UUID para String na persistência e fazem a conversão inversa na leitura, preservando o contrato REST.
3. **Versionamento `/api/v1`.** A versão fica visível na URL e permite introduzir um contrato futuro sem alterar imediatamente os clientes atuais.
4. **Encerramento calculado.** Não persistimos um status que precisaria ser atualizado. O serviço compara o relógio com `openedAt` e `closesAt`. `Clock` permite testes determinísticos. Não há scheduler nem atualização do documento quando o prazo termina.
5. **COUNT no banco.** Os votos não são carregados em memória para contabilização. O adapter chama `countByAgendaIdAndChoice` para YES e NO, e o total é a soma. São duas consultas, sem terceira consulta para o total. Durante a sessão, novas inserções podem ocorrer entre as contagens; a resposta representa uma contagem parcial, sem promessa de snapshot transacional. Após o fim das escritas, a contagem estabiliza.
6. **Índices e concorrência.** `agendaId` é indexado, a contagem usa índice por pauta/escolha e a duplicidade é garantida por índices UNIQUE no banco. Não há locks de aplicação ou verificação de duplicidade como única proteção.
7. **Cloud.** A API permanece stateless, com configuração por ambiente e armazenamento externo. Isso facilita executar múltiplas instâncias. A capacidade efetiva depende de carga, consultas e recursos do cluster; deve ser medida antes de adicionar cache ou outros mecanismos.
8. **Simplicidade.** Sem Lombok, builders, factories, classes base genéricas ou abstrações sem uso. As operações individuais não usam `@Transactional`. SLF4J registra criação da pauta, abertura da sessão e registro do voto sem incluir associado, escolha ou conteúdo da pauta.

### Organização dos pacotes

```text
br.com.nutrieduc.clinica.microservicevoting
├── MicroserviceVotingApplication
├── adapter
│   ├── input
│   │   ├── controller
│   │   ├── request
│   │   ├── response
│   │   └── handler
│   └── output.persistence
│       ├── document
│       ├── repository
│       └── adapter
├── core
│   ├── exception
│   ├── port
│   │   ├── input
│   │   └── output
│   └── services
├── domain
│   ├── dto
│   ├── enums
│   └── model
└── config
```

## Possíveis evoluções em produção

Autenticação e autorização, identidade confiável do associado, métricas, política de backup do cluster, sincronização de relógios entre instâncias e paginação de pautas podem ser adicionados conforme os requisitos. A criação e evolução de índices pode passar para um procedimento de implantação controlado quando o volume justificar.

O bônus de CPF está fora desta entrega. Se necessário, o serviço poderá receber um `AssociateEligibilityPort` com adapter HTTP, timeout e tratamento das respostas externas. Essa interface não foi criada antecipadamente.
