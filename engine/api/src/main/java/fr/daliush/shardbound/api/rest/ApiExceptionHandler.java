package fr.daliush.shardbound.api.rest;

import fr.daliush.shardbound.api.session.SessionException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error as RFC 9457 Problem Details: Spring's own (validation, unreadable body) and the session's. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail refused(SessionException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception) {
            case SessionException.UnknownGame ignored -> HttpStatus.NOT_FOUND;
            case SessionException.InvalidRequest ignored -> HttpStatus.BAD_REQUEST;
            case SessionException.JoinRefused ignored -> HttpStatus.CONFLICT;
            case SessionException.InvalidToken ignored -> HttpStatus.UNAUTHORIZED;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
