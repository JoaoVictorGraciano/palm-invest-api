package com.palminvest.api.repository;

import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Só as transações do usuário logado, da mais recente para a mais antiga (id desempata datas iguais)
    List<Transaction> findAllByUsuarioOrderByTransactionDateDescIdDesc(Usuario usuario);

    // Histórico de um ativo do usuário, para o cálculo de saldo (a ordenação é feita no serviço)
    List<Transaction> findAllByUsuarioAndAssetSymbol(Usuario usuario, String assetSymbol);

    // Apaga todas as transações de um ativo do usuário e devolve quantas foram; roda dentro da transação do serviço
    long deleteByUsuarioAndAssetSymbol(Usuario usuario, String assetSymbol);

    // Transação mais recente do ativo antes de um instante: prova que o usuário tinha o ativo e dá a classe do provento
    Optional<Transaction> findFirstByUsuarioAndAssetSymbolAndTransactionDateBeforeOrderByTransactionDateDescIdDesc(
            Usuario usuario, String assetSymbol, LocalDateTime antesDe);

    // Classe registrada pelo usuário para cada símbolo pedido (define se a cotação vem da Brapi ou da CoinGecko)
    @Query("select distinct new com.palminvest.api.repository.SimboloClasse(t.assetSymbol, t.assetClass) "
            + "from Transaction t where t.usuario = :usuario and t.assetSymbol in :simbolos")
    List<SimboloClasse> findClassesDosSimbolos(@Param("usuario") Usuario usuario, @Param("simbolos") Collection<String> simbolos);

    // Vazio se a transação não existe OU pertence a outro usuário (o controller devolve 404 nos dois casos)
    Optional<Transaction> findByIdAndUsuario(Long id, Usuario usuario);
}
