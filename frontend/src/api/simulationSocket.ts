// api/simulationSocket.ts

export function connectSimulationSocket<T>(
    simulationId: string,
    onMessage: (payload: T) => void
): WebSocket {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";

    const socket = new WebSocket(
        `${protocol}://localhost:8080/ws/simulations/${simulationId}`
    );

    socket.onmessage = (message) => {
        onMessage(JSON.parse(message.data) as T);
    };

    socket.onerror = (e) => {
        console.error("WebSocket error", e);
    };

    return socket;
}

