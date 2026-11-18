package com.palminvest.api.service;

import com.palminvest.api.dto.DadosCadastroProventoDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.infra.RegraDeNegocioException;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Provento;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.ProventoRepository;
import com.palminvest.api.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Registro de proventos: sempre do usuário do token e só de ativos que ele já tinha na data do pagamento
@Service
public class ProventoService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
