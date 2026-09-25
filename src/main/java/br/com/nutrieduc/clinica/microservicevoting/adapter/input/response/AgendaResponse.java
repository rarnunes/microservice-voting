package br.com.nutrieduc.clinica.microservicevoting.adapter.input.response;

import java.util.UUID;
import java.time.Instant;
import br.com.nutrieduc.clinica.microservicevoting.domain.model.Agenda;

public record AgendaResponse(UUID id, String title, String description, Instant createdAt) {
    public static AgendaResponse from(Agenda value) {
        return new AgendaResponse(value.id(), value.title(), value.description(), value.createdAt());
    }
}
