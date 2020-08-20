package org.clever.hinny.core;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ConverterKeyBuild;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.Cell;
import com.alibaba.excel.metadata.CellData;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.alibaba.excel.read.builder.ExcelReaderBuilder;
import com.alibaba.excel.read.metadata.holder.ReadHolder;
import com.alibaba.excel.read.metadata.property.ExcelReadHeadProperty;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.AbstractCellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.AbstractVerticalCellStyleStrategy;
import com.alibaba.excel.write.style.column.AbstractHeadColumnWidthStyleStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.clever.common.utils.codec.DigestUtils;
import org.clever.common.utils.codec.EncodeDecodeUtils;
import org.clever.common.utils.excel.ExcelDataReader;
import org.clever.common.utils.excel.ExcelDataWriter;
import org.clever.common.utils.excel.ExcelReaderExceptionHand;
import org.clever.common.utils.excel.ExcelRowReader;
import org.clever.common.utils.excel.dto.ExcelData;
import org.clever.common.utils.excel.dto.ExcelRow;
import org.clever.common.utils.tuples.TupleTow;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/07/28 22:33 <br/>
 */
public class ExcelUtils {
    public static final ExcelUtils Instance = new ExcelUtils();

    private ExcelUtils() {
    }

    @SuppressWarnings("rawtypes")
    @SneakyThrows
    public ExcelDataReader<Map> createReader(ExcelDataReaderConfig config) {
        Assert.notNull(config, "参数config不能为null");
        ExcelDataReader<Map> excelDataReader;
        if (config.getRequest() != null) {
            excelDataReader = new ExcelDataReader<>(
                    config.getRequest(),
                    Map.class,
                    config.limitRows,
                    config.enableExcelData,
                    false,
                    config.excelRowReader,
                    config.excelReaderExceptionHand);
        } else {
            excelDataReader = new ExcelDataReader<>(
                    config.filename,
                    config.inputStream,
                    Map.class,
                    config.limitRows,
                    config.enableExcelData,
                    false,
                    config.excelRowReader,
                    config.excelReaderExceptionHand);
        }
        excelDataReader.setEnableValidation(false);
        ExcelReaderBuilder builder = excelDataReader.read();
        builder.autoCloseStream(config.autoCloseStream);
        if (config.extraRead != null) {
            for (CellExtraTypeEnum typeEnum : config.extraRead) {
                if (typeEnum != null) {
                    builder.extraRead(typeEnum);
                }
            }
        }
        builder.ignoreEmptyRow(config.ignoreEmptyRow);
        builder.mandatoryUseInputStream(config.mandatoryUseInputStream);
        if (config.password != null) {
            builder.password(config.password);
        }
        if (StringUtils.isNotBlank(config.sheetName)) {
            builder.sheet(config.sheetName);
        }
        if (config.sheetNo != null) {
            builder.sheet(config.sheetNo);
        }
        if (config.headRowNumber != null) {
            builder.headRowNumber(config.headRowNumber);
        } else {
            builder.headRowNumber(config.getHeadRowCount());
        }
        builder.useScientificFormat(config.useScientificFormat);
        builder.use1904windowing(config.use1904windowing);
        if (config.locale != null) {
            builder.locale(config.locale);
        }
        builder.autoTrim(config.autoTrim);
        builder.customObject(config.customObject);
        // 自定义解析逻辑
        builder.useDefaultListener(false);
        builder.registerReadListener(new ExcelDateReadListener(config, excelDataReader));
        return excelDataReader;
    }

    public ExcelDataWriter createWriter(ExcelDataWriterConfig config) {
        Assert.notNull(config, "参数config不能为null");
        ExcelDataWriter excelDataWriter;
        if (config.request != null && config.response != null) {
            excelDataWriter = new ExcelDataWriter(config.request, config.response, config.fileName, null);
        } else {
            excelDataWriter = new ExcelDataWriter(config.outputStream, null);
            if (StringUtils.isNotBlank(config.fileName)) {
                excelDataWriter.write().file(config.fileName);
            }
        }
        ExcelWriterBuilder builder = excelDataWriter.write();
        List<List<String>> heads = config.getHeads();
        if (heads.isEmpty() || heads.get(0).isEmpty()) {
            builder.needHead(false);
        } else {
            builder.head(heads);
        }
        builder.autoCloseStream(config.autoCloseStream);
        builder.inMemory(config.inMemory);
        if (StringUtils.isNotBlank(config.template)) {
            builder.withTemplate(config.template);
        }
        if (config.templateInputStream != null) {
            builder.withTemplate(config.templateInputStream);
        }
        builder.automaticMergeHead(config.automaticMergeHead);
        if (!config.excludeColumnFiledNames.isEmpty()) {
            builder.excludeColumnFiledNames(config.excludeColumnFiledNames);
        }
        if (!config.excludeColumnIndexes.isEmpty()) {
            builder.excludeColumnIndexes(config.excludeColumnIndexes);
        }
        if (!config.includeColumnFiledNames.isEmpty()) {
            builder.includeColumnFiledNames(config.includeColumnFiledNames);
        }
        if (!config.includeColumnIndexes.isEmpty()) {
            builder.includeColumnIndexes(config.includeColumnIndexes);
        }
        builder.needHead(config.needHead);
        builder.relativeHeadRowIndex(config.relativeHeadRowIndex);
        builder.useDefaultStyle(config.useDefaultStyle);
        builder.excelType(config.excelType);
        if (config.password != null) {
            builder.password(config.password);
        }
        if (config.sheetNo != null) {
            builder.sheet(config.sheetNo);
        }
        if (StringUtils.isNotBlank(config.sheetName)) {
            builder.sheet(config.sheetName);
        }
        builder.use1904windowing(config.use1904windowing);
        if (config.locale != null) {
            builder.locale(config.locale);
        }
        builder.autoTrim(config.autoTrim);
        // TODO 根据列配置加入各种 WriteHandler 如：AbstractHeadColumnWidthStyleStrategy、AbstractVerticalCellStyleStrategy。参考 AbstractWriteHolder
        builder.registerWriteHandler(new FillHeadStrategy(config));
        builder.registerWriteHandler(new ColumnWidthStyleStrategy());

        // TODO 初始化 registerWriteHandler
//        Map<Integer, Head> headMap = getExcelWriteHeadProperty().getHeadMap();
//        boolean hasColumnWidth = false;
//        boolean hasStyle = false;
//        for (Head head : headMap.values()) {
//            if (head.getColumnWidthProperty() != null) {
//                hasColumnWidth = true;
//            }
//            if (head.getHeadStyleProperty() != null || head.getHeadFontProperty() != null || head.getContentStyleProperty() != null || head.getContentFontProperty() != null) {
//                hasStyle = true;
//            }
//            dealLoopMerge(handlerList, head);
//            // -------------------------------------------------------------------------->>>
//            // LoopMergeProperty loopMergeProperty = head.getLoopMergeProperty();
//            // if (loopMergeProperty == null) {
//            //     return;
//            // }
//            // handlerList.add(new LoopMergeStrategy(loopMergeProperty, head.getColumnIndex()));
//        }
//
//        if (hasColumnWidth) {
//            builder.registerWriteHandler(new ColumnWidthStyleStrategy());
//        }
//        if (hasStyle) {
//            builder.registerWriteHandler(new StyleStrategy());
//        }
//
//        dealRowHigh(handlerList);
//        // -------------------------------------------------------------------------->>>
//        // RowHeightProperty headRowHeightProperty = getExcelWriteHeadProperty().getHeadRowHeightProperty();
//        // RowHeightProperty contentRowHeightProperty = getExcelWriteHeadProperty().getContentRowHeightProperty();
//        // if (headRowHeightProperty == null && contentRowHeightProperty == null) {
//        //     return;
//        // }
//        // Short headRowHeight = null;
//        // if (headRowHeightProperty != null) {
//        //     headRowHeight = headRowHeightProperty.getHeight();
//        // }
//        // Short contentRowHeight = null;
//        // if (contentRowHeightProperty != null) {
//        //     contentRowHeight = contentRowHeightProperty.getHeight();
//        // }
//        // handlerList.add(new SimpleRowHeightStyleStrategy(headRowHeight, contentRowHeight));
//
//        dealOnceAbsoluteMerge(handlerList);
//        // -------------------------------------------------------------------------->>>
//        // OnceAbsoluteMergeProperty onceAbsoluteMergeProperty = getExcelWriteHeadProperty().getOnceAbsoluteMergeProperty();
//        // if (onceAbsoluteMergeProperty == null) {
//        //     return;
//        // }
//        // handlerList.add(new OnceAbsoluteMergeStrategy(onceAbsoluteMergeProperty));
        return excelDataWriter;
    }

    // 配置类
    //----------------------------------------------------------------------------------------------------------------------------------------------

    @Data
    public static class ExcelDataReaderConfig implements Serializable {
        /**
         * Excel文件上传的请求对象
         */
        private HttpServletRequest request;
        /**
         * 上传的Excel文件名称
         */
        private String filename;
        /**
         * 上传的文件数据流
         */
        private InputStream inputStream;
        /**
         * 读取Excel文件最大行数
         */
        private int limitRows = org.clever.common.utils.excel.ExcelDataReader.LIMIT_ROWS;
        /**
         * 是否缓存读取的数据结果到内存中(默认启用)
         */
        private boolean enableExcelData = true;
        /**
         * 是否启用数据校验(默认启用)
         */
        private boolean enableValidation = true;
        /**
         * 处理读取Excel异常
         */
        private ExcelReaderExceptionHand excelReaderExceptionHand;
        /**
         * 处理Excel数据行
         */
        @SuppressWarnings("rawtypes")
        private ExcelRowReader<Map> excelRowReader;

        // ----------------------------------------------------------------------

        /**
         * 是否自动关闭输入流
         */
        private boolean autoCloseStream = false;

        /**
         * 读取扩展信息配置
         */
        private CellExtraTypeEnum[] extraRead = new CellExtraTypeEnum[]{};

        /**
         * 是否忽略空行
         */
        private boolean ignoreEmptyRow = false;

        /**
         * 强制使用输入流，如果为false，则将“inputStream”传输到临时文件以提高效率
         */
        private boolean mandatoryUseInputStream = false;

        /**
         * Excel文件密码
         */
        private String password;

        /**
         * Excel页签编号(从0开始)
         */
        private Integer sheetNo;

        /**
         * Excel页签名称(xlsx格式才支持)
         */
        private String sheetName;

        /**
         * 表头行数
         */
        private Integer headRowNumber;

        /**
         * 使用科学格式
         */
        private boolean useScientificFormat = false;

        /**
         * 如果日期使用1904窗口，则为True；如果使用1900日期窗口，则为false
         */
        private boolean use1904windowing = false;

        /**
         * Locale对象表示特定的地理、政治或文化区域。设置日期和数字格式时使用此参数
         */
        private Locale locale = Locale.SIMPLIFIED_CHINESE;

        /**
         * 自动删除空格字符
         */
        private boolean autoTrim = true;

        /**
         * 设置一个自定义对象，可以在侦听器中读取此对象(AnalysisContext.getCustom())
         */
        private Object customObject;

        /**
         * Excel列配置(表头) {@code Map<Entity.propertyName, ExcelReaderHeadConfig>}
         */
        private final LinkedHashMap<String, ExcelReaderHeadConfig> columns = new LinkedHashMap<>();

        /**
         * 返回表头行数
         */
        public int getHeadRowCount() {
            int headRowCount = 0;
            for (Map.Entry<String, ExcelReaderHeadConfig> entry : columns.entrySet()) {
                ExcelReaderHeadConfig headConfig = entry.getValue();
                if (headConfig != null && headConfig.excelProperty.column.size() > headRowCount) {
                    headRowCount = headConfig.excelProperty.column.size();
                }
            }
            return headRowCount;
        }
    }

    @Data
    public static class ExcelDataWriterConfig implements Serializable {
        /**
         * Excel导出请求对象
         */
        private HttpServletRequest request;
        /**
         * Excel导出响应对象
         */
        private HttpServletResponse response;
        /**
         * Excel导出文件名
         */
        private String fileName;
        /**
         * Excel文件对应输出流
         */
        private OutputStream outputStream;
        /**
         * 是否自动关闭输入流
         */
        private boolean autoCloseStream = false;
        /**
         * 在内存中编写excel。默认为false，则创建缓存文件并最终写入excel。仅在内存模式下支持Comment和RichTextString
         */
        private boolean inMemory = false;
        /**
         * Excel模板文件路径
         */
        private String template;
        /**
         * Excel模板文件输入流
         */
        private InputStream templateInputStream;
        /**
         * 写入Excel时出现异常是否仍然继续导出
         */
        private boolean writeExcelOnException = false;
        /**
         * 是否自动合并表头
         */
        private boolean automaticMergeHead = true;
        /**
         * 忽略自定义列
         */
        private final List<String> excludeColumnFiledNames = new ArrayList<>();
        /**
         * 忽略自定义列
         */
        private final List<Integer> excludeColumnIndexes = new ArrayList<>();
        /**
         * 只输出自定义列
         */
        private final List<String> includeColumnFiledNames = new ArrayList<>();
        /**
         * 只输出自定义列
         */
        private final List<Integer> includeColumnIndexes = new ArrayList<>();
        /**
         * 是否输出表头
         */
        private boolean needHead = true;
        /**
         * 输出第一行的位置
         */
        private int relativeHeadRowIndex = 0;
        /**
         * 是否使用默认样式
         */
        private boolean useDefaultStyle = true;
        /**
         * Excel类型
         */
        private ExcelTypeEnum excelType = ExcelTypeEnum.XLSX;
        /**
         * Excel文件密码
         */
        private String password;
        /**
         * Excel页签编号(从0开始)
         */
        private Integer sheetNo;
        /**
         * Excel页签名称(xlsx格式才支持)
         */
        private String sheetName;
        /**
         * 如果日期使用1904窗口，则为True；如果使用1900日期窗口，则为false
         */
        private boolean use1904windowing = false;
        /**
         * Locale对象表示特定的地理、政治或文化区域。设置日期和数字格式时使用此参数
         */
        private Locale locale = Locale.SIMPLIFIED_CHINESE;
        /**
         * 自动删除空格字符
         */
        private boolean autoTrim = true;
        /**
         * Excel表头 Map<Entity.propertyName, ExcelWriterHeadConfig>
         */
        private final LinkedHashMap<String, ExcelWriterHeadConfig> columns = new LinkedHashMap<>();
        /**
         * 全局样式配置
         */
        private final WriterStyleConfig styleConfig = new WriterStyleConfig();

        public List<List<String>> getHeads() {
            return columns.values().stream()
                    .map(headConfig -> headConfig.excelProperty.column)
                    .collect(Collectors.toList());
        }
    }

    @Data
    public static class ExcelProperty implements Serializable {
        /**
         * 列名称
         */
        private final List<String> column = new ArrayList<>();
        /**
         * 是否忽略当前列
         */
        private Boolean ignore;

        /**
         * 列的索引在列的索引上读写，如果等于-1，则按Java类排序。优先级：index>默认排序
         */
        private Integer index = -1;
    }

    @Data
    public static class DateTimeFormat implements Serializable {
        /**
         * 时间格式化的格式定义
         */
        private String dateFormat;

        /**
         * 如果日期使用1904窗口，则为True；如果使用1900日期窗口，则为false
         */
        private Boolean use1904windowing;
    }

    @Data
    public static class NumberFormat implements Serializable {
        /**
         * 数字格式化
         */
        private String numberFormat;

        /**
         * 四舍五入模式
         */
        private RoundingMode roundingMode;
    }

    @Data
    public static class ColumnWidth implements Serializable {
        /**
         * 列宽
         */
        private Integer columnWidth;
    }

    @Data
    public static class ExcelFontStyle implements Serializable {
        /**
         * 字体的名称（如: Arial）
         */
        private String fontName;

        /**
         * 以熟悉的测量单位表示的高度- points
         */
        private Short fontHeightInPoints;

        /**
         * 是否使用斜体
         */
        private Boolean italic;

        /**
         * 是否在文本中使用删除线水平线
         */
        private Boolean strikeout;

        /**
         * 字体的颜色
         */
        private Short color;

        /**
         * 设置normal、super或subscript
         */
        private Short typeOffset;

        /**
         * 要使用的文本下划线
         */
        private Byte underline;

        /**
         * 设置要使用的字符集
         */
        private Integer charset;

        /**
         * 粗体
         */
        private Boolean bold;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ContentFontStyle extends ExcelFontStyle {
    }

    @Data
    public static class ContentLoopMerge implements Serializable {
        /**
         * 行
         */
        private Integer eachRow;

        /**
         * 列
         */
        private Integer columnExtend;
    }

    @Data
    public static class ContentRowHeight implements Serializable {
        /**
         * 行高
         */
        private Short rowHeight;
    }

    @Data
    public static class ExcelCellStyle implements Serializable {
        /**
         * 设置数据格式（必须是有效格式）。内置格式在内置信息中定义 {@link BuiltinFormats}.
         */
        private Short dataFormat;

        /**
         * 将单元格使用此样式设置为隐藏
         */
        private Boolean hidden;

        /**
         * 将单元格使用此样式设置为锁定
         */
        private Boolean locked;

        /**
         * 打开或关闭样式的“Quote Prefix”或“123 Prefix”，
         * 用于告诉Excel，看起来像数字或公式的内容不应被视为打开。
         * 打开此选项有点（但不是完全打开，请参见IgnoredErrorType）类似于在Excel中为单元格值添加前缀
         * {@link IgnoredErrorType})
         */
        private Boolean quotePrefix;

        /**
         * 设置单元格的水平对齐方式
         */
        private HorizontalAlignment horizontalAlignment;

        /**
         * 设置是否应该换行。将此标志设置为true可以通过在多行上显示所有内容来使其在一个单元格中可见
         */
        private Boolean wrapped;

        /**
         * 设置单元格的垂直对齐方式
         */
        private VerticalAlignment verticalAlignment;

        /**
         * 设置单元格中文本的旋转度<br />
         * 注意：HSSF使用-90至90度的值，而XSSF使用0至180度的值。
         * 此方法的实现将在这两个值范围之间进行映射，
         * 但是，相应的getter返回此CellStyle所应用的当前Excel文件格式类型所要求的范围内的值。
         */
        private Short rotation;

        /**
         * 设置空格数以缩进单元格中的文本
         */
        private Short indent;

        /**
         * 设置要用于单元格左边框的边框类型
         */
        private BorderStyle borderLeft;

        /**
         * 设置用于单元格右边框的边框类型
         */
        private BorderStyle borderRight;

        /**
         * 设置要用于单元格顶部边框的边框类型
         */
        private BorderStyle borderTop;

        /**
         * 设置用于单元格底部边框的边框类型
         */
        private BorderStyle borderBottom;

        /**
         * 设置用于左边框的颜色
         *
         * @see IndexedColors
         */
        private Short leftBorderColor;

        /**
         * 设置用于右边框的颜色
         *
         * @see IndexedColors
         */
        private short rightBorderColor;

        /**
         * 设置要用于顶部边框的颜色
         *
         * @see IndexedColors
         */
        private Short topBorderColor;

        /**
         * 设置用于底边框的颜色
         *
         * @see IndexedColors
         */
        private Short bottomBorderColor;

        /**
         * 设置为1会使单元格充满前景色...不知道其他值
         *
         * @see FillPatternType#SOLID_FOREGROUND
         */
        private FillPatternType fillPatternType;

        /**
         * 设置背景填充颜色
         *
         * @see IndexedColors
         */
        private Short fillBackgroundColor;

        /**
         * 设置前景色填充颜色<br />
         * 注意：确保将前景色设置为背景颜色之前
         *
         * @see IndexedColors
         */
        private Short fillForegroundColor;

        /**
         * 控制如果文本太长，是否应自动调整单元格的大小以缩小以适合
         */
        private Boolean shrinkToFit;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ContentStyle extends ExcelCellStyle {
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class HeadFontStyle extends ExcelFontStyle {
    }

    @Data
    public static class HeadRowHeight implements Serializable {
        /**
         * Head行高
         */
        private Short headRowHeight;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class HeadStyle extends ExcelCellStyle {
    }

    @Data
    public static class OnceAbsoluteMerge implements Serializable {
        /**
         * 第一行
         */
        private Integer firstRowIndex;

        /**
         * 最后一行
         */
        private Integer lastRowIndex;

        /**
         * 第一列
         */
        private Integer firstColumnIndex;

        /**
         * 最后一列
         */
        private Integer lastColumnIndex;
    }

    @Data
    public static class ExcelReaderHeadConfig implements Serializable {
        /**
         * 列的数据类型
         */
        private Class<?> dataType;
        private final ExcelProperty excelProperty = new ExcelProperty();
        private final DateTimeFormat dateTimeFormat = new DateTimeFormat();
        private final NumberFormat numberFormat = new NumberFormat();
        
        public ExcelReaderHeadConfig() {
        }

        public ExcelReaderHeadConfig(Class<?> dataType, String... names) {
            this.dataType = dataType;
            if (names != null) {
                this.excelProperty.column.addAll(Arrays.asList(names));
            }
        }
    }

    @Data
    public static class ExcelWriterHeadConfig implements Serializable {
        private final ExcelProperty excelProperty = new ExcelProperty();
        private final DateTimeFormat dateTimeFormat = new DateTimeFormat();
        private final NumberFormat numberFormat = new NumberFormat();
        private final ColumnWidth columnWidth = new ColumnWidth();
        private final ContentFontStyle contentFontStyle = new ContentFontStyle();
        private final ContentStyle contentStyle = new ContentStyle();
        private final HeadFontStyle headFontStyle = new HeadFontStyle();
        private final HeadStyle headStyle = new HeadStyle();

        public ExcelWriterHeadConfig() {
        }

        public ExcelWriterHeadConfig(String... names) {
            if (names != null) {
                this.excelProperty.column.addAll(Arrays.asList(names));
            }
        }
    }

    @Data
    public static class WriterStyleConfig implements Serializable {
        private final ContentFontStyle contentFontStyle = new ContentFontStyle();
        private final ContentRowHeight contentRowHeight = new ContentRowHeight();
        private final ContentStyle contentStyle = new ContentStyle();
        private final HeadFontStyle headFontStyle = new HeadFontStyle();
        private final HeadRowHeight headRowHeight = new HeadRowHeight();
        private final HeadStyle headStyle = new HeadStyle();
        private final OnceAbsoluteMerge onceAbsoluteMerge = new OnceAbsoluteMerge();
    }

    // 自定义读取、写入操作
    //----------------------------------------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    @Slf4j
    private static class ExcelDateReadListener extends AnalysisEventListener<Map<Integer, CellData<?>>> {
        private final ExcelDataReaderConfig config;
        private final ExcelDataReader<Map> excelDataReader;
        /**
         * {@code Map<index, TupleTow<type, Entity.propertyName>>}
         */
        private final Map<Integer, TupleTow<Class<?>, String>> columns = new HashMap<>();

        public ExcelDateReadListener(ExcelDataReaderConfig config, ExcelDataReader<Map> excelDataReader) {
            Assert.notNull(config, "参数config不能为null");
            Assert.notNull(excelDataReader, "参数excelDataReader不能为null");
            this.config = config;
            this.excelDataReader = excelDataReader;
        }

        private ExcelData<Map> getExcelData(AnalysisContext context) {
            final Integer sheetNo = context.readSheetHolder().getSheetNo();
            final String sheetName = context.readSheetHolder().getSheetName();
            String key = String.format("%s-%s", sheetNo, sheetName);
            return excelDataReader.getExcelSheetMap().computeIfAbsent(key, s -> new ExcelData<>(Map.class, sheetName, sheetNo));
        }

        private Class<?> getCellDataType(CellData<?> cellData) {
            if (cellData.getType() == null) {
                return Void.class;
            }
            switch (cellData.getType()) {
                case NUMBER:
                    return BigDecimal.class;
                case BOOLEAN:
                    return Boolean.class;
                case DIRECT_STRING:
                case STRING:
                case ERROR:
                    return String.class;
                case IMAGE:
                    return Byte[].class;
                default:
                    return Void.class;
            }
        }

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            ExcelData<Map> excelData = getExcelData(context);
            if (excelData.getStartTime() == null) {
                excelData.setStartTime(System.currentTimeMillis());
            }
            LinkedHashMap<String, ExcelReaderHeadConfig> columnsConfig = config.columns;
            // TODO 根据 index | column 确定列字段对应关系
            for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                Integer index = entry.getKey();
                String head = entry.getValue();
                Class<?> clazz = null;
                if (!columnsConfig.isEmpty()) {
                    ExcelReaderHeadConfig headConfig = columnsConfig.get(head);
                    if (headConfig == null) {
                        continue;
                    }
                    clazz = headConfig.dataType;
                }
                columns.put(index, TupleTow.creat(clazz, head));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void invoke(Map<Integer, CellData<?>> data, AnalysisContext context) {
            ExcelData<Map> excelData = getExcelData(context);
            if (excelData.getStartTime() == null) {
                excelData.setStartTime(System.currentTimeMillis());
            }
            int index = context.readRowHolder().getRowIndex() + 1;
            ExcelRow<Map> excelRow = new ExcelRow<>(new HashMap(data.size()), index);
            // 数据签名-防重机制
            Map<Integer, Cell> map = context.readRowHolder().getCellMap();
            StringBuilder sb = new StringBuilder(map.size() * 32);
            for (Map.Entry<Integer, Cell> entry : map.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue().toString()).append("|");
            }
            excelRow.setDataSignature(EncodeDecodeUtils.encodeHex(DigestUtils.sha1(sb.toString().getBytes())));
            // 读取数据需要类型转换
            ReadHolder currentReadHolder = context.currentReadHolder();
            ExcelReadHeadProperty excelReadHeadProperty = context.currentReadHolder().excelReadHeadProperty();
            Map<Integer, ExcelContentProperty> contentPropertyMap = excelReadHeadProperty.getContentPropertyMap();
            for (Map.Entry<Integer, CellData<?>> entry : data.entrySet()) {
                Integer idx = entry.getKey();
                CellData<?> cellData = entry.getValue();
                TupleTow<Class<?>, String> tupleTow = columns.get(idx);
                if (tupleTow.getValue1() == null) {
                    tupleTow.setValue1(getCellDataType(cellData));
                }
                Object value;
                if (Objects.equals(Void.class, tupleTow.getValue1())) {
                    value = "";
                } else {
                    ExcelContentProperty excelContentProperty = contentPropertyMap.get(index);
                    value = ConverterUtils.convertToJavaObject(
                            cellData,
                            tupleTow.getValue1(),
                            excelContentProperty,
                            currentReadHolder.converterMap(),
                            currentReadHolder.globalConfiguration(),
                            context.readRowHolder().getRowIndex(), index);
                }
                excelRow.getData().put(tupleTow.getValue2(), value);
            }
            boolean success = true;
            final boolean enableExcelData = config.isEnableExcelData();
            if (enableExcelData) {
                success = excelData.addRow(excelRow);
            }
            if (!success) {
                log.info("Excel数据导入数据重复，filename={} | data={}", config.getFilename(), data);
            }
            // 数据校验
            //final boolean enableValidation = config.isEnableValidation();
            //if (enableValidation && !excelRow.hasError()) {
            //    // TODO 数据校验
            //}
            // 自定义读取行处理逻辑
            final ExcelRowReader<Map> excelRowReader = config.getExcelRowReader();
            if (!excelRow.hasError() && excelRowReader != null) {
                try {
                    excelRowReader.readRow(data, excelRow, context);
                } catch (Throwable e) {
                    excelRow.addErrorInRow(e.getMessage());
                }
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            ExcelData<Map> excelData = getExcelData(context);
            if (excelData.getEndTime() == null) {
                excelData.setEndTime(System.currentTimeMillis());
            }
            if (excelData.getEndTime() != null && excelData.getStartTime() != null) {
                log.info("Excel Sheet读取完成，sheet={} | 耗时：{}ms", excelData.getSheetName(), excelData.getEndTime() - excelData.getStartTime());
            }
            ExcelRowReader<Map> excelRowReader = config.getExcelRowReader();
            if (excelRowReader != null) {
                excelRowReader.readEnd(context);
            }
            // if (!enableExcelData) {
            //     excelData.setStartTime(null);
            //     excelData.setEndTime(null);
            // }
        }

        @Override
        public void onException(Exception exception, AnalysisContext context) throws Exception {
            ExcelReaderExceptionHand excelReaderExceptionHand = config.getExcelReaderExceptionHand();
            if (excelReaderExceptionHand != null) {
                excelReaderExceptionHand.exceptionHand(exception, context);
            } else {
                // 默认的异常处理
                throw exception;
            }
        }

        @Override
        public boolean hasNext(AnalysisContext context) {
            // 未配置列 - 提前退出
            if (context.readSheetHolder().getHeadRowNumber() > 0 && columns.isEmpty()) {
                log.warn("未匹配到列配置");
                return false;

            }
            final ExcelData<Map> excelData = getExcelData(context);
            // 是否重复读取
            if (excelData.getEndTime() != null && excelData.getStartTime() != null) {
                log.info("Excel Sheet已经读取完成，当前跳过，sheet={}", excelData.getSheetName());
                return false;
            }
            // 数据是否超出限制 LIMIT_ROWS
            final int limitRows = config.getLimitRows();
            final int rowNum = context.readRowHolder().getRowIndex() + 1;
            final int dataRowNum = rowNum - context.currentReadHolder().excelReadHeadProperty().getHeadRowNumber();
            if (limitRows > 0 && dataRowNum > limitRows) {
                log.info("Excel数据行超出限制：dataRowNum={} | limitRows={}", dataRowNum, limitRows);
                excelData.setInterruptByRowNum(rowNum);
                // 设置已经读取完成
                doAfterAllAnalysed(context);
                return false;
            }
            return true;
        }
    }

    public static class ConverterUtils {
        private ConverterUtils() {
        }

        @SuppressWarnings("rawtypes")
        public static Object convertToJavaObject(
                CellData<?> cellData,
                Class<?> clazz,
                ExcelContentProperty contentProperty,
                Map<String, Converter> converterMap,
                GlobalConfiguration globalConfiguration,
                Integer rowIndex,
                Integer columnIndex) {
            if (clazz == null) {
                clazz = String.class;
            }
            if (Objects.equals(cellData.getType(), CellDataTypeEnum.EMPTY)) {
                if (Objects.equals(String.class, clazz)) {
                    return StringUtils.EMPTY;
                } else {
                    return null;
                }
            }
            Converter<?> converter = null;
            if (contentProperty != null) {
                converter = contentProperty.getConverter();
            }
            if (converter == null) {
                converter = converterMap.get(ConverterKeyBuild.buildKey(clazz, cellData.getType()));
            }
            if (converter == null) {
                throw new ExcelDataConvertException(rowIndex, columnIndex, cellData, contentProperty, "Converter not found, convert " + cellData.getType() + " to " + clazz.getName());
            }
            try {
                return converter.convertToJavaData(cellData, contentProperty, globalConfiguration);
            } catch (Exception e) {
                throw new ExcelDataConvertException(rowIndex, columnIndex, cellData, contentProperty, "Convert data " + cellData + " to " + clazz + " error ", e);
            }
        }
    }

    @Slf4j
    private static class FillHeadStrategy extends AbstractCellWriteHandler {
        private final ExcelDataWriterConfig config;
        private final Map<Integer, Boolean> filledMap = new HashMap<>();

        public FillHeadStrategy(ExcelDataWriterConfig config) {
            Assert.notNull(config, "参数config不能为null");
            this.config = config;
        }

        @Override
        public void beforeCellCreate(
                WriteSheetHolder writeSheetHolder,
                WriteTableHolder writeTableHolder,
                Row row,
                Head head,
                Integer columnIndex,
                Integer relativeRowIndex,
                Boolean isHead) {
            boolean filled = filledMap.computeIfAbsent(columnIndex, idx -> false);
            if (filled) {
                return;
            }
            filledMap.put(columnIndex, true);
            Collection<ExcelWriterHeadConfig> columns = config.columns.values();
            if (columns.isEmpty() || columns.size() <= columnIndex) {
                return;
            }
//            ExcelWriterHeadConfig excelWriterHeadConfig = columns.get(columnIndex);
//            if (excelWriterHeadConfig == null) {
//                return;
//            }
//            if (excelWriterHeadConfig.columnWidth.columnWidth != null) {
//                head.setColumnWidthProperty(new ColumnWidthProperty(excelWriterHeadConfig.columnWidth.columnWidth));
//            }


            // writeContext.currentWriteHolder().excelWriteHeadProperty().getIgnoreMap()
        }
    }

    private static class ColumnWidthStyleStrategy extends AbstractHeadColumnWidthStyleStrategy {
        @Override
        protected Integer columnWidth(Head head, Integer columnIndex) {
            if (head == null) {
                return null;
            }
            if (head.getColumnWidthProperty() != null) {
                return head.getColumnWidthProperty().getWidth();
            }
            return null;
        }
    }

    private static class StyleStrategy extends AbstractVerticalCellStyleStrategy {
        @Override
        protected WriteCellStyle headCellStyle(Head head) {
            return WriteCellStyle.build(head.getHeadStyleProperty(), head.getHeadFontProperty());
        }

        @Override
        protected WriteCellStyle contentCellStyle(Head head) {
            return WriteCellStyle.build(head.getContentStyleProperty(), head.getContentFontProperty());
        }
    }

//    @Slf4j
//    private static class ColumnStyleStrategy extends AbstractCellWriteHandler implements NotRepeatExecutor {
//        private final ExcelDataWriterConfig config;
//
//        public ColumnStyleStrategy(ExcelDataWriterConfig config) {
//            Assert.notNull(config, "参数config不能为null");
//            this.config = config;
//        }
//
//        @Override
//        public String uniqueValue() {
//            return "hinny-core-ColumnStyleStrategy";
//        }
//
//        int count = 0;
//
//
//        @Override
//        public void afterCellDispose(
//                WriteSheetHolder writeSheetHolder,
//                WriteTableHolder writeTableHolder,
//                List<CellData> cellDataList,
//                org.apache.poi.ss.usermodel.Cell cell,
//                Head head,
//                Integer relativeRowIndex,
//                Boolean isHead) {
//            log.info("--> {}", (++count));
//            writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), 60 * 256);
//        }
//    }
}
