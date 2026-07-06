package com.example.ProjetoPadrao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarcaService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final List<String> PADRAO = List.of(
        "Animê Kids", "Animê Petite", "Animê Bebê",
        "Momi Kids",  "Momi Bebê",   "Momi Mini",
        "Authoria",   "Youccie",
        "Bimbi Menina", "Bimbi Menino"
    );

    private List<String> cache;

    private Path getPath() {
        return Paths.get(System.getProperty("user.dir"), uploadDir, "marcas.txt");
    }

    public synchronized List<String> listar() {
        if (cache != null) return cache;
        Path path = getPath();
        try {
            if (Files.exists(path)) {
                List<String> lidas = new ArrayList<>();
                for (String linha : Files.readAllLines(path)) {
                    String m = linha.trim();
                    if (!m.isBlank()) lidas.add(m);
                }
                cache = lidas;
            } else {
                cache = new ArrayList<>(PADRAO);
                salvar(cache);
            }
        } catch (IOException e) {
            cache = new ArrayList<>(PADRAO);
        }
        return cache;
    }

    public synchronized void adicionar(String marca) throws IOException {
        if (marca == null || marca.isBlank()) return;
        String limpa = marca.trim();
        List<String> atuais = new ArrayList<>(listar());
        String limpaNorm = normalizar(limpa);
        boolean existe = atuais.stream().anyMatch(m -> normalizar(m).equals(limpaNorm));
        if (!existe) {
            atuais.add(limpa);
            salvar(atuais);
            cache = atuais;
        }
    }

    public synchronized void remover(String marca) throws IOException {
        if (marca == null) return;
        List<String> atuais = new ArrayList<>(listar());
        atuais.removeIf(m -> m.equals(marca));
        salvar(atuais);
        cache = atuais;
    }

    private void salvar(List<String> lista) throws IOException {
        Path path = getPath();
        Files.createDirectories(path.getParent());
        Files.write(path, lista);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}
