package com.palminvest.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String assetSymbol;
    @Column(precision = 19, scale = 8) // Aceita frações (cripto, fundos)
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDateTime transactionDate;
    private String observation; // Campo opcional de anotações

    // 🚀 NOVOS CAMPOS AQUI
    private String broker; // Corretora
    private BigDecimal fee; // Taxas (Emolumentos, corretagem, etc)

    // Construtor vazio obrigatório
    public Transaction() {}

    // Getters e Setters padrão
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAssetSymbol() { return assetSymbol; }
    public void setAssetSymbol(String assetSymbol) { this.assetSymbol = assetSymbol; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    // 🚀 GETTERS E SETTERS DOS NOVOS CAMPOS
    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }
}