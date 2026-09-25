package br.com.nutrieduc.clinica.microservicevoting.core.services;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.*;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.AgendaPort;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotePort;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotingSessionPort;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.VoteTotals;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.SessionStatus;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VotingResult;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VotingServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private final UUID agendaId = UUID.randomUUID();
    private final Agenda agenda = new Agenda(agendaId, "Contas de 2026", "Descrição", NOW);
    @Mock private AgendaPort agendaPort;
    @Mock private VotingSessionPort sessionPort;
    @Mock private VotePort votePort;
    private VotingService service;

    // Cria o serviço com dependências simuladas e relógio fixo antes de cada teste.
    @BeforeEach
    void setUp() {
        service = new VotingService(agendaPort, sessionPort, votePort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // Verifica se a pauta é criada com identificador, título, descrição e data corretos e enviada para persistência.
    @Test
    void createsAgenda() {
        when(agendaPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Agenda created = service.createAgenda("Contas de 2026", "Descrição");
        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Contas de 2026");
        assertThat(created.description()).isEqualTo("Descrição");
        assertThat(created.createdAt()).isEqualTo(NOW);
        verify(agendaPort).save(created);
    }

    // Verifica se a sessão é criada para a pauta informada com identificador e duração solicitada de cinco minutos.
    @Test
    void opensSessionWithRequestedDuration() {
        existingAgenda();
        when(sessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        VotingSession session = service.openSession(agendaId, 5);
        assertThat(session.id()).isNotNull();
        assertThat(session.agendaId()).isEqualTo(agendaId);
        assertThat(session.openedAt()).isEqualTo(NOW);
        assertThat(session.closesAt()).isEqualTo(NOW.plusSeconds(300));
    }

    // Verifica se a sessão recebe a duração padrão de um minuto quando a duração informada é nula.
    @Test
    void defaultsSessionToOneMinute() {
        existingAgenda();
        when(sessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.openSession(agendaId, null).closesAt()).isEqualTo(NOW.plusSeconds(60));
    }

    // Verifica se durações iguais a zero ou negativas lançam InvalidVotingRequestException sem salvar a sessão.
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveDuration(int duration) {
        existingAgenda();
        assertThatThrownBy(() -> service.openSession(agendaId, duration))
                .isInstanceOf(InvalidVotingRequestException.class);
        verify(sessionPort, never()).save(any());
    }

    // Verifica se uma sessão anterior, mesmo encerrada, impede a criação de outra para a mesma pauta.
    @Test
    void refusesSecondSessionEvenAfterClosing() {
        existingAgenda();
        sessionClosingAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.openSession(agendaId, 1))
                .isInstanceOf(VotingSessionAlreadyExistsException.class);
        verify(sessionPort, never()).save(any());
    }

    // Verifica se votos YES e NO são criados com os dados corretos e enviados para persistência durante a sessão aberta.
    @ParameterizedTest
    @EnumSource(VoteChoice.class)
    void castsYesAndNoVotes(VoteChoice choice) {
        existingAgenda();
        sessionClosingAt(NOW.plusSeconds(60));
        when(votePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Vote vote = service.castVote(agendaId, "associate-123", choice);
        assertThat(vote.id()).isNotNull();
        assertThat(vote.agendaId()).isEqualTo(agendaId);
        assertThat(vote.associateId()).isEqualTo("associate-123");
        assertThat(vote.choice()).isEqualTo(choice);
        assertThat(vote.createdAt()).isEqualTo(NOW);
        verify(votePort).save(vote);
    }

    // Verifica se votar em uma pauta sem sessão lança VotingSessionNotFoundException sem acessar a persistência de votos.
    @Test
    void refusesVoteWithoutSession() {
        existingAgenda();
        assertThatThrownBy(() -> service.castVote(agendaId, "associate-123", VoteChoice.YES))
                .isInstanceOf(VotingSessionNotFoundException.class);
        verifyNoInteractions(votePort);
    }

    // Verifica se votos no instante de encerramento ou após ele são rejeitados sem acessar a persistência de votos.
    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void refusesVoteAtOrAfterClosingTime(long secondsUntilClosing) {
        existingAgenda();
        sessionClosingAt(NOW.plusSeconds(secondsUntilClosing));
        assertThatThrownBy(() -> service.castVote(agendaId, "associate-123", VoteChoice.YES))
                .isInstanceOf(VotingSessionClosedException.class);
        verifyNoInteractions(votePort);
    }

    // Verifica se o serviço propaga DuplicateVoteException quando a persistência rejeita um voto duplicado.
    @Test
    void propagatesDuplicateVoteConflict() {
        existingAgenda();
        sessionClosingAt(NOW.plusSeconds(60));
        when(votePort.save(any())).thenThrow(new DuplicateVoteException());
        assertThatThrownBy(() -> service.castVote(agendaId, "associate-123", VoteChoice.YES))
                .isInstanceOf(DuplicateVoteException.class);
    }

    // Verifica os totais, a vitória de YES ou NO e o empate, inclusive sem votos, mantendo o status de sessão aberta.
    @ParameterizedTest
    @CsvSource({"10,5,YES", "5,10,NO", "5,5,TIE", "0,0,TIE"})
    void calculatesResult(long yes, long no, VotingResult expected) {
        existingAgenda();
        sessionClosingAt(NOW.plusSeconds(60));
        when(votePort.countByAgendaId(agendaId)).thenReturn(new VoteTotals(yes, no));
        var result = service.getResult(agendaId);
        assertThat(result.agendaId()).isEqualTo(agendaId);
        assertThat(result.yesVotes()).isEqualTo(yes);
        assertThat(result.noVotes()).isEqualTo(no);
        assertThat(result.totalVotes()).isEqualTo(yes + no);
        assertThat(result.result()).isEqualTo(expected);
        assertThat(result.sessionStatus()).isEqualTo(SessionStatus.OPEN);
    }

    // Verifica se o resultado informa a sessão como encerrada exatamente no instante limite de votação.
    @Test
    void returnsClosedStatusAtDeadline() {
        existingAgenda();
        sessionClosingAt(NOW);
        when(votePort.countByAgendaId(agendaId)).thenReturn(new VoteTotals(0, 0));
        assertThat(service.getResult(agendaId).sessionStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    // Verifica se consultar o resultado sem sessão lança VotingSessionNotFoundException sem consultar os votos.
    @Test
    void resultRequiresSession() {
        existingAgenda();
        assertThatThrownBy(() -> service.getResult(agendaId)).isInstanceOf(VotingSessionNotFoundException.class);
        verifyNoInteractions(votePort);
    }

    // Verifica se consultar pauta, abrir sessão, votar e obter resultado exigem uma pauta existente.
    // Confirma que a ausência da pauta lança AgendaNotFoundException sem acessar sessões ou votos.
    @Test
    void rejectsOperationsOnMissingAgenda() {
        assertThatThrownBy(() -> service.getAgenda(agendaId)).isInstanceOf(AgendaNotFoundException.class);
        assertThatThrownBy(() -> service.openSession(agendaId, 1)).isInstanceOf(AgendaNotFoundException.class);
        assertThatThrownBy(() -> service.castVote(agendaId, "associate-123", VoteChoice.YES))
                .isInstanceOf(AgendaNotFoundException.class);
        assertThatThrownBy(() -> service.getResult(agendaId)).isInstanceOf(AgendaNotFoundException.class);
        verifyNoInteractions(sessionPort, votePort);
    }

    // Configura a dependência simulada para retornar uma pauta existente ao consultar seu identificador.
    private void existingAgenda() {
        when(agendaPort.findById(agendaId)).thenReturn(Optional.of(agenda));
    }

    // Configura a dependência simulada para retornar uma sessão da pauta com o instante de encerramento informado.
    private void sessionClosingAt(Instant closesAt) {
        when(sessionPort.findByAgendaId(agendaId)).thenReturn(Optional.of(
                new VotingSession(UUID.randomUUID(), agendaId, NOW.minusSeconds(60), closesAt)));
    }
}
