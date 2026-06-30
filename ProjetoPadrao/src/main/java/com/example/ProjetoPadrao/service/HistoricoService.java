package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ColecaoInfo;
import com.example.ProjetoPadrao.model.ItemAlteracao;
import com.example.ProjetoPadrao.model.AviamentoPlanejado;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistoricoService {

    static class SnapshotDto {
        public String timestamp;
        public List<EntradaDto> aviamentos = new ArrayList<>();
    }

    static class EntradaDto {
        public String codigoNormalizado;
        public String codigoSystextil;
        public String descricao;
        public String modelo;
        public String aprovCont;
        public int total;
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private ColecaoService colecaoService;

    // ── Com coleção ──────────────────────────────────────────────────────────

    public void salvarSnapshot(List<AviamentoPlanejado> planejados, String slug) throws IOException {
        Map<String, AviamentoPlanejado> map = new LinkedHashMap<>();
        for (AviamentoPlanejado tp : planejados) map.putIfAbsent(tp.codigoNormalizado(), tp);

        SnapshotDto snapshot = new SnapshotDto();
        snapshot.timestamp = LocalDateTime.now().format(FMT);
        for (AviamentoPlanejado tp : map.values()) {
            EntradaDto e = new EntradaDto();
            e.codigoNormalizado = tp.codigoNormalizado();
            e.codigoSystextil   = tp.codigoSystextil();
            e.descricao         = tp.descricaoSystextil();
            e.modelo            = tp.modelo();
            e.aprovCont         = tp.aprovCont();
            e.total             = tp.totalAprovacaoAviamento();
            snapshot.aviamentos.add(e);
        }

        List<SnapshotDto> lista = carregarSlug(slug);
        lista.add(snapshot);
        salvarSlug(lista, slug);
    }

    public List<ItemAlteracao> calcularAlteracoes(String slug) throws IOException {
        return calcularDeLista(carregarSlug(slug));
    }

    public List<ItemAlteracao> calcularAlteracoesTodas() throws IOException {
        List<SnapshotDto> todos = new ArrayList<>();
        for (ColecaoInfo c : colecaoService.listarColecoes()) {
            todos.addAll(carregarSlug(c.slug()));
        }
        return calcularDeLista(todos);
    }

    // ── Legado (lê de uploads/ raiz) ─────────────────────────────────────────

    public void salvarSnapshot(List<AviamentoPlanejado> planejados) throws IOException {
        Map<String, AviamentoPlanejado> map = new LinkedHashMap<>();
        for (AviamentoPlanejado tp : planejados) map.putIfAbsent(tp.codigoNormalizado(), tp);

        SnapshotDto snapshot = new SnapshotDto();
        snapshot.timestamp = LocalDateTime.now().format(FMT);
        for (AviamentoPlanejado tp : map.values()) {
            EntradaDto e = new EntradaDto();
            e.codigoNormalizado = tp.codigoNormalizado();
            e.codigoSystextil   = tp.codigoSystextil();
            e.descricao         = tp.descricaoSystextil();
            e.modelo            = tp.modelo();
            e.aprovCont         = tp.aprovCont();
            e.total             = tp.totalAprovacaoAviamento();
            snapshot.aviamentos.add(e);
        }

        List<SnapshotDto> lista = carregarLegado();
        lista.add(snapshot);
        salvarLegado(lista);
    }

    public List<ItemAlteracao> calcularAlteracoes() throws IOException {
        return calcularDeLista(carregarLegado());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<ItemAlteracao> calcularDeLista(List<SnapshotDto> snapshots) {
        if (snapshots.size() < 2) return Collections.emptyList();

        Map<String, List<String>> historicoMap = new LinkedHashMap<>();
        Map<String, String>       codOriginal  = new LinkedHashMap<>();
        Map<String, String>       descricaoMap = new LinkedHashMap<>();
        Map<String, String>       modeloMap    = new LinkedHashMap<>();
        Map<String, String>       aprovContMap = new LinkedHashMap<>();
        Map<String, Integer>      ultimoValor  = new LinkedHashMap<>();

        for (SnapshotDto snap : snapshots) {
            for (EntradaDto e : snap.aviamentos) {
                String cod  = e.codigoNormalizado;
                int    val  = e.total;
                int    prev = ultimoValor.getOrDefault(cod, -1);
                if (prev != val) {
                    historicoMap.computeIfAbsent(cod, ignored -> new ArrayList<>())
                                .add(snap.timestamp + ": " + val);
                    ultimoValor.put(cod, val);
                }
                codOriginal.putIfAbsent(cod, e.codigoSystextil);
                descricaoMap.putIfAbsent(cod, e.descricao);
                if (e.modelo    != null) modeloMap.putIfAbsent(cod, e.modelo);
                if (e.aprovCont != null) aprovContMap.putIfAbsent(cod, e.aprovCont);
            }
        }

        return historicoMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new ItemAlteracao(
                        codOriginal.get(entry.getKey()),
                        descricaoMap.get(entry.getKey()),
                        modeloMap.getOrDefault(entry.getKey(), ""),
                        aprovContMap.getOrDefault(entry.getKey(), ""),
                        ultimoValor.get(entry.getKey()),
                        entry.getValue().size() - 1,
                        entry.getValue()))
                .sorted(Comparator.comparing((ItemAlteracao item) -> {
                    String ultimo = item.historico().get(item.historico().size() - 1);
                    return LocalDateTime.parse(ultimo.substring(0, 16), FMT);
                }).reversed())
                .collect(Collectors.toList());
    }

    private List<SnapshotDto> carregarSlug(String slug) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("historico.json");
        if (!Files.exists(path)) return new ArrayList<>();
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, new TypeReference<List<SnapshotDto>>() {});
        }
    }

    private void salvarSlug(List<SnapshotDto> lista, String slug) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("historico.json");
        Files.createDirectories(path.getParent());
        try (OutputStream os = Files.newOutputStream(path)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(os, lista);
        }
    }

    private List<SnapshotDto> carregarLegado() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "uploads", "historico-planilha1.json");
        if (!Files.exists(path)) return new ArrayList<>();
        try (InputStream is = Files.newInputStream(path)) {
            return mapper.readValue(is, new TypeReference<List<SnapshotDto>>() {});
        }
    }

    private void salvarLegado(List<SnapshotDto> lista) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "uploads", "historico-planilha1.json");
        Files.createDirectories(path.getParent());
        try (OutputStream os = Files.newOutputStream(path)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(os, lista);
        }
    }
}
