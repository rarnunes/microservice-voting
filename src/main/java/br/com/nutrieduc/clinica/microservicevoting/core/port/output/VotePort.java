package br.com.nutrieduc.clinica.microservicevoting.core.port.output;

import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.VoteTotals;

public interface VotePort {
    Vote save(Vote vote);
    VoteTotals countByAgendaId(UUID agendaId);
}
