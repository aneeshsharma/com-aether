package aether.exceptions;

public class FileDecryptionError extends Exception {
    public FileDecryptionError() {
        super();
    }

    public FileDecryptionError(String message) {
        super(message);
    }

    public FileDecryptionError(String message, Throwable cause) {
        super(message, cause);
    }

    public FileDecryptionError(Throwable cause) {
        super(cause);
    }
}
