package org.clever.data.jdbc;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.clever.common.model.request.QueryByPage;
import org.clever.common.model.request.QueryBySort;
import org.clever.common.utils.tuples.TupleTow;
import org.clever.data.common.AbstractDataSource;
import org.clever.data.jdbc.support.*;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Jdbc 数据库操作封装
 * <p>
 * 作者：lizw <br/>
 * 创建时间：2020/07/08 20:55 <br/>
 */
@Slf4j
public class JdbcDataSource extends AbstractDataSource {
    /**
     * 分页时最大的页大小
     */
    private static final int Max_Page_Size = 1000;
    /**
     * 设置游标读取数据时，单批次的数据读取量(值不能太大也不能太小)
     */
    private static final int Fetch_Size = 500;
    /**
     * 事务名称前缀
     */
    private static final String Transaction_Name_Prefix = "TX";

    /**
     * 数据库类型
     */
    private final DbType dbType;
    /**
     * 数据源
     */
    private final DataSource dataSource;
    /**
     * JDBC API操作
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /**
     * 事务序列号
     */
    private final AtomicInteger transactionSerialNumber = new AtomicInteger(0);
    /**
     * 数据源管理器
     */
    private final DataSourceTransactionManager transactionManager;

    /**
     * 使用Hikari连接池配置初始化数据源，创建对象
     *
     * @param hikariConfig Hikari连接池配置
     */
    public JdbcDataSource(HikariConfig hikariConfig) {
        Assert.notNull(hikariConfig, "HikariConfig不能为空");
        this.dataSource = new HikariDataSource(hikariConfig);
        this.jdbcTemplate = new NamedParameterJdbcTemplate(this.dataSource);
        this.jdbcTemplate.getJdbcTemplate().setFetchSize(Fetch_Size);
        this.dbType = getDbType();
        this.transactionManager = new DataSourceTransactionManager(this.dataSource);
        initCheck();
    }

    /**
     * 使用DataSource创建对象
     *
     * @param dataSource 数据源
     */
    public JdbcDataSource(DataSource dataSource) {
        Assert.notNull(dataSource, "DataSource不能为空");
        this.dataSource = dataSource;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(this.dataSource);
        this.jdbcTemplate.getJdbcTemplate().setFetchSize(Fetch_Size);
        this.dbType = getDbType();
        this.transactionManager = new DataSourceTransactionManager(this.dataSource);
        initCheck();
    }

    /**
     * 使用JdbcTemplate创建对象
     */
    public JdbcDataSource(JdbcTemplate jdbcTemplate) {
        Assert.notNull(jdbcTemplate, "JdbcTemplate不能为空");
        this.dataSource = jdbcTemplate.getDataSource();
        Assert.notNull(this.dataSource, "DataSource不能为空");
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.jdbcTemplate.getJdbcTemplate().setFetchSize(Fetch_Size);
        this.dbType = getDbType();
        this.transactionManager = new DataSourceTransactionManager(this.dataSource);
        initCheck();
    }

    /**
     * 使用JdbcTemplate创建对象
     */
    public JdbcDataSource(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        Assert.notNull(namedParameterJdbcTemplate, "NamedParameterJdbcTemplate不能为空");
        this.dataSource = namedParameterJdbcTemplate.getJdbcTemplate().getDataSource();
        Assert.notNull(this.dataSource, "DataSource不能为空");
        this.jdbcTemplate = namedParameterJdbcTemplate;
        this.jdbcTemplate.getJdbcTemplate().setFetchSize(Fetch_Size);
        this.dbType = getDbType();
        this.transactionManager = new DataSourceTransactionManager(this.dataSource);
        initCheck();
    }

    /**
     * 校验数据源是否可用
     */
    private void initCheck() {
        Assert.notNull(dbType, "DbType不能为空");
    }

    /**
     * 获取数据库类型
     */
    public DbType getDbType() {
        if (this.dbType != null) {
            return this.dbType;
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return JdbcUtils.getDbType(connection.getMetaData().getURL());
        } catch (Throwable e) {
            throw new RuntimeException("读取数据库类型失败", e);
        } finally {
            if (connection != null) {
                org.springframework.jdbc.support.JdbcUtils.closeConnection(connection);
            }
        }
    }

    /**
     * 查询一条数据，返回一个Map
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public Map<String, Object> queryMap(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Map<String, Object> res = jdbcTemplate.queryForObject(sql, paramMap, new ColumnMapRowMapper());
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询一条数据，返回一个Map
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public Map<String, Object> queryMap(String sql) {
        return queryMap(sql, Collections.emptyMap());
    }

    /**
     * 查询多条数据，返回一个Map数组
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public List<Map<String, Object>> queryList(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        List<Map<String, Object>> resList = jdbcTemplate.queryForList(sql, paramMap);
        SqlLoggerUtils.printfTotal(resList);
        return resList;
    }

    /**
     * 查询多条数据，返回一个Map数组
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public List<Map<String, Object>> queryList(String sql) {
        return queryList(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 String
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public String queryString(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        String res = jdbcTemplate.queryForObject(sql, paramMap, String.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询返回一个 String
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public String queryString(String sql) {
        return queryString(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 Long
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public Long queryLong(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Long res = jdbcTemplate.queryForObject(sql, paramMap, Long.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询返回一个 Long
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public Long queryLong(String sql) {
        return queryLong(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 Double
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public Double queryDouble(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Double res = jdbcTemplate.queryForObject(sql, paramMap, Double.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }


    /**
     * 查询返回一个 Double
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public Double queryDouble(String sql) {
        return queryDouble(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 BigDecimal
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public BigDecimal queryBigDecimal(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        BigDecimal res = jdbcTemplate.queryForObject(sql, paramMap, BigDecimal.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询返回一个 BigDecimal
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public BigDecimal queryBigDecimal(String sql) {
        return queryBigDecimal(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 Boolean
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public Boolean queryBoolean(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Boolean res = jdbcTemplate.queryForObject(sql, paramMap, Boolean.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询返回一个 Boolean
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public Boolean queryBoolean(String sql) {
        return queryBoolean(sql, Collections.emptyMap());
    }

    /**
     * 查询返回一个 Date
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public Date queryDate(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Date res = jdbcTemplate.queryForObject(sql, paramMap, Date.class);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 查询返回一个 Date
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public Date queryDate(String sql) {
        return queryDate(sql, Collections.emptyMap());
    }

    /**
     * SQL Count(获取一个SQL返回的数据总量)
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public long queryCount(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        String countSql = SqlUtils.getCountSql(sql);
        countSql = StringUtils.trim(countSql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        Long total = jdbcTemplate.queryForObject(countSql, paramMap, Long.class);
        if (total == null) {
            total = 0L;
        }
        SqlLoggerUtils.printfTotal(total);
        return total;
    }

    /**
     * 查询多条数据(大量数据)，使用游标读取
     *
     * @param sql       sql脚本，参数格式[:param]
     * @param paramMap  参数(可选)，参数格式[:param]
     * @param batchSize 一个批次的数据量
     * @param consumer  游标批次读取数据消费者
     */
    public void query(String sql, Map<String, Object> paramMap, int batchSize, Consumer<BatchData> consumer) {
        Assert.hasText(sql, "sql不能为空");
        Assert.notNull(consumer, "数据消费者不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        final BatchDataReaderCallback batchDataReaderCallback = new BatchDataReaderCallback(batchSize, consumer);
        if (paramMap == null) {
            jdbcTemplate.query(sql, batchDataReaderCallback);
        } else {
            jdbcTemplate.query(sql, paramMap, batchDataReaderCallback);
        }
        batchDataReaderCallback.processEnd();
        SqlLoggerUtils.printfTotal(batchDataReaderCallback.getColumnCount());
    }

    /**
     * 查询多条数据(大量数据)，使用游标读取
     *
     * @param sql       sql脚本，参数格式[:param]
     * @param batchSize 一个批次的数据量
     * @param consumer  游标批次读取数据消费者
     */
    public void query(String sql, int batchSize, Consumer<BatchData> consumer) {
        query(sql, null, batchSize, consumer);
    }

    /**
     * 查询多条数据(大量数据)，使用游标读取
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     * @param consumer 游标读取数据消费者
     */
    public void query(String sql, Map<String, Object> paramMap, Consumer<RowData> consumer) {
        Assert.hasText(sql, "sql不能为空");
        Assert.notNull(consumer, "数据消费者不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        final RowDataReaderCallback rowDataReaderCallback = new RowDataReaderCallback(consumer);
        if (paramMap == null) {
            jdbcTemplate.query(sql, rowDataReaderCallback);
        } else {
            jdbcTemplate.query(sql, paramMap, rowDataReaderCallback);
        }
        SqlLoggerUtils.printfTotal(rowDataReaderCallback.getColumnCount());
    }

    /**
     * 查询多条数据(大量数据)，使用游标读取
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param consumer 游标读取数据消费者
     */
    public void query(String sql, Consumer<RowData> consumer) {
        query(sql, null, consumer);
    }

    /**
     * 执行更新SQL，返回更新影响数据量
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public int update(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlLoggerUtils.printfSql(sql, paramMap);
        int res = jdbcTemplate.update(sql, paramMap);
        SqlLoggerUtils.printfUpdateTotal(res);
        return res;
    }

    /**
     * 执行更新SQL，返回更新影响数据量
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public int update(String sql) {
        return update(sql, Collections.emptyMap());
    }

    /**
     * 更新数据库表数据
     *
     * @param tableName         表名称
     * @param fields            更新字段值
     * @param whereMap          更新条件字段(只支持=，and条件)
     * @param camelToUnderscore 字段驼峰转下划线(可选)
     */
    public int updateTable(String tableName, Map<String, Object> fields, Map<String, Object> whereMap, boolean camelToUnderscore) {
        Assert.hasText(tableName, "更新表名称不能为空");
        Assert.notEmpty(fields, "更新字段不能为空");
        Assert.notEmpty(whereMap, "更新条件不能为空");
        tableName = StringUtils.trim(tableName);
        TupleTow<String, Map<String, Object>> tupleTow = SqlUtils.updateSql(tableName, fields, whereMap, camelToUnderscore);
        String sql = StringUtils.trim(tupleTow.getValue1());
        return update(sql, tupleTow.getValue2());
    }

    /**
     * 更新数据库表数据
     *
     * @param tableName 表名称
     * @param fields    更新字段值
     * @param whereMap  更新条件字段(只支持=，and条件)
     */
    public int updateTable(String tableName, Map<String, Object> fields, Map<String, Object> whereMap) {
        return updateTable(tableName, fields, whereMap, false);
    }

    /**
     * 更新数据库表数据
     *
     * @param tableName         表名称
     * @param fields            更新字段值
     * @param where             自定义where条件(不用写where关键字)
     * @param camelToUnderscore 字段驼峰转下划线(可选)
     */
    public int updateTable(String tableName, Map<String, Object> fields, String where, boolean camelToUnderscore) {
        Assert.hasText(tableName, "更新表名称不能为空");
        Assert.notEmpty(fields, "更新字段不能为空");
        Assert.hasText(where, "更新条件不能为空");
        tableName = StringUtils.trim(tableName);
        TupleTow<String, Map<String, Object>> tupleTow = SqlUtils.updateSql(tableName, fields, null, camelToUnderscore);
        String sql = String.format("%s where %s", tupleTow.getValue1(), StringUtils.trim(where));
        return update(sql, tupleTow.getValue2());
    }

    /**
     * 更新数据库表数据
     *
     * @param tableName 表名称
     * @param fields    更新字段值
     * @param where     自定义where条件(不用写where关键字)
     */
    public int updateTable(String tableName, Map<String, Object> fields, String where) {
        return updateTable(tableName, fields, where, false);
    }

    /**
     * 执行insert SQL，返回数据库自增主键值和新增数据量
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param paramMap 参数(可选)，参数格式[:param]
     */
    public InsertResult insert(String sql, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        SqlParameterSource sqlParameterSource;
        if (paramMap != null && paramMap.size() > 0) {
            sqlParameterSource = new MapSqlParameterSource(paramMap);
        } else {
            sqlParameterSource = new EmptySqlParameterSource();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        SqlLoggerUtils.printfSql(sql, paramMap);
        int insertCount = jdbcTemplate.update(sql, sqlParameterSource, keyHolder);
        SqlLoggerUtils.printfUpdateTotal(insertCount);
        List<Map<String, Object>> keysList = keyHolder.getKeyList();
        InsertResult.KeyHolder resultKeyHolder = new InsertResult.KeyHolder(keysList);
        return new InsertResult(insertCount, resultKeyHolder);
    }

    /**
     * 执行insert SQL，返回数据库自增主键值和新增数据量
     *
     * @param sql sql脚本，参数格式[:param]
     */
    public InsertResult insert(String sql) {
        return insert(sql, null);
    }

    /**
     * 数据插入到表
     *
     * @param tableName         表名称
     * @param fields            字段名
     * @param camelToUnderscore 字段驼峰转下划线(可选)
     */
    public InsertResult insertTable(String tableName, Map<String, Object> fields, boolean camelToUnderscore) {
        Assert.hasText(tableName, "插入表名称不能为空");
        Assert.notEmpty(fields, "插入字段不能为空");
        tableName = StringUtils.trim(tableName);
        TupleTow<String, Map<String, Object>> tupleTow = SqlUtils.insertSql(tableName, fields, camelToUnderscore);
        return insert(tupleTow.getValue1(), tupleTow.getValue2());
    }

    /**
     * 数据插入到表
     *
     * @param tableName 表名称
     * @param fields    字段名
     */
    public InsertResult insertTable(String tableName, Map<String, Object> fields) {
        return insertTable(tableName, fields, false);
    }

    /**
     * 数据插入到表
     *
     * @param tableName         表名称
     * @param fieldsList        字段名集合
     * @param camelToUnderscore 字段驼峰转下划线(可选)
     */
    public List<InsertResult> insertTables(String tableName, Collection<Map<String, Object>> fieldsList, boolean camelToUnderscore) {
        Assert.hasText(tableName, "插入表名称不能为空");
        Assert.notEmpty(fieldsList, "插入字段不能为空");
        tableName = StringUtils.trim(tableName);
        List<InsertResult> resultList = new ArrayList<>(fieldsList.size());
        for (Map<String, Object> fields : fieldsList) {
            TupleTow<String, Map<String, Object>> tupleTow = SqlUtils.insertSql(tableName, fields, camelToUnderscore);
            InsertResult insertResult = insert(tupleTow.getValue1(), tupleTow.getValue2());
            resultList.add(insertResult);
        }
        return resultList;
    }

    /**
     * 数据插入到表
     *
     * @param tableName  表名称
     * @param fieldsList 字段名集合
     */
    public List<InsertResult> insertTables(String tableName, Collection<Map<String, Object>> fieldsList) {
        return insertTables(tableName, fieldsList, false);
    }

    /**
     * 批量执行更新SQL，返回更新影响数据量
     *
     * @param sql          sql脚本，参数格式[:param]
     * @param paramMapList 参数数组，参数格式[:param]
     */
    public int[] batchUpdate(String sql, Collection<Map<String, Object>> paramMapList) {
        Assert.hasText(sql, "sql不能为空");
        Assert.notNull(paramMapList, "参数数组不能为空");
        sql = StringUtils.trim(sql);
        SqlParameterSource[] paramMapArray = new SqlParameterSource[paramMapList.size()];
        int index = 0;
        for (Map<String, Object> map : paramMapList) {
            paramMapArray[index] = new MapSqlParameterSource(map);
            index++;
        }
        SqlLoggerUtils.printfSql(sql, paramMapList);
        int[] res = jdbcTemplate.batchUpdate(sql, paramMapArray);
        SqlLoggerUtils.printfUpdateTotal(res);
        return res;
    }

    /**
     * 排序查询
     *
     * @param sql      sql脚本，参数格式[:param]
     * @param sort     排序配置
     * @param paramMap 参数，参数格式[:param]
     */
    public List<Map<String, Object>> queryBySort(String sql, QueryBySort sort, Map<String, Object> paramMap) {
        Assert.hasText(sql, "sql不能为空");
        sql = StringUtils.trim(sql);
        // 构造排序以及分页sql
        String sortSql = SqlUtils.concatOrderBy(sql, sort);
        SqlLoggerUtils.printfSql(sortSql, paramMap);
        List<Map<String, Object>> res = jdbcTemplate.queryForList(sortSql, paramMap);
        SqlLoggerUtils.printfTotal(res);
        return res;
    }

    /**
     * 排序查询
     *
     * @param sql  sql脚本，参数格式[:param]
     * @param sort 排序配置
     */
    public List<Map<String, Object>> queryBySort(String sql, QueryBySort sort) {
        return queryBySort(sql, sort, Collections.emptyMap());
    }

    /**
     * 分页查询(支持排序)，返回分页对象
     *
     * @param sql        sql脚本，参数格式[:param]
     * @param pagination 分页配置(支持排序)
     * @param paramMap   参数，参数格式[:param]
     * @param countQuery 是否要执行count查询(可选)
     */
    public IPage<Map<String, Object>> queryByPage(String sql, QueryByPage pagination, Map<String, Object> paramMap, boolean countQuery) {
        Assert.hasText(sql, "sql不能为空");
        Assert.notNull(pagination, "分页配置不能为空");
        sql = StringUtils.trim(sql);
        Page<Map<String, Object>> page = new Page<>(pagination.getPageNo(), Math.min(pagination.getPageSize(), Max_Page_Size));
        // 执行 count 查询
        if (countQuery) {
            long total = queryCount(sql, paramMap);
            page.setTotal(total);
            // 溢出总页数，设置最后一页
            long pages = page.getPages();
            if (page.getCurrent() > pages) {
                page.setCurrent(pages);
            }
        } else {
            page.setSearchCount(false);
            page.setTotal(-1);
        }
        // 构造排序以及分页sql
        String sortSql = SqlUtils.concatOrderBy(sql, pagination);
        String pageSql = DialectFactory.buildPaginationSql(page, sortSql, paramMap, dbType, null);
        // 执行 pageSql
        SqlLoggerUtils.printfSql(pageSql, paramMap);
        List<Map<String, Object>> listData = jdbcTemplate.queryForList(pageSql, paramMap);
        SqlLoggerUtils.printfTotal(listData);
        // 设置返回数据
        page.setRecords(listData);
        // 排序信息
        List<String> orderFieldsTmp = pagination.getOrderFieldsSql();
        List<String> sortsTmp = pagination.getSortsSql();
        for (int i = 0; i < orderFieldsTmp.size(); i++) {
            String fieldSql = orderFieldsTmp.get(i);
            String sort = sortsTmp.get(i);
            OrderItem orderItem = new OrderItem();
            orderItem.setColumn(fieldSql);
            orderItem.setAsc(SqlUtils.ASC.equalsIgnoreCase(StringUtils.trim(sort)));
            page.addOrder(orderItem);
        }
        return page;
    }

    /**
     * 分页查询(支持排序)，返回分页对象
     *
     * @param sql        sql脚本，参数格式[:param]
     * @param pagination 分页配置(支持排序)
     * @param countQuery 是否要执行count查询(可选)
     */
    public IPage<Map<String, Object>> queryByPage(String sql, QueryByPage pagination, boolean countQuery) {
        return queryByPage(sql, pagination, Collections.emptyMap(), countQuery);
    }

    /**
     * 分页查询(支持排序)，返回分页对象
     *
     * @param sql        sql脚本，参数格式[:param]
     * @param pagination 分页配置(支持排序)
     */
    public IPage<Map<String, Object>> queryByPage(String sql, QueryByPage pagination) {
        return queryByPage(sql, pagination, Collections.emptyMap(), true);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param timeout             设置事务超时时间，-1表示不超时(单位：秒)
     * @param isolationLevel      设置事务隔离级别 @link org.springframework.transaction.TransactionDefinition#ISOLATION_DEFAULT}
     * @param readOnly            设置事务是否只读
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginTX(TransactionCallback<T> action, int propagationBehavior, int timeout, int isolationLevel, boolean readOnly) {
        Assert.notNull(action, "分页配置不能为空");
        TransactionTemplate transactionTemplate = createTransactionDefinition(isolationLevel, propagationBehavior, readOnly, timeout);
        return transactionTemplate.execute(action);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param timeout             设置事务超时时间(单位：秒)
     * @param isolationLevel      设置事务隔离级别 @link org.springframework.transaction.TransactionDefinition#ISOLATION_DEFAULT}
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginTX(TransactionCallback<T> action, int propagationBehavior, int timeout, int isolationLevel) {
        return beginTX(action, propagationBehavior, timeout, isolationLevel, false);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param timeout             设置事务超时时间(单位：秒)
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginTX(TransactionCallback<T> action, int propagationBehavior, int timeout) {
        return beginTX(action, propagationBehavior, timeout, TransactionDefinition.ISOLATION_DEFAULT, false);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginTX(TransactionCallback<T> action, int propagationBehavior) {
        return beginTX(action, propagationBehavior, -1, TransactionDefinition.ISOLATION_DEFAULT, false);
    }

    /**
     * 在事务内支持操作
     *
     * @param action 事务内数据库操作
     * @param <T>    返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginTX(TransactionCallback<T> action) {
        return beginTX(action, TransactionDefinition.PROPAGATION_REQUIRED, -1, TransactionDefinition.ISOLATION_DEFAULT, false);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param timeout             设置事务超时时间，-1表示不超时(单位：秒)
     * @param isolationLevel      设置事务隔离级别 @link org.springframework.transaction.TransactionDefinition#ISOLATION_DEFAULT}
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginReadOnlyTX(TransactionCallback<T> action, int propagationBehavior, int timeout, int isolationLevel) {
        return beginTX(action, propagationBehavior, timeout, isolationLevel, true);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param timeout             设置事务超时时间，-1表示不超时(单位：秒)
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginReadOnlyTX(TransactionCallback<T> action, int propagationBehavior, int timeout) {
        return beginTX(action, propagationBehavior, timeout, TransactionDefinition.ISOLATION_DEFAULT, true);
    }

    /**
     * 在事务内支持操作
     *
     * @param action              事务内数据库操作
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param <T>                 返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginReadOnlyTX(TransactionCallback<T> action, int propagationBehavior) {
        return beginTX(action, propagationBehavior, -1, TransactionDefinition.ISOLATION_DEFAULT, true);
    }

    /**
     * 在事务内支持操作
     *
     * @param action 事务内数据库操作
     * @param <T>    返回值类型
     * @see org.springframework.transaction.TransactionDefinition
     */
    public <T> T beginReadOnlyTX(TransactionCallback<T> action) {
        return beginTX(action, TransactionDefinition.PROPAGATION_REQUIRED, -1, TransactionDefinition.ISOLATION_DEFAULT, true);
    }

    /**
     * 创建事务执行模板对象
     *
     * @param isolationLevel      设置事务隔离级别 {@link org.springframework.transaction.TransactionDefinition#ISOLATION_DEFAULT}
     * @param propagationBehavior 设置事务传递性 {@link org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED}
     * @param readOnly            设置事务是否只读
     * @param timeout             设置事务超时时间(单位：秒)
     * @see org.springframework.transaction.TransactionDefinition
     */
    private TransactionTemplate createTransactionDefinition(int isolationLevel, int propagationBehavior, boolean readOnly, int timeout) {
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setName(getNextTransactionName());
        transactionDefinition.setPropagationBehavior(propagationBehavior);
        transactionDefinition.setTimeout(timeout);
        transactionDefinition.setIsolationLevel(isolationLevel);
        transactionDefinition.setReadOnly(readOnly);
        return new TransactionTemplate(transactionManager, transactionDefinition);
    }

    /**
     * 获取下一个事务名称
     */
    private String getNextTransactionName() {
        int nextSerialNumber = transactionSerialNumber.incrementAndGet();
        String transactionName;
        if (nextSerialNumber < 0) {
            transactionName = Transaction_Name_Prefix + nextSerialNumber;
        } else {
            transactionName = Transaction_Name_Prefix + "+" + nextSerialNumber;
        }
        return transactionName;
    }

    // TODO 动态sql支持(mybatis标准?)

    @Override
    public boolean isClosed() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            return hikariDataSource.isClosed();
        }
        return closed;
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            if (!hikariDataSource.isClosed()) {
                super.close();
                hikariDataSource.close();
            }
        } else {
            throw new UnsupportedOperationException("当前数据源不支持close");
        }
    }
}
