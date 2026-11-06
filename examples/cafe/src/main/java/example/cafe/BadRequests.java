package example.cafe;

import cz.landsman.moneta.MonetaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A request the café refuses, or one MONETA could not answer. */
@RestControllerAdvice
class BadRequests {

    /** MONETA did not answer as asked; the café's own state is fine, so this is the bank's side, a 502. */
    @ExceptionHandler(MonetaException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    String monetaFailed(MonetaException e) {
        return "MONETA: " + e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return String.valueOf(e.getMessage());
    }
}
