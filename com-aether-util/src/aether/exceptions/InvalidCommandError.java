package aether.exceptions;

public class InvalidCommandError extends Exception {
    public InvalidCommandError() {
        super();
    }

    public InvalidCommandError(String message) {
        super(message);
    }

    public InvalidCommandError(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidCommandError(Throwable cause) {
        super(cause);
    }
}
