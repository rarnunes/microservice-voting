package br.com.nutrieduc.clinica.microservicevoting.core.port.output;

import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;
import java.util.List;
import java.util.Optional;

public interface AgendaPort {
    Agenda save(Agenda agenda);
    Optional<Agenda> findById(UUID agendaId);
    List<Agenda> findAll();
}
