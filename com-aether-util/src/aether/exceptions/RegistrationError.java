package aether.exceptions;

public class RegistrationError extends Exception{
    public RegistrationError() {
        super();
    }

    public RegistrationError(String message) {
        super(message);
    }

    public RegistrationError(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistrationError(Throwable cause) {
        super(cause);
    }
}
