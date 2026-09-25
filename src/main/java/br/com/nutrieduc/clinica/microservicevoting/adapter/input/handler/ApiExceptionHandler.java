package br.com.nutrieduc.clinica.microservicevoting.adapter.input.handler;

import java.time.Clock;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import br.com.nutrieduc.clinica.microservicevoting.adapter.input.response.ApiErrorResponse;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.AgendaNotFoundException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.DuplicateVoteException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.InvalidVotingRequestException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionAlreadyExistsException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionClosedException;
import br.com.nutrieduc.clinica.microservicevoting.core.exception.VotingSessionNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({AgendaNotFoundException.class, VotingSessionNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException exception, WebRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler({DuplicateVoteException.class, VotingSessionAlreadyExistsException.class,
            VotingSessionClosedException.class})
    public ResponseEntity<Object> handleConflict(RuntimeException exception, WebRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(InvalidVotingRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidVotingRequestException exception, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted().collect(Collectors.joining("; "));
        return error(status, message, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = status.value() == 400
                ? "Invalid request. Check JSON, field types and identifiers; choice must be YES or NO."
                : HttpStatus.valueOf(status.value()).getReasonPhrase();
        return error(status, message, request, headers);
    }

    private ResponseEntity<Object> error(HttpStatusCode status, String message, WebRequest request, HttpHeaders headers) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        ApiErrorResponse body = new ApiErrorResponse(clock.instant(), status.value(),
                HttpStatus.valueOf(status.value()).getReasonPhrase(), message, path);
        return new ResponseEntity<>(body, headers, status);
    }
}
