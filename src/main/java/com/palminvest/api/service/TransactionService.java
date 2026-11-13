package com.palminvest.api.service;

import com.palminvest.api.dto.DadosCadastroTransacaoDTO;
import com.palminvest.api.infra.RegraDeNegocioException;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.TransactionType;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Toda escrita de transação passa por aqui: valida o saldo do ativo antes de salvar, editar ou excluir
@Service
public class TransactionService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private TransactionRepository repository;

    @Transactional(readOnly = true)
    public List<Transaction> listar(Usuario usuario) {
        return repository.findAllByUsuarioOrderByTransactionDateDescIdDesc(usuario);
    }

    @Transactional
    public Transaction criar(DadosCadastroTransacaoDTO dados, Usuario usuario) {
        var nova = new Transaction(dados, usuario);
        validarSaldo(usuario, nova.getAssetSymbol(), null, nova);
        return repository.save(nova);
    }

    // Vazio se a transação não existe ou é de outro usuário (o controller devolve 404)
    @Transactional
    public Optional<Transaction> atualizar(Long id, DadosCadastroTransacaoDTO dados, Usuario usuario) {
        return repository.findByIdAndUsuario(id, usuario).map(existente -> {
            var simboloAntigo = existente.getAssetSymbol();
            var editada = copiaComDados(existente, dados, usuario);

            // Simula a linha do tempo do símbolo novo sem a versão antiga e com a editada
            validarSaldo(usuario, editada.getAssetSymbol(), id, editada);
            // Se o símbolo mudou, o antigo perde esta transação: as vendas dele também precisam continuar cobertas
            if (!simboloAntigo.equals(editada.getAssetSymbol())) {
                validarSaldo(usuario, simboloAntigo, id, null);
            }

            existente.atualizarInformacoes(dados);
            return repository.save(existente);
        });
    }

    @Transactional
    public boolean excluir(Long id, Usuario usuario) {
        var encontrada = repository.findByIdAndUsuario(id, usuario);
        if (encontrada.isEmpty()) {
            return false;
        }
        var transaction = encontrada.get();
        validarSaldo(usuario, transaction.getAssetSymbol(), id, null);
        repository.delete(transaction);
        return true;
    }

    /**
     * Percorre o histórico do ativo em ordem cronológica, como ele ficaria após a operação,
     * e falha se em algum momento a quantidade em carteira ficar negativa.
     *
     * @param idIgnorado  transação que sai da linha do tempo (a que está sendo editada ou excluída)
     * @param adicional   transação que entra na linha do tempo (a nova ou a versão editada); pode ser null
     */
    private void validarSaldo(Usuario usuario, String assetSymbol, Long idIgnorado, Transaction adicional) {
        var linhaDoTempo = new ArrayList<Transaction>();
        for (var t : repository.findAllByUsuarioAndAssetSymbol(usuario, assetSymbol)) {
            if (!Objects.equals(t.getId(), idIgnorado)) {
                linhaDoTempo.add(t);
            }
        }
        if (adicional != null) {
            linhaDoTempo.add(adicional);
        }

        // Mesma data: compra antes da venda (permite comprar e vender no mesmo instante); id desempata o resto
        linhaDoTempo.sort(Comparator.comparing(Transaction::getTransactionDate)
                .thenComparing(t -> t.getType() == TransactionType.SELL ? 1 : 0)
                .thenComparing(Transaction::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        var saldo = BigDecimal.ZERO;
        for (var t : linhaDoTempo) {
            if (t.getType() == TransactionType.SELL) {
                if (saldo.compareTo(t.getQuantity()) < 0) {
                    throw new RegraDeNegocioException("Saldo insuficiente de %s em %s: disponível %s, venda de %s".formatted(
                            assetSymbol, FORMATO_DATA.format(t.getTransactionDate()), plain(saldo), plain(t.getQuantity())));
                }
                saldo = saldo.subtract(t.getQuantity());
            } else {
                saldo = saldo.add(t.getQuantity());
            }
        }
    }

    // Versão em memória da transação com os dados novos, mantendo o id para o desempate da ordenação
    private static Transaction copiaComDados(Transaction original, DadosCadastroTransacaoDTO dados, Usuario usuario) {
        var copia = new Transaction(dados, usuario);
        copia.setId(original.getId());
        return copia;
    }

    private static String plain(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

}
