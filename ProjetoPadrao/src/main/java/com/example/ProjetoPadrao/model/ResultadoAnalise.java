package com.example.ProjetoPadrao.model;

import java.util.List;

public record ResultadoAnalise(
        List<ItemRelatorio> excessos,
        List<ItemRelatorio> semPlanejamento,
        List<ItemRelatorio> nuncaUtilizados) {
}
