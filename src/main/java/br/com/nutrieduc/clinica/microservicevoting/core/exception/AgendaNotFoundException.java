package br.com.nutrieduc.clinica.microservicevoting.core.exception;

public class AgendaNotFoundException extends RuntimeException {
    public AgendaNotFoundException() {
        super("Agenda not found");
    }
}
