package com.shop.service;

import com.shop.model.ManualPayment;
import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.repo.ManualPaymentRepo;
import com.shop.repo.OrderRepo;
import com.shop.repo.ProductRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatsService {

    public record TopProduct(String name, long quantity, BigDecimal revenue) {}

    public record Stats(BigDecimal revenueTotal, BigDecimal revenueToday, BigDecimal revenueMonth,
                        long ordersTotal, long ordersToday, long ordersPending,
                        long activeCustomers, List<TopProduct> topProducts) {}

    public record SeriesPoint(String label, BigDecimal value) {}

    private final OrderRepo orderRepo;
    private final ManualPaymentRepo manualPaymentRepo;
    private final ProductRepo productRepo;

    /**
     * Umsatzverlauf für das Dashboard-Chart. Bucketing:
     * day = 24 Stunden stündlich, week = 7 Tage, month = 30 Tage, year = 12 Monate.
     * Zählt Shop-Bestellungen (bezahlt/geliefert) + manuelle Zahlungen (bezahlt).
     */
    public List<SeriesPoint> revenueSeries(String range) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        DateTimeFormatter fmt;
        Instant cutoff;

        switch (range == null ? "month" : range) {
            case "day" -> {
                fmt = DateTimeFormatter.ofPattern("HH:00");
                ZonedDateTime start = now.minusHours(23).truncatedTo(ChronoUnit.HOURS);
                cutoff = start.toInstant();
                for (int i = 0; i < 24; i++) buckets.put(fmt.format(start.plusHours(i)), BigDecimal.ZERO);
            }
            case "week" -> {
                fmt = DateTimeFormatter.ofPattern("dd.MM.");
                ZonedDateTime start = now.minusDays(6).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 7; i++) buckets.put(fmt.format(start.plusDays(i)), BigDecimal.ZERO);
            }
            case "year" -> {
                fmt = DateTimeFormatter.ofPattern("MM.yy");
                ZonedDateTime start = now.minusMonths(11).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 12; i++) buckets.put(fmt.format(start.plusMonths(i)), BigDecimal.ZERO);
            }
            default -> {
                fmt = DateTimeFormatter.ofPattern("dd.MM.");
                ZonedDateTime start = now.minusDays(29).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 30; i++) buckets.put(fmt.format(start.plusDays(i)), BigDecimal.ZERO);
            }
        }

        for (Order o : orderRepo.findByStatusInAndPaidAtAfter(OrderService.REVENUE_STATUSES, cutoff)) {
            String key = fmt.format(o.getPaidAt().atZone(zone));
            buckets.computeIfPresent(key, (k, v) -> v.add(o.getTotalPrice()));
        }
        for (ManualPayment p : manualPaymentRepo.findByStatusAndPaymentDateAfter(ManualPayment.Status.PAID, cutoff)) {
            String key = fmt.format(p.getPaymentDate().atZone(zone));
            buckets.computeIfPresent(key, (k, v) -> v.add(p.getAmount()));
        }
        return buckets.entrySet().stream()
                .map(e -> new SeriesPoint(e.getKey(), e.getValue()))
                .toList();
    }

    public Stats getStats() {
        ZoneId zone = ZoneId.systemDefault();
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        var statuses = OrderService.REVENUE_STATUSES;

        List<TopProduct> top = orderRepo.topProducts(statuses).stream()
                .limit(5)
                .map(row -> new TopProduct((String) row[0], (Long) row[1], (BigDecimal) row[2]))
                .toList();

        return new Stats(
                orderRepo.revenueTotal(statuses),
                orderRepo.revenueSince(statuses, todayStart),
                orderRepo.revenueSince(statuses, monthStart),
                orderRepo.count(),
                orderRepo.countByCreatedAtAfter(todayStart),
                orderRepo.countByStatus(Order.Status.PENDING),
                orderRepo.activeCustomersSince(Instant.now().minus(30, ChronoUnit.DAYS)),
                top
        );
    }

    // ===== Tenant-Sicht: nur Bestellungen für Produkte auf den eigenen Servern =====

    /** Map productId -> guildId für die Guild-Filterung von Bestellungen. */
    private Map<Long, String> productGuilds() {
        Map<Long, String> result = new HashMap<>();
        for (Product p : productRepo.findAll()) result.put(p.getId(), p.getGuildId());
        return result;
    }

    private List<Order> ordersForGuilds(Set<String> guildIds) {
        Map<Long, String> guilds = productGuilds();
        return orderRepo.findAll().stream()
                .filter(o -> guildIds.contains(guilds.get(o.getProductId())))
                .toList();
    }

    public Stats getStatsFor(Set<String> guildIds) {
        ZoneId zone = ZoneId.systemDefault();
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant activeCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Order> orders = ordersForGuilds(guildIds);

        var paid = orders.stream()
                .filter(o -> OrderService.REVENUE_STATUSES.contains(o.getStatus()))
                .toList();
        BigDecimal total = paid.stream().map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal today = paid.stream().filter(o -> o.getPaidAt() != null && o.getPaidAt().isAfter(todayStart))
                .map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal month = paid.stream().filter(o -> o.getPaidAt() != null && o.getPaidAt().isAfter(monthStart))
                .map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, long[]> qtyByProduct = new LinkedHashMap<>();
        Map<String, BigDecimal> revByProduct = new LinkedHashMap<>();
        for (Order o : paid) {
            qtyByProduct.computeIfAbsent(o.getProductName(), k -> new long[1])[0] += o.getQuantity();
            revByProduct.merge(o.getProductName(), o.getTotalPrice(), BigDecimal::add);
        }
        List<TopProduct> top = revByProduct.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> new TopProduct(e.getKey(), qtyByProduct.get(e.getKey())[0], e.getValue()))
                .toList();

        return new Stats(
                total, today, month,
                orders.size(),
                orders.stream().filter(o -> o.getCreatedAt().isAfter(todayStart)).count(),
                orders.stream().filter(o -> o.getStatus() == Order.Status.PENDING).count(),
                orders.stream().filter(o -> o.getCreatedAt().isAfter(activeCutoff))
                        .map(Order::getUserId).distinct().count(),
                top
        );
    }

    /** Umsatzverlauf nur für die eigenen Server + die eigenen manuellen Zahlungen. */
    public List<SeriesPoint> revenueSeriesFor(String range, Set<String> guildIds, String ownerId) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        DateTimeFormatter fmt;
        Instant cutoff;

        switch (range == null ? "month" : range) {
            case "day" -> {
                fmt = DateTimeFormatter.ofPattern("HH:00");
                ZonedDateTime start = now.minusHours(23).truncatedTo(ChronoUnit.HOURS);
                cutoff = start.toInstant();
                for (int i = 0; i < 24; i++) buckets.put(fmt.format(start.plusHours(i)), BigDecimal.ZERO);
            }
            case "week" -> {
                fmt = DateTimeFormatter.ofPattern("dd.MM.");
                ZonedDateTime start = now.minusDays(6).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 7; i++) buckets.put(fmt.format(start.plusDays(i)), BigDecimal.ZERO);
            }
            case "year" -> {
                fmt = DateTimeFormatter.ofPattern("MM.yy");
                ZonedDateTime start = now.minusMonths(11).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 12; i++) buckets.put(fmt.format(start.plusMonths(i)), BigDecimal.ZERO);
            }
            default -> {
                fmt = DateTimeFormatter.ofPattern("dd.MM.");
                ZonedDateTime start = now.minusDays(29).truncatedTo(ChronoUnit.DAYS);
                cutoff = start.toInstant();
                for (int i = 0; i < 30; i++) buckets.put(fmt.format(start.plusDays(i)), BigDecimal.ZERO);
            }
        }

        Map<Long, String> guilds = productGuilds();
        for (Order o : orderRepo.findByStatusInAndPaidAtAfter(OrderService.REVENUE_STATUSES, cutoff)) {
            if (!guildIds.contains(guilds.get(o.getProductId()))) continue;
            String key = fmt.format(o.getPaidAt().atZone(zone));
            buckets.computeIfPresent(key, (k, v) -> v.add(o.getTotalPrice()));
        }
        for (ManualPayment p : manualPaymentRepo.findByStatusAndPaymentDateAfter(ManualPayment.Status.PAID, cutoff)) {
            if (ownerId == null || !ownerId.equals(p.getOwnerId())) continue;
            String key = fmt.format(p.getPaymentDate().atZone(zone));
            buckets.computeIfPresent(key, (k, v) -> v.add(p.getAmount()));
        }
        return buckets.entrySet().stream()
                .map(e -> new SeriesPoint(e.getKey(), e.getValue()))
                .toList();
    }
}
