package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class DuplicateVoteException extends RuntimeException {
    public DuplicateVoteException() {
        super("Associate has already voted on this agenda");
    }
}
