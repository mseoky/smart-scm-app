package com.scm.dao;

import java.sql.*;

public class DashboardDAO {
    private Connection conn;

    public DashboardDAO(Connection conn) {
        this.conn = conn;
    }
    /**
     * 프로젝트 ID 또는 선박명을 기반으로 대시보드 정보를 출력한다.
     * @param input 프로젝트 ID 또는 선박명 일부
     */
    public void printProjectDashboard(String input) {
        // 1. 프로젝트 기본 정보 조회 쿼리 (프로젝트 ID 혹은 선박명 일부)
        String basicSql = "SELECT * FROM SHIP_PROJECT WHERE Proj_ID::text = ? OR ShipName LIKE ?";

        try (PreparedStatement pstmt = conn.prepareStatement(basicSql)) {
            pstmt.setString(1, input);
            pstmt.setString(2, "%" + input + "%");
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                System.out.println("\n[안내] 해당 조건에 맞는 프로젝트를 찾을 수 없습니다.");
                return;
            }

            int pid = rs.getInt("Proj_ID");
            System.out.println("\n========= [ 프로젝트 기본 정보 ] =========");
            System.out.println("프로젝트 ID: " + pid);
            System.out.println("선 박 명: " + rs.getString("ShipName"));
            System.out.println("선    종: " + rs.getString("Type"));
            System.out.println("계 약 일: " + rs.getDate("ContractDate"));
            System.out.println("인도예정일: " + rs.getDate("DeliveryDate"));
            System.out.println("상    태: " + rs.getString("Status"));

            // 2. 비용 정보 및 탄소 정보 집계 (집계 함수, JOIN 활용)
            printFinancialAndCarbonInfo(pid);

        } catch (SQLException e) {
            System.err.println("[ERROR] 대시보드 조회 중 오류: " + e.getMessage());
        }
    }

    /**
     * 특정 프로젝트의 비용 및 탄소 배출 정보를 집계하여 출력한다.
     * @param pid 프로젝트 ID
     */
    private void printFinancialAndCarbonInfo(int pid) throws SQLException {
        // 총 발주 금액 계산
        String costSql = "SELECT SUM(l.Qty * l.OrderPrice) as total_cost " +
                "FROM PURCHASE_ORDER o JOIN PO_LINE l ON o.PO_ID = l.PO_ID " +
                "WHERE o.Proj_ID = ?";

        // 탄소 배출량 유형별 계산
        String carbonSql = "SELECT " +
                "SUM(CASE WHEN Type = '운송' THEN Amount ELSE 0 END) as transport_em, " +
                "SUM(CASE WHEN Type = '보관' THEN Amount ELSE 0 END) as storage_em, " +
                "SUM(Amount) as total_em " +
                "FROM CARBON_RECORD WHERE Proj_ID = ? OR Del_ID IN (SELECT Del_ID FROM DELIVERY WHERE PO_ID IN (SELECT PO_ID FROM PURCHASE_ORDER WHERE Proj_ID = ?))";

        double totalCost = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(costSql)) {
            pstmt.setInt(1, pid);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) totalCost = rs.getDouble("total_cost");
        }

        System.out.println("\n========= [ 비용 및 탄소 리포트 ] =========");
        System.out.printf("총 발주 금액: %,.0f 원\n", totalCost);

        try (PreparedStatement pstmt = conn.prepareStatement(carbonSql)) {
            pstmt.setInt(1, pid);
            pstmt.setInt(2, pid);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                double transport = rs.getDouble("transport_em");
                double storage = rs.getDouble("storage_em");
                double totalEm = rs.getDouble("total_em");

                System.out.printf("운송 탄소배출: %,.2f kg CO2e\n", transport);
                System.out.printf("보관 탄소배출: %,.2f kg CO2e\n", storage);
                System.out.printf("전체 탄소배출: %,.2f kg CO2e\n", totalEm);

                // 간단한 지표: 탄소 집약도 계산 (kg / 백만 원)
                if (totalCost > 0) {
                    double intensity = totalEm / (totalCost / 1_000_000.0);
                    System.out.printf("탄소 집약도: %,.4f kg CO2e / 백만 원\n", intensity);
                }
            }
        }

        // 공급업체별 발주 금액 상위 3개 (GROUP BY 활용)
        String supplierSql = "SELECT s.Name, SUM(l.Qty * l.OrderPrice) as supp_total " +
                "FROM PURCHASE_ORDER o " +
                "JOIN SUPPLIER s ON o.Supp_ID = s.Supp_ID " +
                "JOIN PO_LINE l ON o.PO_ID = l.PO_ID " +
                "WHERE o.Proj_ID = ? " +
                "GROUP BY s.Name ORDER BY supp_total DESC LIMIT 3";

        System.out.println("\n[ 공급업체별 발주 상위 3선 ]");
        try (PreparedStatement pstmt = conn.prepareStatement(supplierSql)) {
            pstmt.setInt(1, pid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                System.out.printf("- %s: %,.0f 원\n", rs.getString("Name"), rs.getDouble("supp_total"));
            }
        }
    }
}