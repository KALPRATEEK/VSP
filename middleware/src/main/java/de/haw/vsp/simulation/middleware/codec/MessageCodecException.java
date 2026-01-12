package de.haw.vsp.simulation.middleware.codec;

/**
 * Thrown when a message cannot be encoded/decoded.
 */
public class MessageCodecException extends RuntimeException {

    public MessageCodecException(String message) {
        super(message);
    }

    public MessageCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
