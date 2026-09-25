package br.com.nutrieduc.clinica.microservicevoting.core.services;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.nutrieduc.clinica.microservicevoting.core.exception.AgendaNotFoundException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.InvalidVotingRequestException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionAlreadyExistsException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionClosedException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionNotFoundException;
import br.com.nutrieduc.clinica.microservicevoting.core.port.input.VotingUseCase;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.AgendaPort;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotePort;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotingSessionPort;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.AgendaResult;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.VoteTotals;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.SessionStatus;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VotingResult;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;

@Service
public class VotingService implements VotingUseCase {
    private static final Logger log = LoggerFactory.getLogger(VotingService.class);
    private static final int DEFAULT_DURATION_MINUTES = 1;

    private final AgendaPort agendaPort;
    private final VotingSessionPort sessionPort;
    private final VotePort votePort;
    private final Clock clock;

    public VotingService(AgendaPort agendaPort, VotingSessionPort sessionPort, VotePort votePort, Clock clock) {
        this.agendaPort = agendaPort;
        this.sessionPort = sessionPort;
        this.votePort = votePort;
        this.clock = clock;
    }

    @Override
    public Agenda createAgenda(String title, String description) {
        if (title == null || title.isBlank()) {
            throw new InvalidVotingRequestException("Title is required");
        }
        Agenda agenda = agendaPort.save(new Agenda(UUID.randomUUID(), title, description, clock.instant()));
        log.info("Agenda created: agendaId={}", agenda.id());
        return agenda;
    }

    @Override
    public Agenda getAgenda(UUID agendaId) {
        return agendaPort.findById(agendaId).orElseThrow(AgendaNotFoundException::new);
    }

    @Override
    public List<Agenda> listAgendas() {
        return agendaPort.findAll();
    }

    @Override
    public VotingSession openSession(UUID agendaId, Integer durationMinutes) {
        getAgenda(agendaId);
        int duration = durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
        if (duration <= 0) {
            throw new InvalidVotingRequestException("Duration must be greater than zero");
        }
        if (sessionPort.findByAgendaId(agendaId).isPresent()) {
            throw new VotingSessionAlreadyExistsException();
        }
        Instant openedAt = clock.instant();
        VotingSession session = sessionPort.save(new VotingSession(UUID.randomUUID(), agendaId,
                openedAt, openedAt.plus(duration, ChronoUnit.MINUTES)));
        log.info("Voting session opened: agendaId={}, closesAt={}", agendaId, session.closesAt());
        return session;
    }

    @Override
    public Vote castVote(UUID agendaId, String associateId, VoteChoice choice) {
        getAgenda(agendaId);
        if (associateId == null || associateId.isBlank() || choice == null) {
            throw new InvalidVotingRequestException("Associate ID and choice are required");
        }
        VotingSession session = getSession(agendaId);
        Instant now = clock.instant();
        if (!session.isOpenAt(now)) {
            throw new VotingSessionClosedException();
        }
        Vote vote = votePort.save(new Vote(UUID.randomUUID(), agendaId, associateId, choice, now));
        log.info("Vote registered: agendaId={}, voteId={}", agendaId, vote.id());
        return vote;
    }

    @Override
    public AgendaResult getResult(UUID agendaId) {
        getAgenda(agendaId);
        VotingSession session = getSession(agendaId);
        VoteTotals totals = votePort.countByAgendaId(agendaId);
        VotingResult result = totals.yesVotes() > totals.noVotes() ? VotingResult.YES
                : totals.noVotes() > totals.yesVotes() ? VotingResult.NO : VotingResult.TIE;
        SessionStatus status = session.isOpenAt(clock.instant()) ? SessionStatus.OPEN : SessionStatus.CLOSED;
        return new AgendaResult(agendaId, totals.yesVotes(), totals.noVotes(), totals.totalVotes(), result, status);
    }

    private VotingSession getSession(UUID agendaId) {
        return sessionPort.findByAgendaId(agendaId).orElseThrow(VotingSessionNotFoundException::new);
    }
}
