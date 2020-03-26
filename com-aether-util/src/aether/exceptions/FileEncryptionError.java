package aether.exceptions;

public class FileEncryptionError extends Exception {
    public FileEncryptionError() {
        super();
    }

    public FileEncryptionError(String message) {
        super(message);
    }

    public FileEncryptionError(String message, Throwable cause) {
        super(message, cause);
    }

    public FileEncryptionError(Throwable cause) {
        super(cause);
    }
}
