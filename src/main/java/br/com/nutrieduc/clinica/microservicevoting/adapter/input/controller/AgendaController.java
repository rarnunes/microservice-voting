package br.com.nutrieduc.clinica.microservicevoting.adapter.input.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.request.CastVoteRequest;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.request.CreateAgendaRequest;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.request.OpenSessionRequest;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.response.AgendaResponse;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.response.AgendaResultResponse;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.response.VoteResponse;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.response.VotingSessionResponse;
import br.com.nutrieduc.clinica.microservicevoting.core.port.input.VotingUseCase;

@RestController
@RequestMapping("/api/v1/agendas")
@Tag(name = "Agendas", description = "Pautas, sessões de votação e votos")
public class AgendaController {
    private final VotingUseCase voting;

    public AgendaController(VotingUseCase voting) {
        this.voting = voting;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar pauta")
    public ResponseEntity<AgendaResponse> createAgenda(@Valid @RequestBody CreateAgendaRequest request) {

        AgendaResponse response = AgendaResponse.from(voting.createAgenda(request.title(), request.description()));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{agendaId}")
    @Operation(summary = "Buscar pauta")
    public AgendaResponse getAgenda(@PathVariable UUID agendaId) {
        return AgendaResponse.from(voting.getAgenda(agendaId));
    }

    @GetMapping
    @Operation(summary = "Listar pautas")
    public List<AgendaResponse> listAgendas() {
        return voting.listAgendas().stream().map(AgendaResponse::from).toList();
    }

    @PostMapping("/{agendaId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir sessão", description = "Uma sessão por pauta. Duração padrão de 1 minuto.")
    public VotingSessionResponse openSession(@PathVariable UUID agendaId,
            @Valid @RequestBody(required = false) OpenSessionRequest request) {
        return VotingSessionResponse.from(voting.openSession(agendaId, request == null ? null : request.durationMinutes()));
    }

    @PostMapping("/{agendaId}/votes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar voto", description = "Aceita YES ou NO. Um voto por associado em cada pauta.")
    public VoteResponse castVote(@PathVariable UUID agendaId, @Valid @RequestBody CastVoteRequest request) {
        return VoteResponse.from(voting.castVote(agendaId, request.associateId(), request.choice()));
    }

    @GetMapping("/{agendaId}/result")
    @Operation(summary = "Consultar resultado", description = "Contagem atual, inclusive durante a sessão. Exige sessão existente.")
    public AgendaResultResponse getResult(@PathVariable UUID agendaId) {
        return AgendaResultResponse.from(voting.getResult(agendaId));
    }
}
