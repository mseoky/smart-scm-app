package com.scm.dao;

import com.scm.util.AppLogger;
import java.sql.*;
import java.util.List;

/**
 * 발주 및 납품 관리를 담당하는 Data Access Object 클래스
 */
public class OrderDAO {
    private Connection conn;
    private static final int MAX_RETRIES = 3;

    public OrderDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * 신규 발주 등록, 초기 납품 생성 및 재고 반영을 하나의 트랜잭션으로 처리한다.
     * @param pid 프로젝트 ID
     * @param sid 공급업체 ID
     * @param userId 담당 사용자 ID
     * @param items 발주 항목 리스트
     * @param wid 입고할 창고 ID
     */
    public void processOrderTransaction(int pid, int sid, String userId, List<OrderItem> items, int wid) {
        int retryCount = 0;
        boolean success = false;

        while (retryCount < MAX_RETRIES && !success) {
            try {
                conn.setAutoCommit(false);
                AppLogger.log("INFO", "발주 트랜잭션 시도 " + (retryCount + 1));

                // 1. 발주서 생성
                int poId = 0;
                String poSql = "INSERT INTO PURCHASE_ORDER (Proj_ID, Supp_ID, User_ID, Status) VALUES (?, ?, ?, '발주완료') RETURNING PO_ID";
                try (PreparedStatement pstmt = conn.prepareStatement(poSql)) {
                    pstmt.setInt(1, pid); pstmt.setInt(2, sid); pstmt.setString(3, userId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) poId = rs.getInt(1);
                }

                // 2. 초기 납품 기록 생성
                int delId = 0;
                String delSql = "INSERT INTO DELIVERY (ArrivalDate, TransType, Distance, Status, PO_ID) VALUES (CURRENT_DATE, '트럭', 0, '정상', ?) RETURNING Del_ID";
                try (PreparedStatement pstmt = conn.prepareStatement(delSql)) {
                    pstmt.setInt(1, poId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) delId = rs.getInt(1);
                }

                // 3. 각 항목 처리
                for (int i = 0; i < items.size(); i++) {
                    OrderItem item = items.get(i);
                    int lineNo = i + 1;

                    // 3-1. PO_LINE 삽입
                    String lineSql = "INSERT INTO PO_LINE (PO_ID, LineNo, Part_ID, Qty, OrderPrice, DueDate) VALUES (?, ?, ?, ?, ?, CURRENT_DATE + 30)";
                    try (PreparedStatement pstmt = conn.prepareStatement(lineSql)) {
                        pstmt.setInt(1, poId); pstmt.setInt(2, lineNo); pstmt.setInt(3, item.partId);
                        pstmt.setInt(4, item.qty); pstmt.setDouble(5, item.price);
                        pstmt.executeUpdate();
                    }

                    // 3-2. INCLUDES(DeliveryItem) 삽입
                    int delivQty = (int) (item.qty * 0.5);
                    String incSql = "INSERT INTO INCLUDES (Del_ID, PO_ID, LineNo, DelivQty, Inspection) VALUES (?, ?, ?, ?, '초기입고')";
                    try (PreparedStatement pstmt = conn.prepareStatement(incSql)) {
                        pstmt.setInt(1, delId); pstmt.setInt(2, poId); pstmt.setInt(3, lineNo);
                        pstmt.setInt(4, delivQty);
                        pstmt.executeUpdate();
                    }

                    // 3-3. 재고 반영 (Upsert)
                    String upsertSql = "INSERT INTO STORES (Wh_ID, Part_ID, Inventory) VALUES (?, ?, ?) " +
                            "ON CONFLICT (Wh_ID, Part_ID) DO UPDATE SET Inventory = STORES.Inventory + EXCLUDED.Inventory";
                    try (PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
                        pstmt.setInt(1, wid); pstmt.setInt(2, item.partId); pstmt.setInt(3, delivQty);
                        pstmt.executeUpdate();
                    }
                }

                conn.commit();
                AppLogger.log("INFO", "발주 트랜잭션 커밋 완료 (PO_ID: " + poId + ")");
                success = true;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                    AppLogger.log("ERROR", "트랜잭션 롤백: " + e.getMessage());
                    if ("40P01".equals(e.getSQLState())) { // Deadlock retry
                        retryCount++;
                        Thread.sleep(1000);
                    } else { break; }
                } catch (Exception ex) { break; }
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException e) {}
            }
        }
    }

    public static class OrderItem {
        public int partId; public int qty; public double price;
        public OrderItem(int partId, int qty, double price) {
            this.partId = partId; this.qty = qty; this.price = price;
        }
    }
}