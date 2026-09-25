package br.com.nutrieduc.clinica.microservicevoting.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Agenda(UUID id, String title, String description, Instant createdAt) {}
