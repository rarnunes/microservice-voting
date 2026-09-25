package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.AgendaDocument;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.AgendaMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.AgendaPort;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;

@Component
public class AgendaPersistenceAdapter implements AgendaPort {
    private final AgendaMongoRepository repository;

    public AgendaPersistenceAdapter(AgendaMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Agenda save(Agenda agenda) {
        AgendaDocument document = new AgendaDocument(agenda.id().toString(), agenda.title(),
                agenda.description(), agenda.createdAt());
        return toDomain(repository.insert(document));
    }

    @Override
    public Optional<Agenda> findById(UUID agendaId) {
        return repository.findById(agendaId.toString()).map(this::toDomain);
    }

    @Override
    public List<Agenda> findAll() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream().map(this::toDomain).toList();
    }

    private Agenda toDomain(AgendaDocument document) {
        return new Agenda(UUID.fromString(document.getId()), document.getTitle(),
                document.getDescription(), document.getCreatedAt());
    }
}
