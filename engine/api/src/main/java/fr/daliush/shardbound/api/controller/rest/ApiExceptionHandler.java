package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.domain.bo.error.GameException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error as RFC 9457 Problem Details: Spring's own (validation, unreadable body) and the domain's. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    public ProblemDetail refused(GameException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception) {
            case GameException.UnknownGame ignored -> HttpStatus.NOT_FOUND;
            case GameException.InvalidRequest ignored -> HttpStatus.BAD_REQUEST;
            case GameException.JoinRefused ignored -> HttpStatus.CONFLICT;
            case GameException.InvalidToken ignored -> HttpStatus.UNAUTHORIZED;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
