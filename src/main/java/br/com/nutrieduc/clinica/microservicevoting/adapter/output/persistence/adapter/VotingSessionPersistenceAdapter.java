package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VotingSessionDocument;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.VotingSessionMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionAlreadyExistsException;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotingSessionPort;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.VotingSession;

@Component
public class VotingSessionPersistenceAdapter implements VotingSessionPort {
    private final VotingSessionMongoRepository repository;

    public VotingSessionPersistenceAdapter(VotingSessionMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public VotingSession save(VotingSession session) {
        VotingSessionDocument document = new VotingSessionDocument(session.id().toString(),
                session.agendaId().toString(), session.openedAt(), session.closesAt());
        try {
            return toDomain(repository.insert(document));
        } catch (DuplicateKeyException exception) {
            throw new VotingSessionAlreadyExistsException();
        }
    }

    @Override
    public Optional<VotingSession> findByAgendaId(UUID agendaId) {
        return repository.findByAgendaId(agendaId.toString()).map(this::toDomain);
    }

    private VotingSession toDomain(VotingSessionDocument document) {
        return new VotingSession(UUID.fromString(document.getId()), UUID.fromString(document.getAgendaId()),
                document.getOpenedAt(), document.getClosesAt());
    }
}
