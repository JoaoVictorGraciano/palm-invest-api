package com.palminvest.api.service;

import com.palminvest.api.dto.DadosCadastroProventoDTO;
import com.palminvest.api.dto.DadosResumoProventosDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.infra.RegraDeNegocioException;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Provento;
import com.palminvest.api.model.TipoProvento;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.ProventoRepository;
import com.palminvest.api.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

// Registro de proventos: sempre do usuário do token e só de ativos que ele já tinha na data do pagamento
@Service
public class ProventoService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Zero com 2 casas, igual às colunas numeric(19,2)
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // Mais recente primeiro; id desempata pagamentos no mesmo dia
    private static final Sort ORDEM = Sort.by(Sort.Order.desc("dataPagamento"), Sort.Order.desc("id"));

    @Autowired
    private ProventoRepository repository;

    @Autowired
    private TransactionRepository transactions;

    // ano e assetSymbol são filtros opcionais (null = sem filtro); assetSymbol já normalizado
    @Transactional(readOnly = true)
    public List<Provento> listar(Usuario usuario, Integer ano, String assetSymbol) {
        Specification<Provento> filtro = (provento, consulta, cb) -> cb.equal(provento.get("usuario"), usuario);
        if (ano != null) {
            filtro = filtro.and((provento, consulta, cb) -> cb.between(provento.get("dataPagamento"),
                    LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31)));
        }
        if (assetSymbol != null) {
            filtro = filtro.and((provento, consulta, cb) -> cb.equal(provento.get("assetSymbol"), assetSymbol));
        }
        return repository.findAll(filtro, ORDEM);
    }

    // Totais líquidos do ano: geral, por mês (os 12), por ativo (maior primeiro) e por tipo (os 4)
    @Transactional(readOnly = true)
    public DadosResumoProventosDTO resumir(Usuario usuario, int ano) {
        var proventos = listar(usuario, ano, null); // mais recente primeiro: a classe por ativo fica a do último pagamento

        var porMes = new BigDecimal[12];
        Arrays.fill(porMes, ZERO);
        var porTipo = new EnumMap<TipoProvento, BigDecimal>(TipoProvento.class);
        for (var tipo : TipoProvento.values()) {
            porTipo.put(tipo, ZERO);
        }
        var porAtivo = new LinkedHashMap<String, BigDecimal>();
        var classePorAtivo = new LinkedHashMap<String, AssetClass>();
        var totalBruto = ZERO;
        var totalIr = ZERO;

        for (var provento : proventos) {
            var liquido = provento.getValorLiquido();
            totalBruto = totalBruto.add(provento.getValorBruto());
            totalIr = totalIr.add(provento.getIrRetido());
            int mes = provento.getDataPagamento().getMonthValue() - 1;
            porMes[mes] = porMes[mes].add(liquido);
            porTipo.merge(provento.getTipo(), liquido, BigDecimal::add);
            porAtivo.merge(provento.getAssetSymbol(), liquido, BigDecimal::add);
            classePorAtivo.putIfAbsent(provento.getAssetSymbol(), provento.getAssetClass());
        }

        return new DadosResumoProventosDTO(
                ano, totalBruto, totalIr, totalBruto.subtract(totalIr),
                IntStream.range(0, 12).mapToObj(i -> new DadosResumoProventosDTO.TotalPorMes(i + 1, porMes[i])).toList(),
                porAtivo.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .map(e -> new DadosResumoProventosDTO.TotalPorAtivo(e.getKey(), classePorAtivo.get(e.getKey()), e.getValue()))
                        .toList(),
                porTipo.entrySet().stream().map(e -> new DadosResumoProventosDTO.TotalPorTipo(e.getKey(), e.getValue())).toList());
    }

    @Transactional
    public Provento criar(DadosCadastroProventoDTO dados, Usuario usuario) {
        var classe = validar(dados, usuario);
        return repository.save(new Provento(usuario, dados.assetSymbol(), classe, dados.tipo(), dados.dataPagamento(),
                dados.valorBruto(), dados.irRetido(), dados.observacao()));
    }

    // Vazio se o provento não existe ou é de outro usuário (o controller devolve 404)
    @Transactional
    public Optional<Provento> atualizar(Long id, DadosCadastroProventoDTO dados, Usuario usuario) {
        return repository.findByIdAndUsuario(id, usuario).map(provento -> {
            var classe = validar(dados, usuario);
            provento.atualizar(dados.assetSymbol(), classe, dados.tipo(), dados.dataPagamento(),
                    dados.valorBruto(), dados.irRetido(), dados.observacao());
            return provento;
        });
    }

    @Transactional
    public boolean excluir(Long id, Usuario usuario) {
        var provento = repository.findByIdAndUsuario(id, usuario);
        provento.ifPresent(repository::delete);
        return provento.isPresent();
    }

    /**
     * IR retido não pode passar do bruto (400) e o usuário precisa ter alguma transação do ativo até o fim do dia
     * do pagamento (422). Devolve a classe do ativo, copiada da transação mais recente até essa data.
     */
    private AssetClass validar(DadosCadastroProventoDTO dados, Usuario usuario) {
        if (dados.irRetido().compareTo(dados.valorBruto()) > 0) {
            throw new ParametroInvalidoException("irRetido", "não pode ser maior que o valor bruto");
        }
        var fimDoDia = dados.dataPagamento().plusDays(1).atStartOfDay();
        return transactions
                .findFirstByUsuarioAndAssetSymbolAndTransactionDateBeforeOrderByTransactionDateDescIdDesc(usuario, dados.assetSymbol(), fimDoDia)
                .map(transacao -> transacao.getAssetClass())
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Você não tem transação de %s até %s: registre a compra do ativo antes do provento".formatted(
                                dados.assetSymbol(), FORMATO_DATA.format(dados.dataPagamento()))));
    }
}
