package br.com.nutrieduc.clinica.microservicevoting.domain.dto;

public record VoteTotals(long yesVotes, long noVotes) {
    public long totalVotes() {
        return yesVotes + noVotes;
    }
}
