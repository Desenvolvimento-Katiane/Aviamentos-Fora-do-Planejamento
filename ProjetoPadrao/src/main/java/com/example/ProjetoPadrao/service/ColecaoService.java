package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ColecaoInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

@Service
public class ColecaoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final ObjectMapper mapper = new ObjectMapper();

    private Path getRootUploadPath() {
        return Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    public Path getColecaoDir(String slug) {
        return getRootUploadPath().resolve("colecoes").resolve(slug);
    }

    public String resolverSlug(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) return "sem-colecao";
        String sem = Normalizer.normalize(nomeOriginal.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return sem.isBlank() ? "sem-colecao" : sem;
    }

    public String getColecaoAtual() {
        Path p = getRootUploadPath().resolve("colecao-atual.txt");
        try {
            if (Files.exists(p)) return Files.readString(p).trim();
        } catch (IOException ignored) {}
        return null;
    }

    public void setColecaoAtual(String slug) throws IOException {
        Path p = getRootUploadPath().resolve("colecao-atual.txt");
        Files.createDirectories(p.getParent());
        Files.writeString(p, slug);
    }

    public List<ColecaoInfo> listarColecoes() {
        Path dir = getRootUploadPath().resolve("colecoes");
        List<ColecaoInfo> lista = new ArrayList<>();
        if (!Files.exists(dir)) return lista;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                String slug = entry.getFileName().toString();
                String nomeOriginal = slug;
                String dataUpload = "";
                Path meta = entry.resolve("metadata.json");
                if (Files.exists(meta)) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = mapper.readValue(meta.toFile(), Map.class);
                        nomeOriginal = (String) m.getOrDefault("nomeOriginal", slug);
                        dataUpload   = (String) m.getOrDefault("dataUpload", "");
                    } catch (IOException ignored) {}
                }
                lista.add(new ColecaoInfo(
                        slug, nomeOriginal, dataUpload,
                        Files.exists(entry.resolve("planilha1.xlsx")),
                        Files.exists(entry.resolve("planilha2.xlsx")),
                        Files.exists(entry.resolve("planilha3.xlsx"))
                ));
            }
        } catch (IOException ignored) {}
        lista.sort(Comparator.comparing(ColecaoInfo::dataUpload).reversed());
        return lista;
    }

    public void salvarMetadata(String slug, String nomeOriginal, String dataUpload) throws IOException {
        Path dir = getColecaoDir(slug);
        Files.createDirectories(dir);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("nomeOriginal", nomeOriginal);
        meta.put("dataUpload", dataUpload);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("metadata.json").toFile(), meta);
    }

    public Optional<ColecaoInfo> buscarColecao(String slug) {
        return listarColecoes().stream().filter(c -> c.slug().equals(slug)).findFirst();
    }
}
