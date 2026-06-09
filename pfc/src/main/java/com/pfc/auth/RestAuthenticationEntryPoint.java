package com.pfc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Traduz falhas de autenticação (token ausente, inválido, expirado ou
 * credenciais incorretas) em {@code 401} no formato {@link ProblemDetail}
 * (RFC 7807) — o mesmo formato usado por {@code GlobalExceptionHandler}.
 *
 * <p>Necessário porque exceções lançadas dentro da cadeia de filtros do
 * Spring Security ocorrem antes do {@code DispatcherServlet}: o
 * {@code @RestControllerAdvice} não as alcança, e sem este componente o
 * Spring Security devolveria sua página/JSON padrão, quebrando a
 * consistência do formato de erro da API.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
