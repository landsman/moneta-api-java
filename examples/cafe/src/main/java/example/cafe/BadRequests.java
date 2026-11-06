package example.cafe;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A request the café refuses: an unknown period, half a range, an amount it cannot bill. */
@RestControllerAdvice
class BadRequests {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return String.valueOf(e.getMessage());
    }
}
