package aether.exceptions;

public class LogoutError extends Exception{
    public LogoutError() {
        super();
    }

    public LogoutError(String message) {
        super(message);
    }

    public LogoutError(String message, Throwable cause) {
        super(message, cause);
    }

    public LogoutError(Throwable cause) {
        super(cause);
    }
}
