package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "voting_sessions")
public class VotingSessionDocument {
    @Id
    private final String id;
    @Indexed(name = "uk_session_agenda", unique = true)
    private final String agendaId;
    private final Instant openedAt;
    private final Instant closesAt;

    public VotingSessionDocument(String id, String agendaId, Instant openedAt, Instant closesAt) {
        this.id = id;
        this.agendaId = agendaId;
        this.openedAt = openedAt;
        this.closesAt = closesAt;
    }

    public String getId() {
        return id;
    }

    public String getAgendaId() {
        return agendaId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }
}
