package example.cafe;

import cz.insuit.moneta.MonetaException;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /** The token and {@code cafe.iban} do not match: the café's setup, not the request and not MONETA, so a 503. */
    @ExceptionHandler(CafeAccount.NotOpened.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    String notOpened(CafeAccount.NotOpened e) {
        return String.valueOf(e.getMessage());
    }

    /** Someone else changed the same row first, as linking a payment by hand while a round books it. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String changedMeanwhile(OptimisticLockingFailureException e) {
        return "changed meanwhile; read it again and retry";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return String.valueOf(e.getMessage());
    }
}
