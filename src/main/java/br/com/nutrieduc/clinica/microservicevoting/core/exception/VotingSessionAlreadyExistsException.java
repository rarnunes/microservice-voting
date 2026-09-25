package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class VotingSessionAlreadyExistsException extends RuntimeException {
    public VotingSessionAlreadyExistsException() {
        super("Agenda already has a voting session");
    }
}
