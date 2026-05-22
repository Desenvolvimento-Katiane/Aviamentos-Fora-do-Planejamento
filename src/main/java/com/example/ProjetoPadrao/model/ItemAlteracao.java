package com.example.ProjetoPadrao.model;

import java.util.List;

public record ItemAlteracao(
        String codigoSystextil,
        String descricaoSystextil,
        int quantidadeAtual,
        int totalAlteracoes,
        List<String> historico) {
}
