package com.scm.dao;

import java.sql.*;
import java.util.List;

public class SupplierDAO {
    private Connection conn;

    public SupplierDAO(Connection conn) {
        this.conn = conn;
    }
    /**
     * ESG 등급과 지연율 필터를 적용하여 공급업체 리포트를 출력한다.
     * @param esgFilters 선택된 ESG 등급 리스트
     * @param minDelay 지연율 하한값
     * @param maxDelay 지연율 상한값
     */
    public void printSupplierReport(List<String> esgFilters, double minDelay, double maxDelay) {
        // 복잡한 집계 쿼리: 공급업체별 총 금액 및 지연 비율 계산
        StringBuilder sql = new StringBuilder(
                "SELECT s.Supp_ID, s.Name, s.Country, s.ESG, " +
                        "COALESCE(SUM(l.Qty * l.OrderPrice), 0) as total_order_amt, " +
                        "COUNT(DISTINCT d.Del_ID) as total_deliv_count, " +
                        "COUNT(DISTINCT CASE WHEN d.Status = '지연' THEN d.Del_ID END) as delay_count " +
                        "FROM SUPPLIER s " +
                        "LEFT JOIN PURCHASE_ORDER o ON s.Supp_ID = o.Supp_ID " +
                        "LEFT JOIN PO_LINE l ON o.PO_ID = l.PO_ID " +
                        "LEFT JOIN DELIVERY d ON o.PO_ID = d.PO_ID " +
                        "WHERE 1=1 "
        );

        // ESG 필터링 추가 (다수 선택 가능)
        if (esgFilters != null && !esgFilters.isEmpty()) {
            sql.append("AND s.ESG IN (");
            for (int i = 0; i < esgFilters.size(); i++) {
                sql.append("'").append(esgFilters.get(i)).append("'").append(i < esgFilters.size() - 1 ? "," : "");
            }
            sql.append(") ");
        }

        sql.append("GROUP BY s.Supp_ID, s.Name, s.Country, s.ESG ");

        // 지연 비율 필터링 (HAVING 사용)
        sql.append("HAVING (CASE WHEN COUNT(DISTINCT d.Del_ID) = 0 THEN 0 ");
        sql.append("ELSE CAST(COUNT(DISTINCT CASE WHEN d.Status = '지연' THEN d.Del_ID END) AS FLOAT) / COUNT(DISTINCT d.Del_ID) * 100 END) ");
        sql.append("BETWEEN ? AND ?");

        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setDouble(1, minDelay);
            pstmt.setDouble(2, maxDelay);
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\n-------------------------------------------------------------------------------------------------");
            System.out.printf("%-5s | %-12s | %-8s | %-4s | %-15s | %-10s\n", "ID", "공급업체명", "국가", "ESG", "총 발주금액", "지연율(%)");
            System.out.println("-------------------------------------------------------------------------------------------------");

            while (rs.next()) {
                double totalDeliv = rs.getDouble("total_deliv_count");
                double delayCount = rs.getDouble("delay_count");
                double delayRate = totalDeliv == 0 ? 0 : (delayCount / totalDeliv) * 100;

                System.out.printf("%-5d | %-12s | %-8s | %-4s | %,15.0f | %-10.1f%%\n",
                        rs.getInt("Supp_ID"), rs.getString("Name"), rs.getString("Country"),
                        rs.getString("ESG"), rs.getDouble("total_order_amt"), delayRate);
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] 공급업체 리포트 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 특정 공급업체의 최근 발주 내역 상세 정보를 조회한다.
     * @param sid 공급업체 ID
     */
    public void printSupplierDetail(int sid) {
        String sql = "SELECT o.PO_ID, o.OrderDate, o.Status, " +
                "EXISTS(SELECT 1 FROM DELIVERY d WHERE d.PO_ID = o.PO_ID AND d.Status = '지연') as is_delayed " +
                "FROM PURCHASE_ORDER o WHERE o.Supp_ID = ? " +
                "ORDER BY o.OrderDate DESC LIMIT 5";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sid);
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\n[ 최근 발주 내역 (최대 5건) ]");
            while (rs.next()) {
                String delayText = rs.getBoolean("is_delayed") ? "지연 발생" : "정상";
                System.out.printf("발주ID: %-5d | 날짜: %s | 상태: %-6s | 지연여부: %s\n",
                        rs.getInt("PO_ID"), rs.getDate("OrderDate"), rs.getString("Status"), delayText);
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] 상세 조회 실패: " + e.getMessage());
        }
    }
}