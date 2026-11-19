package com.example.demo.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
@Getter
@NoArgsConstructor
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;

    @Column(name = "consumed_units", nullable = false)
    private long consumedUnits;

    @Column(name = "included_units", nullable = false)
    private long includedUnits;

    @Column(name = "billable_units", nullable = false)
    private long billableUnits;

    @Column(name = "line_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineAmount;

    public InvoiceLineItem(String metricType, long consumedUnits, long includedUnits, long billableUnits, BigDecimal lineAmount) {
        this.metricType = metricType;
        this.consumedUnits = consumedUnits;
        this.includedUnits = includedUnits;
        this.billableUnits = billableUnits;
        this.lineAmount = lineAmount;
    }

    void attachTo(Invoice invoice) {
        this.invoice = invoice;
    }
}
