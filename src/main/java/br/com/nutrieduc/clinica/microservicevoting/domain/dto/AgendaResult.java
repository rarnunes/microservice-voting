package br.com.nutrieduc.clinica.microservicevoting.domain.dto;

import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.SessionStatus;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VotingResult;

public record AgendaResult(UUID agendaId, long yesVotes, long noVotes, long totalVotes,
                           VotingResult result, SessionStatus sessionStatus) {}
