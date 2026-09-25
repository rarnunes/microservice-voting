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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::uri);
        registry.add("spring.data.mongodb.database", () -> "voting_api_test");
    }

    @AfterAll
    static void stopMongo() {
        mongo.close();
    }

    @BeforeEach
    void cleanDatabase() {
        voteRepository.deleteAll();
        sessionRepository.deleteAll();
        agendaRepository.deleteAll();
    }

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

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"durationMinutes\":null}"})
    void defaultsMissingDuration(String body) throws Exception {
        mvc.perform(post(path(agenda(), "sessions")).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closesAt").value(NOW.plusSeconds(60).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "2147483648"})
    void rejectsInvalidDuration(String duration) throws Exception {
        mvc.perform(post(path(agenda(), "sessions")).contentType(APPLICATION_JSON)
                .content("{\"durationMinutes\":" + duration + "}"))
                .andExpect(status().isBadRequest());
    }

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

    @Test
    void missingSessionReturns404() throws Exception {
        UUID id = agenda();
        cast(id, "a", "YES", 404);
        mvc.perform(get(path(id, "result"))).andExpect(status().isNotFound());
    }

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

    @Test
    void rejectsInvalidUuid() throws Exception {
        mvc.perform(get("/api/v1/agendas/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/agendas/not-a-uuid"));
    }

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

    @Test
    void sessionAdapterTranslatesDuplicateKeyToBusinessConflict() {
        UUID id = agenda();
        sessionPort.save(new VotingSession(UUID.randomUUID(), id, NOW, NOW.plusSeconds(60)));
        assertThatThrownBy(() -> sessionPort.save(
                new VotingSession(UUID.randomUUID(), id, NOW, NOW.plusSeconds(120))))
                .isInstanceOf(VotingSessionAlreadyExistsException.class);
    }

    @Test
    void createsIndexForCountingVotesByAgendaAndChoice() {
        var indexes = mongoTemplate.indexOps(VoteDocument.class).getIndexInfo();
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getName()).isEqualTo("idx_vote_agenda_choice");
            assertThat(index.getIndexFields()).extracting(field -> field.getKey())
                    .containsExactly("agendaId", "choice");
        });
    }

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

    @Test
    void concurrentSessionOpeningPersistsOnlyOneSession() throws Exception {
        UUID id = agenda();
        assertConcurrentCreation(() -> mvc.perform(post(path(id, "sessions")))
                .andReturn().getResponse().getStatus());
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void swaggerDocumentsEndpointsAndErrors() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/agendas'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agendas/{agendaId}/votes'].post.responses['409']").exists());
    }

    private UUID agenda() {
        return voting.createAgenda("Contas de 2026", null).id();
    }

    private String path(UUID id, String resource) {
        return "/api/v1/agendas/" + id + "/" + resource;
    }

    private void cast(UUID id, String associate, String choice, int expectedStatus) throws Exception {
        mvc.perform(post(path(id, "votes")).contentType(APPLICATION_JSON)
                .content("{\"associateId\":\"" + associate + "\",\"choice\":\"" + choice + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private void closeSession(UUID id) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("agendaId").is(id.toString())),
                new Update().set("openedAt", NOW.minusSeconds(60)).set("closesAt", NOW),
                VotingSessionDocument.class);
    }

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
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
