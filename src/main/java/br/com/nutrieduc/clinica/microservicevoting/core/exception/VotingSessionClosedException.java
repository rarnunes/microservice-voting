package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class VotingSessionClosedException extends RuntimeException {
    public VotingSessionClosedException() {
        super("Voting session is closed");
    }
}
