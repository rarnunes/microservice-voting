package br.com.nutrieduc.clinica.microservicevoting.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VoteDocument;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.VoteMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.core.port.input.VotingUseCase;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VotingSessionDocument;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.AgendaMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.VotingSessionMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotingSessionPort;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionAlreadyExistsException;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;
import br.com.nutrieduc.clinica.microservicevoting.support.LocalMongo;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(VotingApiIntegrationTest.TimeConfiguration.class)
class VotingApiIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final LocalMongo mongo = new LocalMongo();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private VotingUseCase voting;
    @Autowired private VoteMongoRepository voteRepository;
    @Autowired private AgendaMongoRepository agendaRepository;
    @Autowired private VotingSessionMongoRepository sessionRepository;
    @Autowired private VotingSessionPort sessionPort;

    // Configura a conexão com o MongoDB local utilizado pelos testes.
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::uri);
        registry.add("spring.data.mongodb.database", () -> "voting_api_test");
    }

    // Encerra o MongoDB local após a execução de todos os testes da classe.
    @AfterAll
    static void stopMongo() {
        mongo.close();
    }

    // Remove os dados persistidos para que cada teste comece com o banco vazio.
    @BeforeEach
    void cleanDatabase() {
        voteRepository.deleteAll();
        sessionRepository.deleteAll();
        agendaRepository.deleteAll();
    }

    // Verifica a criação de uma pauta com UUID, data e Location, além da consulta e listagem dos dados.
    @Test
    void createsGetsAndListsAgenda() throws Exception {
        String body = mvc.perform(post("/api/v1/agendas").contentType(APPLICATION_JSON)
                        .content("""
                        {"title":"Contas de 2026","description":"Descrição"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();
        assertThat(UUID.fromString(id)).isNotNull();
        mvc.perform(get("/api/v1/agendas/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Contas de 2026"))
                .andExpect(jsonPath("$.description").value("Descrição"));
        mvc.perform(get("/api/v1/agendas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
    }

    // Verifica se títulos ausentes, nulos ou em branco retornam HTTP 400 com o corpo de erro padronizado.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\"   \"}"})
    void rejectsMissingOrBlankTitle(String body) throws Exception {
        mvc.perform(post("/api/v1/agendas").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/agendas"));
    }

    // Verifica se consultar, abrir sessão, votar e obter resultado de uma pauta inexistente retorna HTTP 404.
    @Test
    void missingAgendasReturn404ForAllOperations() throws Exception {
        String path = "/api/v1/agendas/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(post(path + "/sessions")).andExpect(status().isNotFound());
        mvc.perform(post(path + "/votes").contentType(APPLICATION_JSON)
                .content("""
                        {"associateId":"a","choice":"YES"}
                        """))
                .andExpect(status().isNotFound());
        mvc.perform(get(path + "/result")).andExpect(status().isNotFound());
    }

    // Verifica a abertura de uma sessão de cinco minutos e a rejeição de uma segunda sessão com HTTP 409.
    @Test
    void opensSessionWithDuration() throws Exception {
        UUID id = agenda();
        mvc.perform(post(path(id, "sessions")).contentType(APPLICATION_JSON).content("""
                        {"durationMinutes":5}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.agendaId").value(id.toString()))
                .andExpect(jsonPath("$.openedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.closesAt").value(NOW.plusSeconds(300).toString()));
        mvc.perform(post(path(id, "sessions"))).andExpect(status().isConflict());
    }

    // Verifica se a duração padrão de um minuto é aplicada quando o corpo ou a duração está ausente ou nula.
    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"durationMinutes\":null}"})
    void defaultsMissingDuration(String body) throws Exception {
        mvc.perform(post(path(agenda(), "sessions")).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closesAt").value(NOW.plusSeconds(60).toString()));
    }

    // Verifica se durações iguais a zero, negativas, fracionárias ou acima do limite inteiro retornam HTTP 400.
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "2147483648"})
    void rejectsInvalidDuration(String duration) throws Exception {
        mvc.perform(post(path(agenda(), "sessions")).contentType(APPLICATION_JSON)
                .content("{\"durationMinutes\":" + duration + "}"))
                .andExpect(status().isBadRequest());
    }

    // Verifica o empate sem votos, a contagem por pauta e o resultado durante e após o encerramento da sessão.
    // Confirma também que a sessão encerrada rejeita novos votos e não pode ser reaberta.
    @Test
    void countsVotesDuringAndAfterSession() throws Exception {
        UUID id = agenda();
        voting.openSession(id, 1);
        mvc.perform(get(path(id, "result"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("TIE"))
                .andExpect(jsonPath("$.totalVotes").value(0));
        cast(id, "a", "YES", 201);
        cast(id, "b", "NO", 201);
        cast(id, "c", "YES", 201);
        UUID other = agenda();
        voting.openSession(other, 1);
        cast(other, "a", "NO", 201);
        mvc.perform(get(path(id, "result"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.yesVotes").value(2)).andExpect(jsonPath("$.noVotes").value(1))
                .andExpect(jsonPath("$.totalVotes").value(3)).andExpect(jsonPath("$.result").value("YES"))
                .andExpect(jsonPath("$.sessionStatus").value("OPEN"));
        closeSession(id);
        mvc.perform(get(path(id, "result"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVotes").value(3))
                .andExpect(jsonPath("$.sessionStatus").value("CLOSED"));
        cast(id, "d", "YES", 409);
        mvc.perform(post(path(id, "sessions"))).andExpect(status().isConflict());
    }

    // Verifica se votar ou consultar o resultado de uma pauta sem sessão retorna HTTP 404.
    @Test
    void missingSessionReturns404() throws Exception {
        UUID id = agenda();
        cast(id, "a", "YES", 404);
        mvc.perform(get(path(id, "result"))).andExpect(status().isNotFound());
    }

    // Verifica se um segundo voto do mesmo associado retorna HTTP 409 padronizado e mantém apenas o primeiro voto.
    @Test
    void duplicateVoteReturnsStandardConflict() throws Exception {
        UUID id = agenda();
        voting.openSession(id, 1);
        cast(id, "a", "YES", 201);
        mvc.perform(post(path(id, "votes")).contentType(APPLICATION_JSON)
                .content("""
                        {"associateId":"a","choice":"NO"}
                        """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Associate has already voted on this agenda"))
                .andExpect(jsonPath("$.path").value(path(id, "votes")));
        assertThat(voteRepository.count()).isEqualTo(1);
    }

    // Verifica se votos com campos ausentes ou inválidos e JSON malformado retornam HTTP 400 sem persistir votos.
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"associateId\":\" \",\"choice\":\"YES\"}",
            "{\"associateId\":\"a\"}", "{\"associateId\":\"a\",\"choice\":null}",
            "{\"associateId\":\"a\",\"choice\":\"MAYBE\"}",
            "{\"associateId\":\"a\",\"choice\":0}", "{invalid"})
    void rejectsInvalidVotePayload(String body) throws Exception {
        UUID id = agenda();
        voting.openSession(id, 1);
        mvc.perform(post(path(id, "votes")).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(voteRepository.count()).isZero();
    }

    // Verifica se a consulta de uma pauta com UUID inválido retorna HTTP 400 e identifica o caminho da requisição.
    @Test
    void rejectsInvalidUuid() throws Exception {
        mvc.perform(get("/api/v1/agendas/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/agendas/not-a-uuid"));
    }

    // Verifica se o índice único do MongoDB impede votos duplicados por pauta e associado mesmo via repositório.
    @Test
    void uniqueVoteIndexRejectsDuplicateWithoutService() {
        UUID id = agenda();
        voteRepository.insert(new VoteDocument(UUID.randomUUID().toString(), id.toString(), "same", VoteChoice.YES, NOW));
        assertThatThrownBy(() -> voteRepository.insert(
                new VoteDocument(UUID.randomUUID().toString(), id.toString(), "same", VoteChoice.NO, NOW)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_vote_agenda_associate");
        assertThat(voteRepository.count()).isEqualTo(1);
    }

    // Verifica se o índice único do MongoDB impede duas sessões para a mesma pauta mesmo via repositório.
    @Test
    void uniqueSessionIndexRejectsDuplicateWithoutService() {
        String id = agenda().toString();
        sessionRepository.insert(new VotingSessionDocument(UUID.randomUUID().toString(), id, NOW, NOW.plusSeconds(60)));
        assertThatThrownBy(() -> sessionRepository.insert(
                new VotingSessionDocument(UUID.randomUUID().toString(), id, NOW, NOW.plusSeconds(120))))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_session_agenda");
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    // Verifica se o adaptador converte a duplicidade de sessão no banco em VotingSessionAlreadyExistsException.
    @Test
    void sessionAdapterTranslatesDuplicateKeyToBusinessConflict() {
        UUID id = agenda();
        sessionPort.save(new VotingSession(UUID.randomUUID(), id, NOW, NOW.plusSeconds(60)));
        assertThatThrownBy(() -> sessionPort.save(
                new VotingSession(UUID.randomUUID(), id, NOW, NOW.plusSeconds(120))))
                .isInstanceOf(VotingSessionAlreadyExistsException.class);
    }

    // Verifica a existência e a ordem dos campos do índice composto usado para contar votos por pauta e escolha.
    @Test
    void createsIndexForCountingVotesByAgendaAndChoice() {
        var indexes = mongoTemplate.indexOps(VoteDocument.class).getIndexInfo();
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getName()).isEqualTo("idx_vote_agenda_choice");
            assertThat(index.getIndexFields()).extracting(field -> field.getKey())
                    .containsExactly("agendaId", "choice");
        });
    }

    // Verifica se dois votos simultâneos do mesmo associado geram HTTP 201 e 409, persistindo apenas um voto.
    @Test
    void concurrentVotesPersistOnlyOneVote() throws Exception {
        UUID id = agenda();
        voting.openSession(id, 1);
        assertConcurrentCreation(() -> mvc.perform(post(path(id, "votes")).contentType(APPLICATION_JSON)
                .content("""
                        {"associateId":"same","choice":"YES"}
                        """))
                .andReturn().getResponse().getStatus());
        assertThat(voteRepository.count()).isEqualTo(1);
    }

    // Verifica se duas aberturas simultâneas para a mesma pauta geram HTTP 201 e 409, persistindo apenas uma sessão.
    @Test
    void concurrentSessionOpeningPersistsOnlyOneSession() throws Exception {
        UUID id = agenda();
        assertConcurrentCreation(() -> mvc.perform(post(path(id, "sessions")))
                .andReturn().getResponse().getStatus());
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    // Verifica o acesso ao Swagger UI e a documentação das respostas 201 de criação de pauta e 409 de votação.
    @Test
    void swaggerDocumentsEndpointsAndErrors() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/agendas'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agendas/{agendaId}/votes'].post.responses['409']").exists());
    }

    // Cria uma pauta com título fixo e retorna seu identificador para uso nos testes.
    private UUID agenda() {
        return voting.createAgenda("Contas de 2026", null).id();
    }

    // Monta o caminho da API para acessar um recurso de uma pauta.
    private String path(UUID id, String resource) {
        return "/api/v1/agendas/" + id + "/" + resource;
    }

    // Envia um voto pela API e verifica o status HTTP esperado pelo cenário de teste.
    private void cast(UUID id, String associate, String choice, int expectedStatus) throws Exception {
        mvc.perform(post(path(id, "votes")).contentType(APPLICATION_JSON)
                .content("{\"associateId\":\"" + associate + "\",\"choice\":\"" + choice + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    // Ajusta as datas no banco para que a sessão esteja encerrada no instante fixo utilizado pelos testes.
    private void closeSession(UUID id) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("agendaId").is(id.toString())),
                new Update().set("openedAt", NOW.minusSeconds(60)).set("closesAt", NOW),
                VotingSessionDocument.class);
    }

    // Executa duas requisições sincronizadas e verifica se uma retorna criação (201) e a outra conflito (409).
    private void assertConcurrentCreation(Callable<Integer> request) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> synchronizedRequest = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return request.call();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(synchronizedRequest);
            var second = executor.submit(synchronizedRequest);
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @TestConfiguration
    static class TimeConfiguration {
        // Fornece um relógio fixo em UTC para tornar as verificações de datas e duração determinísticas.
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
