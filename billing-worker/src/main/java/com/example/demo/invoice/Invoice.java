package com.example.demo.invoice;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "billing_cycle"}))
@Getter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "billing_cycle", nullable = false, length = 7)
    private String billingCycle;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "base_plan_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePlanFee;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    public Invoice(String tenantId, String billingCycle, String currency, BigDecimal basePlanFee,
                   BigDecimal totalAmount, Instant createdAt) {
        this.tenantId = tenantId;
        this.billingCycle = billingCycle;
        this.currency = currency;
        this.basePlanFee = basePlanFee;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public void addLineItem(InvoiceLineItem item) {
        item.attachTo(this);
        lineItems.add(item);
    }
}
