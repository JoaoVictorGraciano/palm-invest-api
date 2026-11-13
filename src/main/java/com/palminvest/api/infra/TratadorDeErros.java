package com.palminvest.api.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    // @Valid falhou: devolve 400 com cada campo inválido e o motivo
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<DadosErroValidacao>> tratarErro400(MethodArgumentNotValidException ex) {
        var erros = ex.getFieldErrors().stream().map(DadosErroValidacao::new).toList();
        return ResponseEntity.badRequest().body(erros);
    }

    // JSON malformado ou tipo errado (ex: data inválida, texto no lugar de número).
    // Quando o Jackson diz qual campo falhou, devolve no mesmo formato da validação,
    // listando os valores aceitos se o campo for um enum (ex: type: use BUY ou SELL)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> tratarJsonInvalido(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException causa && !causa.getPath().isEmpty()) {
            var campo = causa.getPath().get(causa.getPath().size() - 1).getPropertyName();
            if (campo != null) {
                var mensagem = causa.getTargetType() != null && causa.getTargetType().isEnum()
                        ? "valor inválido; use " + String.join(", ", valoresDoEnum(causa.getTargetType()))
                        : "valor inválido";
                return ResponseEntity.badRequest().body(List.of(new DadosErroValidacao(campo, mensagem)));
            }
        }
        return ResponseEntity.badRequest().body(new DadosErro("Corpo da requisição inválido"));
    }

    private static List<String> valoresDoEnum(Class<?> tipoEnum) {
        return Arrays.stream(tipoEnum.getEnumConstants()).map(Object::toString).toList();
    }

    // Path ou query param com tipo errado (ex: /api/transactions/abc): 400 no mesmo formato da validação
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<List<DadosErroValidacao>> tratarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(List.of(new DadosErroValidacao(ex.getName(), "valor inválido")));
    }

    // Query param obrigatório ausente
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<List<DadosErroValidacao>> tratarParametroAusente(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(List.of(new DadosErroValidacao(ex.getParameterName(), "parâmetro obrigatório")));
    }

    // Login com e-mail ou senha errados: a exceção nasce dentro do controller, então precisa
    // ser tratada aqui antes do handler genérico, senão viraria 500
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<DadosErro> tratarFalhaDeAutenticacao() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new DadosErro("E-mail ou senha inválidos"));
    }

    // Rota inexistente (só chega aqui se a requisição passou pela autenticação)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<DadosErro> tratarRotaInexistente() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DadosErro("Rota não encontrada"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<DadosErro> tratarMetodoNaoSuportado() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new DadosErro("Método não suportado"));
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

    // Qualquer outra falha: 500 com mensagem genérica; o detalhe fica só no log do servidor
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DadosErro> tratarErroInesperado(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new DadosErro("Erro interno no servidor"));
    }

    private record DadosErroValidacao(String campo, String mensagem) {
        DadosErroValidacao(FieldError erro) {
            this(erro.getField(), erro.getDefaultMessage());
        }
    }

    private record DadosErro(String mensagem) {}
}
