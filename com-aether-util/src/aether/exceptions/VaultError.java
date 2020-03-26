package aether.exceptions;

public class VaultError extends Exception{
    public VaultError() {
        super();
    }

    public VaultError(String message) {
        super(message);
    }

    public VaultError(String message, Throwable cause) {
        super(message, cause);
    }

    public VaultError(Throwable cause) {
        super(cause);
    }
}
