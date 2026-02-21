package com.scm.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import com.scm.util.AppLogger;

public class DBConnection {
    public static Connection getConnection() {
        Connection conn = null;
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            Properties prop = new Properties();
            prop.load(input);

            String url = String.format("jdbc:postgresql://%s:%s/%s",
                    prop.getProperty("db.host"),
                    prop.getProperty("db.port"),
                    prop.getProperty("db.name"));

            conn = DriverManager.getConnection(url, prop.getProperty("db.user"), prop.getProperty("db.password"));

            // 트랜잭션 격리 수준 설정
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            AppLogger.log("INFO", "DB 접속 성공: " + url); // System.out -> AppLogger
        } catch (Exception e) {
            AppLogger.log("ERROR", "DB 접속 실패: " + e.getMessage());
        }
        return conn;
    }
}