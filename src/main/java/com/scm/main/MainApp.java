package com.scm.main;

import com.scm.config.DBConnection;
import com.scm.dao.*;
import com.scm.util.AppLogger;
import java.sql.Connection;
import java.util.List;
import java.util.Scanner;

public class MainApp {
    public static void main(String[] args) {
        AppLogger.log("INFO", "=== 애플리케이션 시작 ===");

        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                AppLogger.log("INFO", "데이터베이스 연결 성공");
                showMenu(conn);
            } else {
                AppLogger.log("ERROR", "데이터베이스 연결 실패 (Connection is null)");
            }
        } catch (Exception e) {
            AppLogger.log("ERROR", "시스템 치명적 오류: " + e.getMessage());
        } finally {
            AppLogger.log("INFO", "=== 애플리케이션 종료 ===");
        }
    }

    private static void showMenu(Connection conn) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                System.out.println("\n--- [ 메인 메뉴 ] ---");
                System.out.println("1. 프로젝트 대시보드 조회 (기능 1)");
                System.out.println("2. 신규 발주 및 납품 등록 (기능 2)");
                System.out.println("3. 공급업체 ESG 리포트 (기능 3)");
                System.out.println("0. 종료");
                System.out.print("선택: ");

                String choice = scanner.nextLine();

                switch (choice) {
                    case "1":
                        System.out.print("조회할 프로젝트 ID 또는 선박명 입력: ");
                        String input = scanner.nextLine();
                        new DashboardDAO(conn).printProjectDashboard(input);
                        break;
                    case "2": {
                        System.out.println("\n--- [ 신규 발주 등록 ] ---");
                        System.out.print("프로젝트 ID: ");
                        int pid = Integer.parseInt(scanner.nextLine());
                        System.out.print("공급업체 ID: ");
                        int sid = Integer.parseInt(scanner.nextLine());
                        System.out.print("입고 창고 ID: ");
                        int wid = Integer.parseInt(scanner.nextLine());

                        List<OrderDAO.OrderItem> items = new java.util.ArrayList<>();
                        while (true) {
                            System.out.print("부품 ID (종료하려면 0 입력): ");
                            int partId = Integer.parseInt(scanner.nextLine());
                            if (partId == 0) break;
                            System.out.print("수량: ");
                            int qty = Integer.parseInt(scanner.nextLine());
                            System.out.print("단가: ");
                            double price = Double.parseDouble(scanner.nextLine());
                            items.add(new OrderDAO.OrderItem(partId, qty, price));
                        }

                        if (!items.isEmpty()) {
                            new OrderDAO(conn).processOrderTransaction(pid, sid, "jack01", items, wid);
                        } else {
                            System.out.println("[안내] 발주 항목이 없어 취소합니다.");
                        }
                        break;
                    }
                    case "3": {
                        System.out.println("\n--- [ 공급업체 ESG 및 지연 리포트 필터 ] ---");
                        System.out.print("ESG 등급 (A~D 공백 구분, 전체는 Enter): ");
                        String esgInput = scanner.nextLine();
                        List<String> esgFilters = esgInput.isEmpty() ? null : java.util.Arrays.asList(esgInput.split(" "));

                        System.out.print("지연율 하한(%) (기본 0): ");
                        double minDelay = 0;
                        String minInput = scanner.nextLine();
                        if (!minInput.isEmpty()) minDelay = Double.parseDouble(minInput);

                        System.out.print("지연율 상한(%) (기본 100): ");
                        double maxDelay = 100;
                        String maxInput = scanner.nextLine();
                        if (!maxInput.isEmpty()) maxDelay = Double.parseDouble(maxInput);

                        SupplierDAO supplierDAO = new SupplierDAO(conn);
                        supplierDAO.printSupplierReport(esgFilters, minDelay, maxDelay);

                        System.out.print("\n상세 조회할 업체 ID (건너뛰려면 0): ");
                        int targetSid = Integer.parseInt(scanner.nextLine());
                        if (targetSid != 0) supplierDAO.printSupplierDetail(targetSid);
                        break;
                    }
                    case "0":
                        return;
                    default:
                        System.out.println("잘못된 입력입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("[오류] 숫자 형식이 올바르지 않습니다.");
                AppLogger.log("ERROR", "입력 데이터 형식 오류: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[오류] 처리 중 예외가 발생했습니다.");
                AppLogger.log("ERROR", "실행 중 예외: " + e.getMessage());
            }
        }
    }
}