package br.com.nutrieduc.clinica.microservicevoting.adapter.input.response;

import java.util.UUID;
import java.time.Instant;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;

public record VoteResponse(UUID id, UUID agendaId, String associateId, VoteChoice choice, Instant createdAt) {
    public static VoteResponse from(Vote value) {
        return new VoteResponse(value.id(), value.agendaId(), value.associateId(), value.choice(), value.createdAt());
    }
}
