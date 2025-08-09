package com.miempresa.fruver.infra.config;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Fabrica de DataSource usando HikariCP.
 */
public class DataSourceFactory {
    private static HikariDataSource ds;

    public static DataSource getDataSource() {
        if (ds == null) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:mysql://localhost:3306/fruver?serverTimezone=America/Bogota");
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
            cfg.setUsername("root");
            cfg.setPassword("12345");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            ds = new HikariDataSource(cfg);
        }
        return ds;
    }
}