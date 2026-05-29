package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ColecaoInfo;
import com.example.ProjetoPadrao.model.ItemRelatorio;
import com.example.ProjetoPadrao.model.ResultadoAnalise;
import com.example.ProjetoPadrao.model.TecidoPlanejado;
import com.example.ProjetoPadrao.model.TecidoUtilizado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

@Service
public class AnaliseService {

    private static final List<String> MARCAS_VALIDAS = List.of(
        "Animê Kids", "Animê Petite", "Animê Bebê",
        "Momi Kids",  "Momi Bebê",   "Momi Mini",
        "Authoria",   "Youccie",
        "Bimbi Menina", "Bimbi Menino"
    );

    public List<String> getMarcasValidas() {
        return MARCAS_VALIDAS;
    }

    private static String normalizarTexto(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private static Set<String> extrairMarcas(String rawMarca) {
        if (rawMarca == null || rawMarca.isBlank()) return Collections.emptySet();
        String inputNorm = normalizarTexto(rawMarca);
        Set<String> encontradas = new LinkedHashSet<>();
        for (String marca : MARCAS_VALIDAS) {
            if (inputNorm.contains(normalizarTexto(marca))) {
                encontradas.add(marca);
            }
        }
        if (encontradas.isEmpty()) {
            encontradas.add(rawMarca.trim());
        }
        return encontradas;
    }

    @Autowired
    private ExcelService excelService;

    @Autowired
    private ColecaoService colecaoService;

    // ── Análise por coleção ─────────────────────────────────────────────────

    public ResultadoAnalise analisar(String slug) throws IOException {
        return calcularResultado(
                excelService.lerPlanilha1(slug),
                excelService.lerPlanilha2(slug),
                excelService.arquivoExisteColecao(slug, "planilha3.xlsx")
                        ? excelService.lerPlanilha3(slug) : null,
                ""
        );
    }

    public ResultadoAnalise analisarColecaoCompleta(String slug) throws IOException {
        return calcularResultadoCC(
                excelService.lerPlanilha1(slug),
                excelService.lerPlanilha3(slug),
                ""
        );
    }

    // ── Soma de consumo por código (P3) ───────────────────────────────────

    public Map<String, Double> contarReferenciasP3(String slug) throws IOException {
        return agruparConsumo(excelService.lerPlanilha3(slug));
    }

    public Map<String, Double> contarReferenciasP3Todas() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p3Existe()) continue;
            try {
                for (TecidoUtilizado tu : excelService.lerPlanilha3(c.slug())) {
                    String cod = tu.codigoNormalizado();
                    if (!cod.isBlank()) result.merge(cod, tu.consumo(), Double::sum);
                }
            } catch (IOException ignored) {}
        }
        return result;
    }

    private Map<String, Double> agruparConsumo(List<TecidoUtilizado> lista) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (TecidoUtilizado tu : lista) {
            String cod = tu.codigoNormalizado();
            if (!cod.isBlank()) result.merge(cod, tu.consumo(), Double::sum);
        }
        return result;
    }

    // ── Análise "Todas as coleções" ─────────────────────────────────────────

    public ResultadoAnalise analisarTodas() throws IOException {
        List<ItemRelatorio> excessos = new ArrayList<>();
        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p1Existe() || !c.p2Existe()) continue;
            ResultadoAnalise r = calcularResultado(
                    excelService.lerPlanilha1(c.slug()),
                    excelService.lerPlanilha2(c.slug()),
                    c.p3Existe() ? excelService.lerPlanilha3(c.slug()) : null,
                    c.nomeOriginal()
            );
            excessos.addAll(r.excessos());
            semPlanejamento.addAll(r.semPlanejamento());
            nuncaUtilizados.addAll(r.nuncaUtilizados());
        }
        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    public ResultadoAnalise analisarColecaoCompletaTodas() throws IOException {
        List<ItemRelatorio> excessos = new ArrayList<>();
        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            if (!c.p1Existe() || !c.p3Existe()) continue;
            ResultadoAnalise r = calcularResultadoCC(
                    excelService.lerPlanilha1(c.slug()),
                    excelService.lerPlanilha3(c.slug()),
                    c.nomeOriginal()
            );
            excessos.addAll(r.excessos());
            semPlanejamento.addAll(r.semPlanejamento());
            nuncaUtilizados.addAll(r.nuncaUtilizados());
        }
        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    // ── Métodos legados (lêem de uploads/ raiz) ─────────────────────────────

    public ResultadoAnalise analisar() throws IOException {
        return calcularResultado(
                excelService.lerPlanilha1(),
                excelService.lerPlanilha2(),
                excelService.arquivoExiste("planilha3.xlsx") ? excelService.lerPlanilha3() : null,
                ""
        );
    }

    public ResultadoAnalise analisarColecaoCompleta() throws IOException {
        return calcularResultadoCC(
                excelService.lerPlanilha1(),
                excelService.lerPlanilha3(),
                ""
        );
    }

    // ── Lógica central ──────────────────────────────────────────────────────

    private ResultadoAnalise calcularResultado(
            List<TecidoPlanejado> planejados,
            List<TecidoUtilizado> utilizados,
            List<TecidoUtilizado> utilizados3,
            String colecaoLabel) throws IOException {

        Map<String, TecidoPlanejado> mapPlanejado = new LinkedHashMap<>();
        Map<String, Set<String>> mapLinhas = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            mapPlanejado.putIfAbsent(tp.codigoNormalizado(), tp);
            for (String marca : extrairMarcas(tp.linha())) {
                mapLinhas.computeIfAbsent(tp.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
            }
        }

        Map<String, Double> mapConsumo = new LinkedHashMap<>();
        Map<String, Set<String>> mapMarcas = new LinkedHashMap<>();
        for (TecidoUtilizado tu : utilizados) {
            String cod = tu.codigoNormalizado();
            if (!cod.isBlank()) mapConsumo.merge(cod, tu.consumo(), Double::sum);
            for (String marca : extrairMarcas(tu.marca()))
                mapMarcas.computeIfAbsent(cod, k -> new LinkedHashSet<>()).add(marca);
        }

        List<ItemRelatorio> excessos = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            TecidoPlanejado tp = entry.getValue();
            double totalConsumido = mapConsumo.getOrDefault(codigo, 0.0);
            if (totalConsumido > tp.totalAprovacaoTecido() && tp.totalAprovacaoTecido() > 0) {
                String marcas = String.join(", ", mapMarcas.getOrDefault(codigo, Collections.emptySet()));
                excessos.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        totalConsumido, totalConsumido - tp.totalAprovacaoTecido(),
                        false, marcas, "", colecaoLabel));
            }
        }
        excessos.sort(Comparator.comparingDouble(ItemRelatorio::diferenca).reversed());

        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        for (Map.Entry<String, Double> entry : mapConsumo.entrySet()) {
            String codigo = entry.getKey();
            if (!mapPlanejado.containsKey(codigo)) {
                String codigoOriginal = utilizados.stream()
                        .filter(u -> u.codigoNormalizado().equals(codigo))
                        .map(TecidoUtilizado::codigoSystextil)
                        .findFirst().orElse(codigo);
                double totalConsumido = entry.getValue();
                semPlanejamento.add(new ItemRelatorio(
                        "", codigoOriginal, "", 0, "",
                        totalConsumido, totalConsumido, true, "", "", colecaoLabel));
            }
        }
        semPlanejamento.sort(Comparator.comparingDouble(ItemRelatorio::totalModeloSomado).reversed());

        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        if (utilizados3 != null) {
            Set<String> codigosP3 = new LinkedHashSet<>();
            for (TecidoUtilizado tu : utilizados3) {
                if (!tu.codigoNormalizado().isBlank()) codigosP3.add(tu.codigoNormalizado());
            }
            for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
                String codigo = entry.getKey();
                if (!codigosP3.contains(codigo)) {
                    TecidoPlanejado tp = entry.getValue();
                    String marcaNorm = String.join(", ", mapLinhas.getOrDefault(codigo, Collections.emptySet()));
                    if (marcaNorm.isBlank()) marcaNorm = tp.linha() != null ? tp.linha().trim() : "";
                    nuncaUtilizados.add(new ItemRelatorio(
                            tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                            tp.totalAprovacaoTecido(), tp.aprovCont(),
                            0, 0, false, "", marcaNorm, colecaoLabel));
                }
            }
            nuncaUtilizados.sort(Comparator.comparing(ItemRelatorio::codigoSystextil));
        }

        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }

    private ResultadoAnalise calcularResultadoCC(
            List<TecidoPlanejado> planejados,
            List<TecidoUtilizado> utilizados,
            String colecaoLabel) throws IOException {

        Map<String, TecidoPlanejado> mapPlanejado = new LinkedHashMap<>();
        Map<String, Set<String>> mapLinhas = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            mapPlanejado.putIfAbsent(tp.codigoNormalizado(), tp);
            for (String marca : extrairMarcas(tp.linha()))
                mapLinhas.computeIfAbsent(tp.codigoNormalizado(), k -> new LinkedHashSet<>()).add(marca);
        }

        Map<String, Double> mapConsumo = new LinkedHashMap<>();
        Map<String, Set<String>> mapMarcas = new LinkedHashMap<>();
        for (TecidoUtilizado tu : utilizados) {
            String cod = tu.codigoNormalizado();
            if (!cod.isBlank()) mapConsumo.merge(cod, tu.consumo(), Double::sum);
            for (String marca : extrairMarcas(tu.marca()))
                mapMarcas.computeIfAbsent(cod, k -> new LinkedHashSet<>()).add(marca);
        }

        List<ItemRelatorio> excessos = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            TecidoPlanejado tp = entry.getValue();
            double totalConsumido = mapConsumo.getOrDefault(codigo, 0.0);
            if (totalConsumido > tp.totalAprovacaoTecido() && tp.totalAprovacaoTecido() > 0) {
                String marcas = String.join(", ", mapMarcas.getOrDefault(codigo, Collections.emptySet()));
                excessos.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        totalConsumido, totalConsumido - tp.totalAprovacaoTecido(),
                        false, marcas, "", colecaoLabel));
            }
        }
        excessos.sort(Comparator.comparingDouble(ItemRelatorio::diferenca).reversed());

        List<ItemRelatorio> semPlanejamento = new ArrayList<>();
        for (Map.Entry<String, Double> entry : mapConsumo.entrySet()) {
            String codigo = entry.getKey();
            if (!mapPlanejado.containsKey(codigo)) {
                String codigoOriginal = utilizados.stream()
                        .filter(u -> u.codigoNormalizado().equals(codigo))
                        .map(TecidoUtilizado::codigoSystextil)
                        .findFirst().orElse(codigo);
                double totalConsumido = entry.getValue();
                semPlanejamento.add(new ItemRelatorio(
                        "", codigoOriginal, "", 0, "",
                        totalConsumido, totalConsumido, true, "", "", colecaoLabel));
            }
        }
        semPlanejamento.sort(Comparator.comparingDouble(ItemRelatorio::totalModeloSomado).reversed());

        List<ItemRelatorio> nuncaUtilizados = new ArrayList<>();
        for (Map.Entry<String, TecidoPlanejado> entry : mapPlanejado.entrySet()) {
            String codigo = entry.getKey();
            if (!mapUtilizados.containsKey(codigo)) {
                TecidoPlanejado tp = entry.getValue();
                String marcaNorm = String.join(", ", mapLinhas.getOrDefault(codigo, Collections.emptySet()));
                if (marcaNorm.isBlank()) marcaNorm = tp.linha() != null ? tp.linha().trim() : "";
                nuncaUtilizados.add(new ItemRelatorio(
                        tp.modelo(), tp.codigoSystextil(), tp.descricaoSystextil(),
                        tp.totalAprovacaoTecido(), tp.aprovCont(),
                        0, 0, false, "", marcaNorm, colecaoLabel));
            }
        }
        nuncaUtilizados.sort(Comparator.comparing(ItemRelatorio::codigoSystextil));

        return new ResultadoAnalise(excessos, semPlanejamento, nuncaUtilizados);
    }
}
