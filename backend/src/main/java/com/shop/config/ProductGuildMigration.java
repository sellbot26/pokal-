package com.shop.config;

import com.shop.repo.DiscountCodeRepo;
import com.shop.repo.ManualPaymentRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.SavedEmbedRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Einmalige Migration für Multi-Tenant-Trennung:
 *  1. Bestehende Produkte ohne guildId werden dem primär konfigurierten Server zugeordnet
 *     (damit auf dem bisherigen Server nichts aus /shop verschwindet).
 *  2. Bestehende gespeicherte Embeds ohne ownerId werden dem ersten konfigurierten Admin zugeordnet.
 *  3. Die alten globalen UNIQUE-Constraints auf products.name / saved_embeds.name werden entfernt —
 *     Namen müssen jetzt nur noch pro Server bzw. pro Nutzer eindeutig sein, nicht mehr global.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductGuildMigration {

    private final DataSource dataSource;
    private final ProductRepo productRepo;
    private final SavedEmbedRepo embedRepo;
    private final DiscountCodeRepo discountRepo;
    private final ManualPaymentRepo manualPaymentRepo;
    private final com.shop.repo.ShopUserRepo userRepo;
    private final ShopProperties props;

    @PostConstruct
    public void migrate() {
        backfillProductGuildId();
        backfillEmbedOwnerId();
        backfillDiscountGuildId();
        backfillManualPaymentOwnerId();
        rebrandToPokal();
        dropLegacyUniqueConstraint("products", "name");
        dropLegacyUniqueConstraint("saved_embeds", "name");
        dropLegacyUniqueConstraint("discount_codes", "code");
    }

    /** Alt-Daten von SELLBOT/ULTIMATE auf Pokal/BUSINESS umbenennen (einmalige Migration). */
    private void rebrandToPokal() {
        productRepo.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().startsWith("SELLBOT "))
                .forEach(p -> {
                    p.setName(p.getName().replaceFirst("^SELLBOT ", "Pokal ").replace("Ultimate Plan", "Business Plan"));
                    if ("ULTIMATE".equals(p.getDeliveryData())) p.setDeliveryData("BUSINESS");
                    productRepo.save(p);
                });
        userRepo.findAll().stream()
                .filter(u -> "ULTIMATE".equals(u.getPlanTier()))
                .forEach(u -> { u.setPlanTier("BUSINESS"); userRepo.save(u); });
    }

    private void backfillManualPaymentOwnerId() {
        var adminIds = props.getDiscord().adminIdList();
        if (adminIds.isEmpty()) return;
        String primaryOwner = adminIds.get(0);
        manualPaymentRepo.findAll().stream()
                .filter(p -> p.getOwnerId() == null || p.getOwnerId().isBlank())
                .forEach(p -> {
                    p.setOwnerId(primaryOwner);
                    manualPaymentRepo.save(p);
                });
    }

    private void backfillProductGuildId() {
        String primaryGuild = props.getDiscord().getGuildId();
        var adminIds = props.getDiscord().adminIdList();
        String primaryOwner = adminIds.isEmpty() ? null : adminIds.get(0);
        productRepo.findAll().forEach(p -> {
            boolean changed = false;
            if ((p.getGuildId() == null || p.getGuildId().isBlank()) && primaryGuild != null && !primaryGuild.isBlank()) {
                p.setGuildId(primaryGuild);
                changed = true;
            }
            // Plattform-Produkte (Plan-Käufe) gehören der Site — kein Owner-Backfill
            if ((p.getOwnerId() == null || p.getOwnerId().isBlank()) && primaryOwner != null
                    && !com.shop.service.PlanService.PLATFORM_CATEGORY.equals(p.getCategory())) {
                p.setOwnerId(primaryOwner);
                changed = true;
            }
            if (changed) productRepo.save(p);
        });
    }

    private void backfillEmbedOwnerId() {
        var adminIds = props.getDiscord().adminIdList();
        if (adminIds.isEmpty()) return;
        String primaryOwner = adminIds.get(0);
        embedRepo.findAll().stream()
                .filter(e -> e.getOwnerId() == null || e.getOwnerId().isBlank())
                .forEach(e -> {
                    e.setOwnerId(primaryOwner);
                    embedRepo.save(e);
                });
    }

    private void backfillDiscountGuildId() {
        String primaryGuild = props.getDiscord().getGuildId();
        if (primaryGuild == null || primaryGuild.isBlank()) return;
        discountRepo.findAll().stream()
                .filter(d -> d.getGuildId() == null || d.getGuildId().isBlank())
                .forEach(d -> {
                    d.setGuildId(primaryGuild);
                    discountRepo.save(d);
                });
    }

    private void dropLegacyUniqueConstraint(String tableHint, String columnHint) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String table = resolveTableName(meta, conn, tableHint);
            if (table == null) return;

            Set<String> indexNames = new LinkedHashSet<>();
            try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, table, true, false)) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    if (column != null && column.equalsIgnoreCase(columnHint) && indexName != null) {
                        indexNames.add(indexName);
                    }
                }
            }
            if (indexNames.isEmpty()) return;
            log.info("Uniqueness check: table='{}', found {} unique index(es) on '{}': {}",
                    table, indexNames.size(), columnHint, indexNames);
            for (String indexName : indexNames) {
                dropConstraintOrIndex(conn, table, indexName);
            }
        } catch (SQLException e) {
            log.debug("Could not inspect {} table constraints: {}", tableHint, e.getMessage());
        }
    }

    private String resolveTableName(DatabaseMetaData meta, Connection conn, String tableHint) throws SQLException {
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String t = rs.getString("TABLE_NAME");
                if (tableHint.equalsIgnoreCase(t)) return t;
            }
        }
        return null;
    }

    private void dropConstraintOrIndex(Connection conn, String table, String indexName) {
        // H2 liefert hier den Index-Namen (z.B. "xyz_INDEX_3" oder "xyz_INDEX_F" — der Suffix
        // ist mal dezimal, mal hex), die zugehörige Constraint heißt aber nur "xyz".
        String constraintName = indexName.replaceAll("_INDEX_\\w+$", "");
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + constraintName + "\"");
            log.info("Dropped legacy unique constraint {} on {}", constraintName, table);
            return;
        } catch (SQLException e1) {
            log.warn("DROP CONSTRAINT failed for {} on {}: {}", constraintName, table, e1.getMessage());
        }
        try (Statement st = conn.createStatement()) {
            st.execute("DROP INDEX \"" + indexName + "\"");
            log.info("Dropped legacy unique index {} on {}", indexName, table);
        } catch (SQLException e2) {
            log.warn("DROP INDEX failed for {} on {}: {}", indexName, table, e2.getMessage());
        }
    }
}
