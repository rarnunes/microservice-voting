package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository;

import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import org.springframework.data.mongodb.repository.MongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VoteDocument;

public interface VoteMongoRepository extends MongoRepository<VoteDocument, String> {
    long countByAgendaIdAndChoice(String agendaId, VoteChoice choice);
}
