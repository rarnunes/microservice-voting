package br.com.nutrieduc.clinica.microservicevoting.adapter.input.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAgendaRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must have at most 255 characters") String title,
        String description) {}
