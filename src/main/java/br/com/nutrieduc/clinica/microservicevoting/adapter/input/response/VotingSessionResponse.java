package br.com.nutrieduc.clinica.microservicevoting.adapter.input.response;

import java.util.UUID;
import java.time.Instant;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;

public record VotingSessionResponse(UUID id, UUID agendaId, Instant openedAt, Instant closesAt) {
    public static VotingSessionResponse from(VotingSession value) {
        return new VotingSessionResponse(value.id(), value.agendaId(), value.openedAt(), value.closesAt());
    }
}
