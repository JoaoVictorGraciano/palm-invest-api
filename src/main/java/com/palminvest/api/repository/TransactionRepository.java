package com.palminvest.api.repository;

import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Só as transações do usuário logado, da mais recente para a mais antiga (id desempata datas iguais)
    List<Transaction> findAllByUsuarioOrderByTransactionDateDescIdDesc(Usuario usuario);

    // Vazio se a transação não existe OU pertence a outro usuário (o controller devolve 404 nos dois casos)
    Optional<Transaction> findByIdAndUsuario(Long id, Usuario usuario);
}
