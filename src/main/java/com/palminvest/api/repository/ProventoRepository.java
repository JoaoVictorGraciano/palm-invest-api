package com.palminvest.api.repository;

import com.palminvest.api.model.Provento;
import com.palminvest.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

// Specification para os filtros opcionais do GET (ano, assetSymbol) sem parâmetros nulos na consulta,
// que o PostgreSQL não consegue tipar
public interface ProventoRepository extends JpaRepository<Provento, Long>, JpaSpecificationExecutor<Provento> {

    // Vazio se o provento não existe OU pertence a outro usuário (o controller devolve 404 nos dois casos)
    Optional<Provento> findByIdAndUsuario(Long id, Usuario usuario);
}
