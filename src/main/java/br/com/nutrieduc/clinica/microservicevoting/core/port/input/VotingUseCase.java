package br.com.nutrieduc.clinica.microservicevoting.core.port.input;

import java.util.List;
import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.AgendaResult;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;

public interface VotingUseCase {
    Agenda createAgenda(String title, String description);
    Agenda getAgenda(UUID agendaId);
    List<Agenda> listAgendas();
    VotingSession openSession(UUID agendaId, Integer durationMinutes);
    Vote castVote(UUID agendaId, String associateId, VoteChoice choice);
    AgendaResult getResult(UUID agendaId);
}
