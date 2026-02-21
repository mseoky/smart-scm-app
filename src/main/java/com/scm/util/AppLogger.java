package com.scm.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogger {
    private static final String LOG_FILE = "scm_system.log";
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(dtf);
        String logEntry = String.format("[%s] [%s] %s", timestamp, level, message);

        // 1. 콘솔 출력
        if (level.equals("ERROR")) {
            System.err.println(logEntry);
        } else {
            System.out.println(logEntry);
        }

        // 2. 파일 저장 (append 모드)
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(logEntry);
        } catch (IOException e) {
            System.err.println("로그 파일 기록 실패: " + e.getMessage());
        }
    }
}