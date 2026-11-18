package com.palminvest.api.service;

import com.palminvest.api.dto.DadosCadastroTransacaoDTO;
import com.palminvest.api.dto.DadosDetalhamentoTransacaoDTO;
import com.palminvest.api.dto.DadosExclusaoPorAtivoDTO;
import com.palminvest.api.infra.RegraDeNegocioException;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.TransactionType;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.ProventoRepository;
import com.palminvest.api.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

// Toda escrita de transação passa por aqui: valida o saldo do ativo antes de salvar, editar ou excluir
@Service
public class TransactionService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private ProventoRepository proventos;

    // Transações do usuário (mais recente primeiro), com o lucro realizado de cada venda
    @Transactional(readOnly = true)
    public List<DadosDetalhamentoTransacaoDTO> listar(Usuario usuario) {
        var transacoes = repository.findAllByUsuarioOrderByTransactionDateDescIdDesc(usuario);
        var lucros = LinhaDoTempo.lucroPorVenda(transacoes);
        return transacoes.stream().map(t -> new DadosDetalhamentoTransacaoDTO(t, lucros.get(t.getId()))).toList();
    }

    @Transactional
    public DadosDetalhamentoTransacaoDTO criar(DadosCadastroTransacaoDTO dados, Usuario usuario) {
        var nova = new Transaction(dados, usuario);
        validarSaldo(usuario, nova.getAssetSymbol(), null, nova);
        return detalhar(repository.save(nova), usuario);
    }

    // Vazio se a transação não existe ou é de outro usuário (o controller devolve 404)
    @Transactional
    public Optional<DadosDetalhamentoTransacaoDTO> atualizar(Long id, DadosCadastroTransacaoDTO dados, Usuario usuario) {
        return atualizarEntidade(id, dados, usuario).map(transacao -> detalhar(transacao, usuario));
    }

    private Optional<Transaction> atualizarEntidade(Long id, DadosCadastroTransacaoDTO dados, Usuario usuario) {
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

    // Remove o ativo inteiro (compras, vendas e proventos) numa única transação de banco: ou apaga tudo, ou nada.
    // Não precisa validar saldo, porque a linha do tempo do símbolo fica vazia. Devolve quanto apagou de cada.
    @Transactional
    public DadosExclusaoPorAtivoDTO excluirPorSimbolo(String assetSymbol, Usuario usuario) {
        var simbolo = assetSymbol.trim().toUpperCase(Locale.ROOT);
        long proventosExcluidos = proventos.deleteByUsuarioAndAssetSymbol(usuario, simbolo);
        long excluidas = repository.deleteByUsuarioAndAssetSymbol(usuario, simbolo);
        return new DadosExclusaoPorAtivoDTO(simbolo, excluidas, proventosExcluidos);
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

        // Mesma ordem do cálculo de lucro: data, compra antes da venda no mesmo instante, id
        var saldo = BigDecimal.ZERO;
        for (var t : LinhaDoTempo.ordenar(linhaDoTempo)) {
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

    // DTO de uma transação com o lucro realizado, recalculado sobre o histórico do ativo já com ela gravada
    private DadosDetalhamentoTransacaoDTO detalhar(Transaction transacao, Usuario usuario) {
        var lucros = LinhaDoTempo.lucroPorVenda(repository.findAllByUsuarioAndAssetSymbol(usuario, transacao.getAssetSymbol()));
        return new DadosDetalhamentoTransacaoDTO(transacao, lucros.get(transacao.getId()));
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
