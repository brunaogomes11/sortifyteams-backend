package com.gomesdev.sortifyteams.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ErrorDetails {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> tratarErro404(EntityNotFoundException ex) {
        var msg = ex.getMessage() != null ? ex.getMessage() : "Recurso não encontrado.";
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(List.of(msg)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> tratarErro409(DataIntegrityViolationException ex) {
        var msg = ex.getMessage() != null ? ex.getMessage() : "Violação de integridade dos dados.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(List.of(msg)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> tratarErro403(AccessDeniedException ex) {
        // O default do Spring Security vem em inglês ("Access Denied").
        var msg = ex.getMessage() == null || "Access Denied".equals(ex.getMessage())
                ? "Acesso negado."
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(List.of(msg)));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> tratarCredenciaisInvalidas(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(List.of("Usuário ou senha incorretos.")));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> tratarErro401(AuthenticationException ex) {
        var msg = ex.getMessage() != null ? ex.getMessage() : "Não autenticado.";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(List.of(msg)));
    }

    /**
     * Corpo que o Jackson não conseguiu ler (data "25/12/2026", horário "25:00",
     * enum minúsculo...) é erro do cliente — sem isto cairia no handler genérico
     * como 500 "Erro interno inesperado".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> tratarCorpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(List.of(mensagemDeFormato(ex))));
    }

    private String mensagemDeFormato(HttpMessageNotReadableException ex) {
        // Spring Boot 4 converte HTTP com Jackson 3 (tools.jackson); o Jackson 2
        // (com.fasterxml) segue no classpath para outros usos — trata os dois.
        for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof tools.jackson.databind.exc.InvalidFormatException formato) {
                String campo = formato.getPath().stream()
                        .map(ref -> ref.getPropertyName())
                        .filter(nome -> nome != null)
                        .collect(Collectors.joining("."));
                return dicaDeFormato(campo, formato.getTargetType());
            }
            if (causa instanceof InvalidFormatException formato) {
                String campo = formato.getPath().stream()
                        .map(ref -> ref.getFieldName())
                        .filter(nome -> nome != null)
                        .collect(Collectors.joining("."));
                return dicaDeFormato(campo, formato.getTargetType());
            }
        }
        return "Corpo da requisição inválido ou mal formatado.";
    }

    private String dicaDeFormato(String campo, Class<?> alvo) {
        String prefixo = campo == null || campo.isEmpty() ? "" : campo + ": ";
        if (alvo == LocalDate.class) {
            return prefixo + "data em formato inválido — use AAAA-MM-DD (ex.: 2026-12-25).";
        }
        if (alvo == LocalTime.class) {
            return prefixo + "horário em formato inválido — use HH:MM (ex.: 19:00).";
        }
        if (alvo != null && alvo.isEnum()) {
            String aceitos = java.util.Arrays.stream(alvo.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return prefixo + "valor inválido — valores aceitos: " + aceitos + ".";
        }
        return prefixo + "valor em formato inválido.";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.add(err.getField() + ": " + err.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(err -> errors.add(err.getObjectName() + ": " + err.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(errors));
    }

    @ExceptionHandler(com.gomesdev.sortifyteams.domain.reserva.ConflitoHorarioException.class)
    public ResponseEntity<ErrorResponse> tratarConflitoHorario(
            com.gomesdev.sortifyteams.domain.reserva.ConflitoHorarioException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(List.of(ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> tratarErro400(IllegalArgumentException ex) {
        var msg = ex.getMessage() != null ? ex.getMessage() : "Operação inválida.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(List.of(msg)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        List<String> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v -> errors.add(v.getPropertyPath() + ": " + v.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(errors));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> tratarRotaInexistente() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(List.of("Recurso não encontrado.")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> tratarErro500(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(List.of("Erro interno inesperado.")));
    }

    public static class ErrorResponse {
        private OffsetDateTime timestamp = OffsetDateTime.now();
        private List<String> message;

        public ErrorResponse(List<String> message) {
            this.message = message;
        }

        public List<String> getMessage() {
            return message;
        }

        public void setMessage(List<String> message) {
            this.message = message;
        }

        public OffsetDateTime getTimestamp() {
            return timestamp;
        }
    }
}
