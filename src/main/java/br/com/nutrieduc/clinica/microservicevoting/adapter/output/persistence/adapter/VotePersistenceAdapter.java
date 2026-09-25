package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.adapter;

import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document.VoteDocument;
import br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.repository.VoteMongoRepository;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.DuplicateVoteException;
import br.com.nutrieduc.clinica.microservicevoting.core.port.output.VotePort;
import br.com.nutrieduc.clinica.microservicevoting.domain.dto.VoteTotals;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Vote;

@Component
public class VotePersistenceAdapter implements VotePort {
    private final VoteMongoRepository repository;

    public VotePersistenceAdapter(VoteMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Vote save(Vote vote) {
        VoteDocument document = new VoteDocument(vote.id().toString(), vote.agendaId().toString(),
                vote.associateId(), vote.choice(), vote.createdAt());
        try {
            // O índice UNIQUE resolve a concorrência; insert também evita sobrescrever um voto existente.
            return toDomain(repository.insert(document));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateVoteException();
        }
    }

    @Override
    public VoteTotals countByAgendaId(UUID agendaId) {
        long yesVotes = repository.countByAgendaIdAndChoice(agendaId.toString(), VoteChoice.YES);
        long noVotes = repository.countByAgendaIdAndChoice(agendaId.toString(), VoteChoice.NO);
        return new VoteTotals(yesVotes, noVotes);
    }

    private Vote toDomain(VoteDocument document) {
        return new Vote(UUID.fromString(document.getId()), UUID.fromString(document.getAgendaId()),
                document.getAssociateId(), document.getChoice(), document.getCreatedAt());
    }
}
