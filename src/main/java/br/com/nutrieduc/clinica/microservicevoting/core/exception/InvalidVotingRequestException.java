package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class InvalidVotingRequestException extends RuntimeException {
    public InvalidVotingRequestException(String message) {
        super(message);
    }
}
