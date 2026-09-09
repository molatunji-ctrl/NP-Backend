package org.admin.npapplication.controller;

import org.admin.npapplication.dto.*;
import org.admin.npapplication.model.*;
import org.admin.npapplication.repository.*;
import org.admin.npapplication.service.OrderService;
import org.admin.npapplication.service.PromoCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ContactMessageRepository messageRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PromoCodeService promoCodeService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // User stats
        stats.put("totalUsers", userRepository.count());
        stats.put("totalAdmins", userRepository.countByRole("ROLE_ADMIN"));

        // Product stats
        stats.put("totalProducts", productRepository.count());
        stats.put("activeProducts", productRepository.countByActiveTrue());
        stats.put("featuredProducts", productRepository.countByActiveTrueAndFeaturedTrue());
        stats.put("outOfStockProducts", productRepository.countByStockEquals(0));

        // Order stats
        OrderStatsDto orderStats = orderService.getOrderStats();
        stats.put("totalOrders", orderStats.getTotalOrders());
        stats.put("pendingOrders", orderStats.getPendingOrders());
        stats.put("confirmedOrders", orderStats.getConfirmedOrders());
        stats.put("shippedOrders", orderStats.getShippedOrders());
        stats.put("deliveredOrders", orderStats.getDeliveredOrders());
        stats.put("cancelledOrders", orderStats.getCancelledOrders());
        stats.put("totalRevenue", orderStats.getTotalRevenue());
        stats.put("paidRevenue", orderStats.getPaidRevenue());

        // Message stats
        stats.put("totalMessages", messageRepository.count());
        stats.put("unreadMessages", messageRepository.countByStatus(MessageStatus.UNREAD));

        // Promo stats
        stats.put("activePromos", promoCodeService.countActivePromos());

        // Recent orders (last 5)
        stats.put("recentOrders", orderService.getAllOrders(
                org.springframework.data.domain.PageRequest.of(
                        0,
                        5,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC,
                                "createdAt"
                        )
                )
        ).getContent());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/sales-chart")
    public ResponseEntity<Map<String, Object>> getSalesChart(
            @RequestParam(defaultValue = "30") int days
    ) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        Map<String, Object> chartData = new HashMap<>();
        chartData.put("labels", Collections.emptyList()); // Would need custom query
        chartData.put("data", Collections.emptyList());
        
        return ResponseEntity.ok(chartData);
    }
}
