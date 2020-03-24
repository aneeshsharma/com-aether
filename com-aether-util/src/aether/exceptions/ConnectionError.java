package aether.exceptions;

public class ConnectionError extends Exception {
    public ConnectionError() {
        super();
    }

    public ConnectionError(String message) {
        super(message);
    }

    public ConnectionError(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionError(Throwable cause) {
        super(cause);
    }
}
