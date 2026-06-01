package com.example.ProjetoPadrao.controller;

import com.example.ProjetoPadrao.model.ColecaoInfo;
import com.example.ProjetoPadrao.model.ItemAlteracao;
import com.example.ProjetoPadrao.model.ItemRelatorio;
import com.example.ProjetoPadrao.model.ResultadoAnalise;
import com.example.ProjetoPadrao.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TecidoController {

    @Autowired private ExcelService excelService;
    @Autowired private AnaliseService analiseService;
    @Autowired private ObservacaoService observacaoService;
    @Autowired private HistoricoService historicoService;
    @Autowired private ColecaoService colecaoService;

    private static final DateTimeFormatter FMT_CARD = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String TODAS = "todas";

    private Path flagPath(String slug) {
        return colecaoService.getColecaoDir(slug).resolve("analisado.flag");
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String colecao, Model model) {

        List<ColecaoInfo> colecoes = colecaoService.listarColecoes();
        model.addAttribute("colecoes", colecoes);

        // Resolver coleção ativa
        String slugAtivo = colecao;
        if (slugAtivo == null || slugAtivo.isBlank()) slugAtivo = colecaoService.getColecaoAtual();
        if (slugAtivo == null && !colecoes.isEmpty()) slugAtivo = colecoes.get(0).slug();

        boolean modoTodas = TODAS.equals(slugAtivo);

        ColecaoInfo colecaoAtual = null;
        if (!modoTodas && slugAtivo != null) {
            String slug = slugAtivo;
            colecaoAtual = colecoes.stream().filter(c -> c.slug().equals(slug)).findFirst().orElse(null);
        }
        model.addAttribute("colecaoAtual", colecaoAtual);
        model.addAttribute("modoTodas", modoTodas);

        if (colecaoAtual != null) {
            model.addAttribute("p1Existe", colecaoAtual.p1Existe());
            model.addAttribute("p2Existe", colecaoAtual.p2Existe());
            model.addAttribute("p3Existe", colecaoAtual.p3Existe());

            Path dir = colecaoService.getColecaoDir(colecaoAtual.slug());
            for (String[] entry : new String[][]{
                    {"planilha1.xlsx", "dataP1"},
                    {"planilha2.xlsx", "dataP2"},
                    {"planilha3.xlsx", "dataP3"}}) {
                Path f = dir.resolve(entry[0]);
                try {
                    if (Files.exists(f)) {
                        LocalDateTime dt = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(f).toInstant(), java.time.ZoneId.systemDefault());
                        model.addAttribute(entry[1], dt.format(FMT_CARD));
                    }
                } catch (IOException ignored) {}
            }

            model.addAttribute("ultimaAtualizacao", colecaoAtual.dataUpload());

            Map<String, String> observacoes = new LinkedHashMap<>();
            try { observacoes = observacaoService.carregar(colecaoAtual.slug()); } catch (IOException ignored) {}
            model.addAttribute("observacoes", observacoes);

            List<ItemAlteracao> historicoAlteracoes = Collections.emptyList();
            try {
                historicoAlteracoes = historicoService.calcularAlteracoes(colecaoAtual.slug());
            } catch (IOException ignored) {}
            model.addAttribute("historicoAlteracoes", historicoAlteracoes);

            if (colecaoAtual.p1Existe() && colecaoAtual.p2Existe()) {
                try {
                    List<ItemRelatorio> resultadoPL = analiseService.analisarPecasLiberadas(colecaoAtual.slug());
                    model.addAttribute("resultadoPL", resultadoPL);
                    model.addAttribute("totalPL", resultadoPL.size());
                } catch (IOException e) {
                    model.addAttribute("erroPL", "Erro ao carregar Peças Liberadas: " + e.getMessage());
                }
            }

            if (Files.exists(flagPath(colecaoAtual.slug()))) {
                try {
                    ResultadoAnalise resultado = analiseService.analisar(colecaoAtual.slug());
                    model.addAttribute("resultado", resultado);
                    model.addAttribute("totalNuncaUtilizados", resultado.nuncaUtilizados().size());
                } catch (IOException e) {
                    model.addAttribute("erro", "Erro ao carregar análise: " + e.getMessage());
                }

                if (colecaoAtual.p3Existe()) {
                    try {
                        ResultadoAnalise resultadoCC = analiseService.analisarColecaoCompleta(colecaoAtual.slug());
                        model.addAttribute("resultadoCC", resultadoCC);
                        model.addAttribute("totalExcessosCC", resultadoCC.excessos().size());
                        model.addAttribute("totalNuncaUtilizadosCC", resultadoCC.nuncaUtilizados().size());
                        model.addAttribute("marcasGraficoExcessosCC", buildMarcasMap(resultadoCC.excessos(), true));
                        model.addAttribute("marcasGraficoNuncaCC", buildMarcasMap(resultadoCC.nuncaUtilizados(), false));
                        Map<String, Double> p3Counts = analiseService.contarReferenciasP3(colecaoAtual.slug());
                        Map<String, Double> utilizadoMap = new LinkedHashMap<>();
                        for (ItemAlteracao item : historicoAlteracoes) {
                            if (item.codigoSystextil() != null) {
                                String normKey = item.codigoSystextil().trim().toLowerCase();
                                utilizadoMap.put(item.codigoSystextil(), p3Counts.getOrDefault(normKey, 0.0));
                            }
                        }
                        model.addAttribute("utilizadoMap", utilizadoMap);
                    } catch (IOException e) {
                        model.addAttribute("erroCC", "Erro ao carregar Coleção Completa: " + e.getMessage());
                    }
                }
            }

        } else if (modoTodas) {
            model.addAttribute("p1Existe", true);
            model.addAttribute("p2Existe", true);
            model.addAttribute("p3Existe", true);
            model.addAttribute("historicoAlteracoes", Collections.emptyList());
            model.addAttribute("observacoes", Collections.emptyMap());

            try {
                ResultadoAnalise resultado = analiseService.analisarTodas();
                model.addAttribute("resultado", resultado);
                model.addAttribute("totalNuncaUtilizados", resultado.nuncaUtilizados().size());
            } catch (IOException e) {
                model.addAttribute("erro", "Erro ao carregar análise: " + e.getMessage());
            }

            try {
                List<ItemRelatorio> resultadoPL = analiseService.analisarPecasLiberadasTodas();
                model.addAttribute("resultadoPL", resultadoPL);
                model.addAttribute("totalPL", resultadoPL.size());
            } catch (Exception ignored) {}

            try {
                ResultadoAnalise resultadoCC = analiseService.analisarColecaoCompletaTodas();
                model.addAttribute("resultadoCC", resultadoCC);
                model.addAttribute("totalExcessosCC", resultadoCC.excessos().size());
                model.addAttribute("totalNuncaUtilizadosCC", resultadoCC.nuncaUtilizados().size());
                model.addAttribute("marcasGraficoExcessosCC", buildMarcasMap(resultadoCC.excessos(), true));
                model.addAttribute("marcasGraficoNuncaCC", buildMarcasMap(resultadoCC.nuncaUtilizados(), false));
                model.addAttribute("utilizadoMap", analiseService.contarReferenciasP3Todas());
            } catch (IOException ignored) {}

        } else {
            model.addAttribute("p1Existe", false);
            model.addAttribute("p2Existe", false);
            model.addAttribute("p3Existe", false);
            model.addAttribute("historicoAlteracoes", Collections.emptyList());
            model.addAttribute("observacoes", Collections.emptyMap());
        }

        model.addAttribute("marcasValidas", analiseService.getMarcasValidas());
        return "index";
    }

    @PostMapping("/analisar")
    public String analisar(
            @RequestParam(value = "planilha1", required = false) MultipartFile p1,
            @RequestParam(value = "planilha2", required = false) MultipartFile p2,
            @RequestParam(value = "planilha3", required = false) MultipartFile p3,
            RedirectAttributes redirectAttributes) {

        try {
            // Detectar coleção
            String nomeColecao = null;
            MultipartFile fonte = p1 != null && !p1.isEmpty() ? p1
                    : p2 != null && !p2.isEmpty() ? p2
                    : p3 != null && !p3.isEmpty() ? p3 : null;
            if (fonte != null) nomeColecao = excelService.lerColecaoDoArquivo(fonte);
            if (nomeColecao == null || nomeColecao.isBlank()) {
                redirectAttributes.addFlashAttribute("erro",
                        "Coluna 'Coleção / Descrição' não encontrada nas planilhas. Verifique os arquivos.");
                redirectAttributes.addFlashAttribute("stayOnUpload", true);
                return "redirect:/";
            }

            String slug = colecaoService.resolverSlug(nomeColecao);
            String agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));

            boolean algumEnviado = false;
            if (p1 != null && !p1.isEmpty()) {
                excelService.salvarArquivoColecao(p1, slug, "planilha1.xlsx");
                algumEnviado = true;
            }
            if (p2 != null && !p2.isEmpty()) {
                excelService.salvarArquivoColecao(p2, slug, "planilha2.xlsx");
                algumEnviado = true;
            }
            if (p3 != null && !p3.isEmpty()) {
                excelService.salvarArquivoColecao(p3, slug, "planilha3.xlsx");
                algumEnviado = true;
            }

            if (algumEnviado) {
                colecaoService.salvarMetadata(slug, nomeColecao, agora);
                colecaoService.setColecaoAtual(slug);

                if (excelService.arquivoExisteColecao(slug, "planilha1.xlsx")) {
                    try {
                        historicoService.salvarSnapshot(excelService.lerPlanilha1(slug), slug);
                    } catch (IOException ignored) {}
                }

                if (excelService.arquivoExisteColecao(slug, "planilha1.xlsx")
                        && excelService.arquivoExisteColecao(slug, "planilha3.xlsx")) {
                    try {
                        analiseService.analisar(slug);
                        Files.createDirectories(flagPath(slug).getParent());
                        Files.writeString(flagPath(slug), LocalDateTime.now().toString());
                    } catch (IOException e) {
                        redirectAttributes.addFlashAttribute("erro", "Erro ao analisar: " + e.getMessage());
                        return "redirect:/?colecao=" + slug;
                    }
                }

                redirectAttributes.addFlashAttribute("sucesso",
                        "Planilha enviada com sucesso para a coleção \"" + nomeColecao + "\".");
                redirectAttributes.addFlashAttribute("stayOnUpload", true);
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar arquivo: " + e.getMessage());
            return "redirect:/";
        }

        String slug = colecaoService.getColecaoAtual();
        return "redirect:/" + (slug != null ? "?colecao=" + slug : "");
    }

    @PostMapping("/observacao")
    @ResponseBody
    public ResponseEntity<Void> salvarObservacao(
            @RequestParam String codigo,
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "") String colecao) {
        try {
            if (!colecao.isBlank()) {
                observacaoService.salvar(colecao, codigo, texto);
            } else {
                observacaoService.salvar(codigo, texto);
            }
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/exportar")
    public void exportar(@RequestParam(required = false) String colecao,
                         @RequestParam(required = false) String marca,
                         HttpServletResponse response) throws IOException {
        ResultadoAnalise resultado;
        Map<String, String> observacoes = new LinkedHashMap<>();

        if (TODAS.equals(colecao)) {
            resultado = analiseService.analisarTodas();
        } else {
            String slug = colecao != null ? colecao : colecaoService.getColecaoAtual();
            if (slug == null || !excelService.arquivoExisteColecao(slug, "planilha1.xlsx")) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Planilhas não encontradas.");
                return;
            }
            resultado = analiseService.analisar(slug);
            try { observacoes = observacaoService.carregar(slug); } catch (IOException ignored) {}
        }

        if (marca != null && !marca.isBlank()) resultado = filtrarPorMarca(resultado, marca.trim());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"relatorio-tecidos.xlsx\"");
        excelService.gerarExcel(resultado.excessos(), resultado.semPlanejamento(),
                resultado.nuncaUtilizados(), observacoes, response.getOutputStream());
    }

    @GetMapping("/exportar-colecao-completa")
    public void exportarColecaoCompleta(@RequestParam(required = false) String colecao,
                                        @RequestParam(required = false) String marca,
                                        HttpServletResponse response) throws IOException {
        ResultadoAnalise resultado;
        Map<String, String> observacoes = new LinkedHashMap<>();

        if (TODAS.equals(colecao)) {
            resultado = analiseService.analisarColecaoCompletaTodas();
        } else {
            String slug = colecao != null ? colecao : colecaoService.getColecaoAtual();
            if (slug == null || !excelService.arquivoExisteColecao(slug, "planilha1.xlsx")) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Planilhas não encontradas.");
                return;
            }
            resultado = analiseService.analisarColecaoCompleta(slug);
            try { observacoes = observacaoService.carregar(slug); } catch (IOException ignored) {}
        }

        if (marca != null && !marca.isBlank()) resultado = filtrarPorMarca(resultado, marca.trim());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"relatorio-colecao-completa.xlsx\"");
        excelService.gerarExcel(resultado.excessos(), resultado.semPlanejamento(),
                resultado.nuncaUtilizados(), observacoes, response.getOutputStream());
    }

    @GetMapping("/exportar-pecas-liberadas")
    public void exportarPecasLiberadas(@RequestParam(required = false) String colecao,
                                       HttpServletResponse response) throws IOException {
        List<ItemRelatorio> resultado;
        if (TODAS.equals(colecao)) {
            resultado = analiseService.analisarPecasLiberadasTodas();
        } else {
            String slug = colecao != null ? colecao : colecaoService.getColecaoAtual();
            if (slug == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            resultado = analiseService.analisarPecasLiberadas(slug);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"pecas-liberadas.xlsx\"");
        excelService.gerarExcelPecasLiberadas(resultado, response.getOutputStream());
    }

    private ResultadoAnalise filtrarPorMarca(ResultadoAnalise original, String marca) {
        List<ItemRelatorio> excessos = original.excessos().stream()
                .filter(i -> contemMarca(i.marcas(), marca))
                .collect(Collectors.toList());
        List<ItemRelatorio> nuncaUtil = original.nuncaUtilizados().stream()
                .filter(i -> contemMarca(i.linha(), marca))
                .collect(Collectors.toList());
        return new ResultadoAnalise(excessos, original.semPlanejamento(), nuncaUtil);
    }

    private boolean contemMarca(String campo, String marca) {
        if (campo == null || campo.isBlank()) return false;
        for (String m : campo.split(",")) {
            if (m.trim().equalsIgnoreCase(marca)) return true;
        }
        return false;
    }

    private Map<String, Integer> buildMarcasMap(List<ItemRelatorio> itens, boolean usarMarcas) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (ItemRelatorio item : itens) {
            String campo = usarMarcas ? item.marcas() : item.linha();
            if (campo != null && !campo.isBlank()) {
                for (String m : campo.split(",")) {
                    String marca = m.trim();
                    if (!marca.isEmpty()) raw.merge(marca, 1, Integer::sum);
                }
            }
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
