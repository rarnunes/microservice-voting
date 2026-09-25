package br.com.nutrieduc.clinica.microservicevoting.domain.model;

import java.time.Instant;
import java.util.UUID;

public record VotingSession(UUID id, UUID agendaId, Instant openedAt, Instant closesAt) {
    public boolean isOpenAt(Instant instant) {
        return !instant.isBefore(openedAt) && instant.isBefore(closesAt);
    }
}
