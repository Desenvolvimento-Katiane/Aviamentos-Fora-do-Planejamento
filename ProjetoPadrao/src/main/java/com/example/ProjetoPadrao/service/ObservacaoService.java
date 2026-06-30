package com.example.ProjetoPadrao.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ObservacaoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Autowired
    private ColecaoService colecaoService;

    // ── Com coleção ──────────────────────────────────────────────────────────

    public Map<String, String> carregar(String slug) throws IOException {
        return carregarDePath(colecaoService.getColecaoDir(slug).resolve("observacoes.txt"));
    }

    public void salvar(String slug, String codigo, String texto) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("observacoes.txt");
        salvarNaPath(path, codigo, texto);
    }

    // ── Legado ───────────────────────────────────────────────────────────────

    public Map<String, String> carregar() throws IOException {
        return carregarDePath(getPathLegado());
    }

    public void salvar(String codigo, String texto) throws IOException {
        salvarNaPath(getPathLegado(), codigo, texto);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Path getPathLegado() {
        return Paths.get(System.getProperty("user.dir"), uploadDir, "observacoes.txt");
    }

    private Map<String, String> carregarDePath(Path p) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(p)) return map;
        for (String line : Files.readAllLines(p)) {
            int sep = line.indexOf('=');
            if (sep > 0) map.put(line.substring(0, sep), line.substring(sep + 1));
        }
        return map;
    }

    private void salvarNaPath(Path path, String codigo, String texto) throws IOException {
        Files.createDirectories(path.getParent());
        Map<String, String> map = carregarDePath(path);
        if (texto == null || texto.isBlank()) {
            map.remove(codigo);
        } else {
            map.put(codigo, texto.trim());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue());
        }
        Files.write(path, lines);
    }
}
