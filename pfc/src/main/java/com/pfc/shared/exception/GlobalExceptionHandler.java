package com.pfc.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Tradução central de exceções para respostas {@link ProblemDetail} (RFC 7807).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que as exceções padrão
 * do Spring MVC (método não suportado, rota inexistente, mídia não suportada
 * etc.) mantenham seus status corretos (405, 404, 415, ...) já no formato
 * {@code ProblemDetail}, em vez de serem encaminhadas ao endpoint
 * {@code /error} — que, por estar atrás de autenticação, devolveria um 401
 * enganoso ao cliente (ver SECURITY.md &gt; "Exposição de dados e logs").
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Cobre falhas de autenticação lançadas dentro do fluxo normal do
     * controller (ex.: {@code BadCredentialsException} do
     * {@code AuthenticationManager} em {@code AuthService.login}). Exceções
     * equivalentes lançadas dentro da cadeia de filtros do Spring Security
     * (token ausente/expirado) não chegam aqui — são tratadas por
     * {@code RestAuthenticationEntryPoint}, no mesmo formato.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * Valor de parâmetro/variável de path incompatível com o tipo esperado
     * (ex.: um id que não é um UUID válido). Responde 400 identificando apenas
     * o nome do parâmetro — não expõe o detalhe interno de conversão.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    /**
     * Rede de segurança para qualquer exceção não prevista: responde 500 com
     * mensagem genérica (nunca stacktrace nem detalhe interno) e registra o
     * erro no log do servidor para diagnóstico. As exceções conhecidas do
     * domínio e do Spring MVC são tratadas pelos handlers acima / pela
     * superclasse, então só o realmente inesperado chega aqui.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Exceção não tratada alcançou o GlobalExceptionHandler", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Sobrescreve o tratamento padrão de validação de body para anexar a lista
     * de mensagens por campo na propriedade {@code errors} do ProblemDetail,
     * preservando o contrato já existente.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Corpo de requisição ausente, malformado ou não-parseável (ex.: JSON
     * inválido). Responde 400 com mensagem genérica — o motivo técnico exato
     * do parser não é devolvido ao cliente.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Malformed or unreadable request body");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}
