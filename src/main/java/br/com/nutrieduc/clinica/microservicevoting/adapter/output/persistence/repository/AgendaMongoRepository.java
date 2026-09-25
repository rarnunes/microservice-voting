package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.AgendaDocument;

public interface AgendaMongoRepository extends MongoRepository<AgendaDocument, String> {
    List<AgendaDocument> findAllByOrderByCreatedAtAscIdAsc();
}
