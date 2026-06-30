package com.example.ProjetoPadrao.model;

public record ItemRelatorio(
        String modelo,
        String codigoSystextil,
        String descricaoSystextil,
        int totalAprovacaoAviamento,
        String aprovCont,
        double totalModeloSomado,
        double diferenca,
        boolean semPlanejamento,
        String marcas,
        String linha,
        String colecao) {
}
