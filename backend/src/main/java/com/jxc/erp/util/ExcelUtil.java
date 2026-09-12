package com.jxc.erp.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Excel 读写工具（Apache POI 5.x，纯 JDK API）。导入逐行读 / 导出 SXSSF 流式 / 模板带下拉 */
public class ExcelUtil {

    /** 读取 Excel 首个工作表 → 行数据（跳过空行），最多 maxRows 行（不含表头？含，从第 1 行起） */
    public static List<List<String>> readRows(InputStream in, int maxRows) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return rows;
            }
            int last = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + maxRows - 1);
            for (int i = sheet.getFirstRowNum(); i <= last; i++) {
                Row r = sheet.getRow(i);
                if (r == null) {
                    continue;
                }
                List<String> row = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < r.getLastCellNum(); c++) {
                    String v = cellStr(r.getCell(c)).trim();
                    row.add(v);
                    if (!v.isEmpty()) {
                        any = true;
                    }
                }
                if (any) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * 构建 xlsx 字节流。
     *
     * @param headers       表头
     * @param rows          数据行（每行长度与 headers 一致，缺列填空）
     * @param columnOptions 列索引 → 下拉选项（模板的 select 列用）
     */
    public static byte[] toXlsx(List<String> headers, List<List<String>> rows,
                                Map<Integer, List<String>> columnOptions) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("data");
            Row hr = sheet.createRow(0);
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(bold);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headStyle);
                int width = Math.min(30 * 256, Math.max(10 * 256, (headers.get(c).length() + 2) * 256));
                sheet.setColumnWidth(c, width);
            }
            int ri = 1;
            for (List<String> row : rows) {
                Row r = sheet.createRow(ri++);
                for (int c = 0; c < headers.size(); c++) {
                    r.createCell(c).setCellValue(row.size() > c ? row.get(c) : "");
                }
            }
            if (columnOptions != null) {
                for (var e : columnOptions.entrySet()) {
                    List<String> opts = e.getValue();
                    if (opts == null || opts.isEmpty()) {
                        continue;
                    }
                    String formula = String.join(",", opts);
                    if (formula.length() > 200) {
                        continue; // 下拉公式有长度上限，超限跳过（列仍可手动填）
                    }
                    DataValidationHelper helper = sheet.getDataValidationHelper();
                    DataValidationConstraint constraint = helper.createFormulaListConstraint("\"" + formula + "\"");
                    CellRangeAddressList range = new CellRangeAddressList(1, 2000, e.getKey(), e.getKey());
                    DataValidation dv = helper.createValidation(constraint, range);
                    dv.setSuppressDropDownArrow(true);
                    dv.setShowErrorBox(false);
                    sheet.addValidationData(dv);
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private static String cellStr(Cell cell) {
        if (cell == null) {
            return "";
        }
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                        : trimNumber(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> String.valueOf(cell.getNumericCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static String trimNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
