package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;

@Document(collection = "votes")
@CompoundIndexes({
        @CompoundIndex(name = "uk_vote_agenda_associate", def = "{'agendaId': 1, 'associateId': 1}", unique = true),
        @CompoundIndex(name = "idx_vote_agenda_choice", def = "{'agendaId': 1, 'choice': 1}")
})
public class VoteDocument {
    @Id
    private final String id;
    private final String agendaId;
    private final String associateId;
    private final VoteChoice choice;
    private final Instant createdAt;

    public VoteDocument(String id, String agendaId, String associateId, VoteChoice choice, Instant createdAt) {
        this.id = id;
        this.agendaId = agendaId;
        this.associateId = associateId;
        this.choice = choice;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getAgendaId() {
        return agendaId;
    }

    public String getAssociateId() {
        return associateId;
    }

    public VoteChoice getChoice() {
        return choice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
