package com.example.ProjetoPadrao.model;

import java.util.List;

public record ItemAlteracao(
        String codigoSystextil,
        String descricaoSystextil,
        String modelo,
        String aprovCont,
        int quantidadeAtual,
        int totalAlteracoes,
        List<String> historico) {
}
