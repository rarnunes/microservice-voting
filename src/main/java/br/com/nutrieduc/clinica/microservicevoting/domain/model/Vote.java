package br.com.nutrieduc.clinica.microservicevoting.domain.model;

import java.time.Instant;
import java.util.UUID;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;

public record Vote(UUID id, UUID agendaId, String associateId, VoteChoice choice, Instant createdAt) {}
