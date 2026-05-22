package com.example.ProjetoPadrao.controller;

import com.example.ProjetoPadrao.model.ItemAlteracao;
import com.example.ProjetoPadrao.model.ItemRelatorio;
import com.example.ProjetoPadrao.model.ResultadoAnalise;
import com.example.ProjetoPadrao.service.AnaliseService;
import com.example.ProjetoPadrao.service.ExcelService;
import com.example.ProjetoPadrao.service.HistoricoService;
import com.example.ProjetoPadrao.service.ObservacaoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class TecidoController {

    @Autowired
    private ExcelService excelService;

    @Autowired
    private AnaliseService analiseService;

    @Autowired
    private ObservacaoService observacaoService;

    @Autowired
    private HistoricoService historicoService;

    private static final DateTimeFormatter FMT_CARD = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Path flagPath() {
        return Paths.get(System.getProperty("user.dir"), "uploads", "analisado.flag");
    }

    @GetMapping("/")
    public String index(Model model) {
        boolean p1Existe = excelService.arquivoExiste("planilha1.xlsx");
        boolean p2Existe = excelService.arquivoExiste("planilha2.xlsx");
        boolean p3Existe = excelService.arquivoExiste("planilha3.xlsx");
        model.addAttribute("p1Existe", p1Existe);
        model.addAttribute("p2Existe", p2Existe);
        model.addAttribute("p3Existe", p3Existe);

        // Data de última atualização por planilha
        Path up = Paths.get(System.getProperty("user.dir"), "uploads");
        String[][] planilhas = {
            {"planilha1.xlsx", "dataP1"},
            {"planilha2.xlsx", "dataP2"},
            {"planilha3.xlsx", "dataP3"}
        };
        for (String[] entry : planilhas) {
            Path f = up.resolve(entry[0]);
            try {
                if (Files.exists(f)) {
                    LocalDateTime dt = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(f).toInstant(), java.time.ZoneId.systemDefault());
                    model.addAttribute(entry[1], dt.format(FMT_CARD));
                }
            } catch (IOException ignored) {}
        }

        Map<String, String> observacoes = new LinkedHashMap<>();
        try { observacoes = observacaoService.carregar(); } catch (IOException ignored) {}
        model.addAttribute("observacoes", observacoes);

        // Data de última atualização geral
        try {
            Path tsPath = up.resolve("ultima-atualizacao.txt");
            if (Files.exists(tsPath)) {
                model.addAttribute("ultimaAtualizacao", Files.readString(tsPath).trim());
            }
        } catch (IOException ignored) {}

        model.addAttribute("marcasValidas", analiseService.getMarcasValidas());

        try {
            model.addAttribute("historicoAlteracoes", historicoService.calcularAlteracoes());
        } catch (IOException ignored) {
            model.addAttribute("historicoAlteracoes", Collections.emptyList());
        }

        // Análise só roda se o flag existir
        if (Files.exists(flagPath())) {
            try {
                ResultadoAnalise resultado = analiseService.analisar();
                model.addAttribute("resultado", resultado);
                model.addAttribute("totalExcessos", resultado.excessos().size());
                model.addAttribute("totalNuncaUtilizados", resultado.nuncaUtilizados().size());

                model.addAttribute("marcasGraficoExcessos", buildMarcasMap(resultado.excessos(), true));
                model.addAttribute("marcasGraficoNunca", buildMarcasMap(resultado.nuncaUtilizados(), false));
            } catch (IOException e) {
                model.addAttribute("erro", "Erro ao carregar análise: " + e.getMessage());
            }
        }

        return "index";
    }

    @PostMapping("/analisar")
    public String analisar(
            @RequestParam(value = "planilha1", required = false) MultipartFile p1,
            @RequestParam(value = "planilha2", required = false) MultipartFile p2,
            @RequestParam(value = "planilha3", required = false) MultipartFile p3,
            RedirectAttributes redirectAttributes) {

        try {
            boolean algumEnviado = false;
            if (p1 != null && !p1.isEmpty()) {
                excelService.salvarArquivo(p1, "planilha1.xlsx");
                try { historicoService.salvarSnapshot(excelService.lerPlanilha1()); } catch (IOException ignored) {}
                algumEnviado = true;
            }
            if (p2 != null && !p2.isEmpty()) {
                excelService.salvarArquivo(p2, "planilha2.xlsx");
                algumEnviado = true;
            }
            if (p3 != null && !p3.isEmpty()) {
                excelService.salvarArquivo(p3, "planilha3.xlsx");
                algumEnviado = true;
            }
            if (algumEnviado) {
                String agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
                try {
                    Path tsPath = Paths.get(System.getProperty("user.dir"), "uploads", "ultima-atualizacao.txt");
                    Files.createDirectories(tsPath.getParent());
                    Files.writeString(tsPath, agora);
                } catch (IOException ignored) {}

                // Auto-análise quando todas as 3 planilhas existem
                if (excelService.arquivoExiste("planilha1.xlsx")
                        && excelService.arquivoExiste("planilha2.xlsx")
                        && excelService.arquivoExiste("planilha3.xlsx")) {
                    try {
                        analiseService.analisar();
                        Files.createDirectories(flagPath().getParent());
                        Files.writeString(flagPath(), LocalDateTime.now().toString());
                    } catch (IOException e) {
                        redirectAttributes.addFlashAttribute("erro", "Erro ao analisar: " + e.getMessage());
                        return "redirect:/";
                    }
                }

                redirectAttributes.addFlashAttribute("sucesso", "Planilha enviada com sucesso.");
                redirectAttributes.addFlashAttribute("stayOnUpload", true);
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar arquivo: " + e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/observacao")
    @ResponseBody
    public ResponseEntity<Void> salvarObservacao(
            @RequestParam String codigo,
            @RequestParam(defaultValue = "") String texto) {
        try {
            observacaoService.salvar(codigo, texto);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/exportar")
    public void exportar(HttpServletResponse response) throws IOException {
        if (!excelService.arquivoExiste("planilha1.xlsx") || !excelService.arquivoExiste("planilha2.xlsx")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Planilhas não encontradas. Faça o upload primeiro.");
            return;
        }

        ResultadoAnalise resultado = analiseService.analisar();
        Map<String, String> observacoes = new LinkedHashMap<>();
        try { observacoes = observacaoService.carregar(); } catch (IOException ignored) {}

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"relatorio-tecidos.xlsx\"");
        excelService.gerarExcel(resultado.excessos(), resultado.semPlanejamento(),
                resultado.nuncaUtilizados(), observacoes, response.getOutputStream());
    }

    private Map<String, Integer> buildMarcasMap(List<ItemRelatorio> itens, boolean usarMarcas) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (ItemRelatorio item : itens) {
            if (usarMarcas) {
                if (item.marcas() != null && !item.marcas().isBlank()) {
                    for (String m : item.marcas().split(",")) {
                        String marca = m.trim();
                        if (!marca.isEmpty()) raw.merge(marca, 1, Integer::sum);
                    }
                }
            } else {
                if (item.linha() != null && !item.linha().isBlank()) {
                    for (String m : item.linha().split(",")) {
                        String marca = m.trim();
                        if (!marca.isEmpty()) raw.merge(marca, 1, Integer::sum);
                    }
                }
            }
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
