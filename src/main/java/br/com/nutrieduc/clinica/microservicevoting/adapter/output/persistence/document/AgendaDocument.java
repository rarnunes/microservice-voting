package br.com.nutrieduc.clinica.microservicevoting.adapter.output.persistence.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "agendas")
public class AgendaDocument {
    @Id
    private final String id;
    private final String title;
    private final String description;
    private final Instant createdAt;

    public AgendaDocument(String id, String title, String description, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
