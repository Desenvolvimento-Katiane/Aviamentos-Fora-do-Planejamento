package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ItemAlteracao;
import com.example.ProjetoPadrao.model.TecidoPlanejado;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
        public List<EntradaDto> tecidos = new ArrayList<>();
    }

    static class EntradaDto {
        public String codigoNormalizado;
        public String codigoSystextil;
        public String descricao;
        public int total;
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void salvarSnapshot(List<TecidoPlanejado> planejados) throws IOException {
        Map<String, TecidoPlanejado> map = new LinkedHashMap<>();
        for (TecidoPlanejado tp : planejados) {
            map.putIfAbsent(tp.codigoNormalizado(), tp);
        }

        SnapshotDto snapshot = new SnapshotDto();
        snapshot.timestamp = LocalDateTime.now().format(FMT);
        for (TecidoPlanejado tp : map.values()) {
            EntradaDto e = new EntradaDto();
            e.codigoNormalizado = tp.codigoNormalizado();
            e.codigoSystextil   = tp.codigoSystextil();
            e.descricao         = tp.descricaoSystextil();
            e.total             = tp.totalAprovacaoTecido();
            snapshot.tecidos.add(e);
        }

        List<SnapshotDto> lista = carregar();
        lista.add(snapshot);
        salvar(lista);
    }

    public List<ItemAlteracao> calcularAlteracoes() throws IOException {
        List<SnapshotDto> snapshots = carregar();
        if (snapshots.size() < 2) return Collections.emptyList();

        Map<String, List<String>> historicoMap  = new LinkedHashMap<>();
        Map<String, String>       codOriginal   = new LinkedHashMap<>();
        Map<String, String>       descricaoMap  = new LinkedHashMap<>();
        Map<String, Integer>      ultimoValor   = new LinkedHashMap<>();

        for (SnapshotDto snap : snapshots) {
            for (EntradaDto e : snap.tecidos) {
                String cod  = e.codigoNormalizado;
                int    val  = e.total;
                int    prev = ultimoValor.getOrDefault(cod, -1);
                if (prev != val) {
                    historicoMap.computeIfAbsent(cod, k -> new ArrayList<>())
                                .add(snap.timestamp + ": " + val);
                    ultimoValor.put(cod, val);
                }
                codOriginal.putIfAbsent(cod, e.codigoSystextil);
                descricaoMap.putIfAbsent(cod, e.descricao);
            }
        }

        return historicoMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new ItemAlteracao(
                        codOriginal.get(entry.getKey()),
                        descricaoMap.get(entry.getKey()),
                        ultimoValor.get(entry.getKey()),
                        entry.getValue().size() - 1,
                        entry.getValue()))
                .sorted(Comparator.comparingInt(ItemAlteracao::totalAlteracoes).reversed())
                .collect(Collectors.toList());
    }

    private List<SnapshotDto> carregar() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "uploads", "historico-planilha1.json");
        if (!Files.exists(path)) return new ArrayList<>();
        return mapper.readValue(path.toFile(), new TypeReference<List<SnapshotDto>>() {});
    }

    private void salvar(List<SnapshotDto> lista) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "uploads", "historico-planilha1.json");
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), lista);
    }
}
