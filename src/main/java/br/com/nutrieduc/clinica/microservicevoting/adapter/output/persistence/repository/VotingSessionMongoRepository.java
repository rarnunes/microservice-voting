package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VotingSessionDocument;

public interface VotingSessionMongoRepository extends MongoRepository<VotingSessionDocument, String> {
    Optional<VotingSessionDocument> findByAgendaId(String agendaId);
}
