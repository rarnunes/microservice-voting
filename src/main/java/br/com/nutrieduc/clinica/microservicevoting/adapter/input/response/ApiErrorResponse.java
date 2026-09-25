package br.com.nutrieduc.clinica.microservicevoting.adapter.input.response;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {}
