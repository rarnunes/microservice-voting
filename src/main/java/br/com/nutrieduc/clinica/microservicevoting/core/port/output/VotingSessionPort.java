package br.com.nutrieduc.clinica.microservicevoting.core.port.output;

import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;
import java.util.Optional;

public interface VotingSessionPort {
    VotingSession save(VotingSession session);
    Optional<VotingSession> findByAgendaId(UUID agendaId);
}
