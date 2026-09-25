package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class VotingSessionNotFoundException extends RuntimeException {
    public VotingSessionNotFoundException() {
        super("Voting session not found");
    }
}
