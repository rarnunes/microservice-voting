package br.com.nutrieduc.clinica.microservicevoting.adapter.input.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import br.com.nutrieduc.clinica.microservicevoting.domain.enums.VoteChoice;

public record CastVoteRequest(
        @NotBlank(message = "Associate ID is required")
        @Size(max = 100, message = "Associate ID must have at most 100 characters") String associateId,
        @NotNull(message = "Choice is required") VoteChoice choice) {}
