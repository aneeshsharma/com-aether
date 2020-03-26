package aether.exceptions;

public class NoSuchUserError extends Exception {
    public NoSuchUserError() {
        super();
    }

    public NoSuchUserError(String message) {
        super(message);
    }

    public NoSuchUserError(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchUserError(Throwable cause) {
        super(cause);
    }
}
