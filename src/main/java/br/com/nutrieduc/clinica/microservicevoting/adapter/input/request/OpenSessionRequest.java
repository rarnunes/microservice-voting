package br.com.nutrieduc.clinica.microservicevoting.adapter.input.request;

import jakarta.validation.constraints.Positive;

public record OpenSessionRequest(
        @Positive(message = "Duration must be greater than zero") Integer durationMinutes) {}
