package br.com.nutrieduc.clinica.microservicevoting.adapter.input.response;

import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VotingResult;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.SessionStatus;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.AgendaResult;

public record AgendaResultResponse(UUID agendaId, long yesVotes, long noVotes, long totalVotes, VotingResult result, SessionStatus sessionStatus) {
    public static AgendaResultResponse from(AgendaResult value) {
        return new AgendaResultResponse(value.agendaId(), value.yesVotes(), value.noVotes(), value.totalVotes(), value.result(), value.sessionStatus());
    }
}
