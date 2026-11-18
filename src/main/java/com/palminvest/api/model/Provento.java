package com.palminvest.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

// Provento recebido de um ativo (dividendo, JCP, rendimento, amortização). O valor líquido é calculado, não gravado.
@Entity
@Table(name = "proventos")
public class Provento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dono do provento: cada usuário só enxerga os próprios
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(length = 20, nullable = false)
    private String assetSymbol;

    // Copiada da transação do usuário daquele ativo (ver ProventoService), nunca do corpo da requisição
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TipoProvento tipo;

    @Column(nullable = false)
    private LocalDate dataPagamento;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal valorBruto;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal irRetido;

    private String observacao;

    // Construtor vazio obrigatório
    protected Provento() {
    }

    public Provento(Usuario usuario, String assetSymbol, AssetClass assetClass, TipoProvento tipo, LocalDate dataPagamento,
                    BigDecimal valorBruto, BigDecimal irRetido, String observacao) {
        this.usuario = usuario;
        atualizar(assetSymbol, assetClass, tipo, dataPagamento, valorBruto, irRetido, observacao);
    }

    // Usado no PUT: substitui os campos editáveis (o dono não muda)
    public void atualizar(String assetSymbol, AssetClass assetClass, TipoProvento tipo, LocalDate dataPagamento,
                          BigDecimal valorBruto, BigDecimal irRetido, String observacao) {
        this.assetSymbol = assetSymbol;
        this.assetClass = assetClass;
        this.tipo = tipo;
        this.dataPagamento = dataPagamento;
        // Sempre com 2 casas, como a coluna numeric(19,2): a resposta do POST/PUT sai igual à do GET.
        // Os valores já chegam validados com no máximo 2 casas, então não há arredondamento.
        this.valorBruto = valorBruto.setScale(2);
        this.irRetido = irRetido.setScale(2);
        this.observacao = observacao;
    }

    // Bruto menos o IR retido na fonte
    public BigDecimal getValorLiquido() {
        return valorBruto.subtract(irRetido);
    }

    public Long getId() { return id; }

    public Usuario getUsuario() { return usuario; }

    public String getAssetSymbol() { return assetSymbol; }

    public AssetClass getAssetClass() { return assetClass; }

    public TipoProvento getTipo() { return tipo; }

    public LocalDate getDataPagamento() { return dataPagamento; }

    public BigDecimal getValorBruto() { return valorBruto; }

    public BigDecimal getIrRetido() { return irRetido; }

    public String getObservacao() { return observacao; }
}
