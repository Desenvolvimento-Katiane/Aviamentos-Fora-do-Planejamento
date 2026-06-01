package com.example.ProjetoPadrao.service;

import com.example.ProjetoPadrao.model.ItemRelatorio;
import com.example.ProjetoPadrao.model.TecidoPlanejado;
import com.example.ProjetoPadrao.model.TecidoUtilizado;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.*;
import java.util.Map;

@Service
public class ExcelService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Autowired
    private ColecaoService colecaoService;

    private Path getUploadPath() {
        return Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    public boolean arquivoExiste(String nome) {
        return Files.exists(getUploadPath().resolve(nome));
    }

    public boolean arquivoExisteColecao(String slug, String nome) {
        return Files.exists(colecaoService.getColecaoDir(slug).resolve(nome));
    }

    public void salvarArquivo(MultipartFile file, String nomeDestino) throws IOException {
        Path dir = getUploadPath();
        Files.createDirectories(dir);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, dir.resolve(nomeDestino), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void salvarArquivoColecao(MultipartFile file, String slug, String nomeDestino) throws IOException {
        Path dir = colecaoService.getColecaoDir(slug);
        Files.createDirectories(dir);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, dir.resolve(nomeDestino), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public String lerColecaoDoArquivo(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, "codigo systextil");
            if (headerRowIndex < 0) return null;
            Map<String, Integer> headers = mapHeaders(sheet.getRow(headerRowIndex));
            int colColecao = -1;
            for (Map.Entry<String, Integer> e : headers.entrySet()) {
                if (e.getKey().contains("colecao")) { colColecao = e.getValue(); break; }
            }
            if (colColecao < 0) return null;
            for (int i = headerRowIndex + 1; i <= Math.min(headerRowIndex + 10, sheet.getLastRowNum()); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String val = readStringCell(row.getCell(colColecao), evaluator);
                if (!val.isBlank()) return val.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<TecidoPlanejado> lerPlanilha1() throws IOException {
        return lerPlanilha1FromPath(getUploadPath().resolve("planilha1.xlsx"));
    }

    public List<TecidoUtilizado> lerPlanilha2() throws IOException {
        return lerPlanilhaUtilizadoFromPath(getUploadPath().resolve("planilha2.xlsx"), "Planilha 2");
    }

    public List<TecidoUtilizado> lerPlanilha3() throws IOException {
        return lerPlanilhaUtilizadoFromPath(getUploadPath().resolve("planilha3.xlsx"), "Planilha 3");
    }

    public List<TecidoPlanejado> lerPlanilha1(String slug) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("planilha1.xlsx");
        return lerPlanilha1FromPath(path);
    }

    public List<TecidoUtilizado> lerPlanilha2(String slug) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("planilha2.xlsx");
        return lerPlanilhaUtilizadoFromPath(path, "Planilha 2");
    }

    public List<TecidoUtilizado> lerPlanilha3(String slug) throws IOException {
        Path path = colecaoService.getColecaoDir(slug).resolve("planilha3.xlsx");
        return lerPlanilhaUtilizadoFromPath(path, "Planilha 3");
    }

    private List<TecidoPlanejado> lerPlanilha1FromPath(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, "codigo systextil");
            if (headerRowIndex < 0)
                throw new IOException("Cabeçalho 'Código Systêxtil' não encontrado na Planilha 1. Verifique o arquivo.");
            Map<String, Integer> headers = mapHeaders(sheet.getRow(headerRowIndex));
            int colModelo    = getColIndex(headers, "modelo");
            int colCodigo    = getColIndex(headers, "codigo systextil");
            int colDescricao = getColIndex(headers, "descricao systextil");
            int colTotal     = getColIndex(headers, "qtd aprov");
            if (colTotal < 0) colTotal = getColIndex(headers, "total aprovacao tecido");
            int colAprov     = getColIndex(headers, "aprov/cont");
            int colLinha     = getColIndex(headers, "linha");
            List<TecidoPlanejado> list = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String codigo = readStringCell(row.getCell(colCodigo), evaluator);
                if (codigo.isBlank()) continue;
                list.add(new TecidoPlanejado(
                        colModelo >= 0    ? readStringCell(row.getCell(colModelo), evaluator)    : "",
                        codigo,
                        normalizarCodigo(codigo),
                        colDescricao >= 0 ? readStringCell(row.getCell(colDescricao), evaluator) : "",
                        colTotal >= 0     ? readIntCell(row.getCell(colTotal), evaluator)        : 0,
                        colAprov >= 0     ? readStringCell(row.getCell(colAprov), evaluator)     : "",
                        colLinha >= 0     ? readStringCell(row.getCell(colLinha), evaluator)     : ""
                ));
            }
            return list;
        }
    }

    private List<TecidoUtilizado> lerPlanilhaUtilizadoFromPath(Path path, String nome) throws IOException {
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, "codigo systextil");
            if (headerRowIndex < 0)
                throw new IOException("Cabeçalho 'Código Systêxtil' não encontrado na " + nome + ". Verifique o arquivo.");
            Map<String, Integer> headers = mapHeaders(sheet.getRow(headerRowIndex));
            int colModelo  = getColIndex(headers, "modelo");
            int colMarca   = getColIndex(headers, "marca");
            int colCodigo  = getColIndex(headers, "codigo systextil");
            int colConsumo = getColIndex(headers, "consumo");
            List<TecidoUtilizado> list = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String codigo = readStringCell(row.getCell(colCodigo), evaluator);
                if (codigo.isBlank()) continue;
                double consumo = colConsumo >= 0 ? readDoubleCell(row.getCell(colConsumo), evaluator) : 0.0;
                list.add(new TecidoUtilizado(
                        colModelo >= 0 ? readStringCell(row.getCell(colModelo), evaluator) : "",
                        colMarca >= 0  ? readStringCell(row.getCell(colMarca), evaluator)  : "",
                        codigo,
                        normalizarCodigo(codigo),
                        consumo
                ));
            }
            return list;
        }
    }

    public void gerarExcelPecasLiberadas(List<ItemRelatorio> itens, OutputStream os) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle rowStyle    = createExcessStyle(wb);

            Sheet sheet = wb.createSheet("Peças Liberadas");
            String[] headers = {"Código Systêxtil", "Descrição Systêxtil",
                                 "Qtd Aprov", "Consumo Total", "Diferença", "Marca", "Coleção"};
            createHeaderRow(sheet, headers, headerStyle);

            int rowNum = 1;
            for (ItemRelatorio item : itens) {
                Row r = sheet.createRow(rowNum++);
                for (int c = 0; c < 7; c++) r.createCell(c).setCellStyle(rowStyle);
                r.getCell(0).setCellValue(item.codigoSystextil());
                r.getCell(1).setCellValue(item.descricaoSystextil());
                r.getCell(2).setCellValue(item.totalAprovacaoTecido());
                r.getCell(3).setCellValue(item.totalModeloSomado());
                r.getCell(4).setCellValue("+" + item.diferenca());
                r.getCell(5).setCellValue(item.marcas());
                r.getCell(6).setCellValue(item.colecao());
            }
            autoSizeColumns(sheet, 7);
            wb.write(os);
        }
    }

    public void gerarExcel(List<ItemRelatorio> excessos, List<ItemRelatorio> semPlanejamento,
                           List<ItemRelatorio> nuncaUtilizados, Map<String, String> observacoes,
                           OutputStream os) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle excessStyle = createExcessStyle(wb);
            CellStyle nuncaStyle  = createNuncaUtilStyle(wb);

            // Aba 1: Nunca Utilizados
            Sheet sheet3 = wb.createSheet("Nunca Utilizados");
            String[] headers3 = {"Modelo", "Código Systêxtil", "Descrição Systêxtil",
                                  "Total Aprovado", "Aprov/Cont", "Linha", "Observação"};
            createHeaderRow(sheet3, headers3, headerStyle);

            int rowNum = 1;
            for (ItemRelatorio item : nuncaUtilizados) {
                Row r = sheet3.createRow(rowNum++);
                for (int c = 0; c < 7; c++) r.createCell(c).setCellStyle(nuncaStyle);
                r.getCell(0).setCellValue(item.modelo());
                r.getCell(1).setCellValue(item.codigoSystextil());
                r.getCell(2).setCellValue(item.descricaoSystextil());
                r.getCell(3).setCellValue(item.totalAprovacaoTecido());
                r.getCell(4).setCellValue(item.aprovCont());
                r.getCell(5).setCellValue(item.linha());
                r.getCell(6).setCellValue(observacoes.getOrDefault(item.codigoSystextil(), ""));
            }
            autoSizeColumns(sheet3, 7);

            wb.write(os);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static String normalizarCodigo(String codigo) {
        if (codigo == null) return "";
        return codigo.trim().toLowerCase();
    }

    private static String normalizarHeader(String header) {
        if (header == null) return "";
        return Normalizer.normalize(header.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private int findHeaderRow(Sheet sheet, String targetNorm) {
        int maxSearch = Math.min(5, sheet.getLastRowNum());
        for (int i = 0; i <= maxSearch; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                if (normalizarHeader(getCellRawString(cell)).contains(targetNorm)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            String norm = normalizarHeader(getCellRawString(cell));
            if (!norm.isBlank()) map.put(norm, cell.getColumnIndex());
        }
        return map;
    }

    private int getColIndex(Map<String, Integer> headers, String target) {
        if (headers.containsKey(target)) return headers.get(target);
        for (Map.Entry<String, Integer> e : headers.entrySet()) {
            if (e.getKey().contains(target) || target.contains(e.getKey())) return e.getValue();
        }
        return -1;
    }

    private String getCellRawString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default      -> "";
        };
    }

    private String readStringCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                CellValue cv = evaluator.evaluate(cell);
                return switch (cv.getCellType()) {
                    case STRING  -> cv.getStringValue().trim();
                    case NUMERIC -> formatNumeric(cv.getNumberValue());
                    case BOOLEAN -> String.valueOf(cv.getBooleanValue());
                    default      -> "";
                };
            } catch (Exception e) {
                return "";
            }
        }
        return switch (type) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private double readDoubleCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return 0.0;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                CellValue cv = evaluator.evaluate(cell);
                return cv.getCellType() == CellType.NUMERIC ? cv.getNumberValue() : 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        }
        return switch (type) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> {
                try { yield Double.parseDouble(cell.getStringCellValue().trim()); }
                catch (NumberFormatException ex) { yield 0.0; }
            }
            default -> 0.0;
        };
    }

    private int readIntCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return 0;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            try {
                CellValue cv = evaluator.evaluate(cell);
                return cv.getCellType() == CellType.NUMERIC ? (int) cv.getNumberValue() : 0;
            } catch (Exception e) {
                return 0;
            }
        }
        return switch (type) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING  -> {
                try { yield Integer.parseInt(cell.getStringCellValue().trim()); }
                catch (NumberFormatException ex) { yield 0; }
            }
            default -> 0;
        };
    }

    private String formatNumeric(double v) {
        return (v == Math.floor(v) && !Double.isInfinite(v))
                ? String.valueOf((long) v)
                : String.valueOf(v);
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void autoSizeColumns(Sheet sheet, int num) {
        for (int i = 0; i < num; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createExcessStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createNuncaUtilStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
