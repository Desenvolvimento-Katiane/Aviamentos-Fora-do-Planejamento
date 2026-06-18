package com.example.ProjetoPadrao.model;

public record AviamentoPlanejado(
        String modelo,
        String codigoSystextil,
        String codigoNormalizado,
        String descricaoSystextil,
        int totalAprovacaoAviamento,
        String aprovCont,
        String linha) {
}
