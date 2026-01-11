package de.haw.vsp.simulation.middleware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Host/port pair for reaching a node via a transport mechanism (e.g. UDP).
 */
public record TransportAddress(String host, int port) {

    @JsonCreator
    public TransportAddress(
            @JsonProperty("host") String host,
            @JsonProperty("port") int port
    ) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null/blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535, but was: " + port);
        }
        this.host = host;
        this.port = port;
    }
}
