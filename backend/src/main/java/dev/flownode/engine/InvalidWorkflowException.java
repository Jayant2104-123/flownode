package dev.flownode.engine;

public class InvalidWorkflowException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidWorkflowException(String message) {
        super(message);
    }
}
