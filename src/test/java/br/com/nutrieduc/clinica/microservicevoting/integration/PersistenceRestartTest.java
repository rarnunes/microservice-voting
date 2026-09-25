package br.com.nutrieduc.clinica.microservicevoting.integration;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import br.com.nutrieduc.clinica.microservicevoting.MicroserviceVotingApplication;
import br.com.nutrieduc.clinica.microservicevoting.support.LocalMongo;
import br.com.nutrieduc.clinica.microservicevoting.core.port.input.VotingUseCase;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @Test
    void agendasSessionsAndVotesSurviveApplicationRestart() {
        try (LocalMongo mongo = new LocalMongo()) {
            verifyApplicationRestart(mongo.uri());
        }
    }

    private void verifyApplicationRestart(String uri) {
        UUID agendaId;
        try (var application = start(uri)) {
            VotingUseCase voting = application.getBean(VotingUseCase.class);
            agendaId = voting.createAgenda("Persistência", "Deve sobreviver ao reinício").id();
            voting.openSession(agendaId, 5);
            voting.castVote(agendaId, "associate-123", VoteChoice.YES);
        }
        try (var application = start(uri)) {
            VotingUseCase voting = application.getBean(VotingUseCase.class);
            assertThat(voting.getAgenda(agendaId).title()).isEqualTo("Persistência");
            var result = voting.getResult(agendaId);
            assertThat(result.yesVotes()).isEqualTo(1);
            assertThat(result.noVotes()).isZero();
            assertThat(result.totalVotes()).isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext start(String uri) {
        return new SpringApplicationBuilder(MicroserviceVotingApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.data.mongodb.uri=" + uri,
                        "--spring.data.mongodb.database=voting_restart_test", "--spring.main.banner-mode=off");
    }
}
