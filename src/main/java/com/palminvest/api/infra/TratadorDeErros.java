package com.palminvest.api.infra;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class TratadorDeErros {

    // @Valid falhou: devolve 400 com cada campo inválido e o motivo
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<DadosErroValidacao>> tratarErro400(MethodArgumentNotValidException ex) {
        var erros = ex.getFieldErrors().stream().map(DadosErroValidacao::new).toList();
        return ResponseEntity.badRequest().body(erros);
    }

    // JSON malformado ou tipo errado (ex: data inválida, texto no lugar de número)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DadosErro> tratarJsonInvalido() {
        return ResponseEntity.badRequest().body(new DadosErro("Corpo da requisição inválido"));
    }

    // Regra de negócio violada (ex: e-mail já cadastrado): 409 com a mensagem
    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<DadosErro> tratarConflito(ConflitoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DadosErro(ex.getMessage()));
    }

    // Restrição do banco violada (ex: duas requisições cadastrando o mesmo e-mail ao mesmo tempo)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<DadosErro> tratarViolacaoDeIntegridade() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DadosErro("Os dados conflitam com um registro existente"));
    }

    private record DadosErroValidacao(String campo, String mensagem) {
        DadosErroValidacao(FieldError erro) {
            this(erro.getField(), erro.getDefaultMessage());
        }
    }

    private record DadosErro(String mensagem) {}
}
