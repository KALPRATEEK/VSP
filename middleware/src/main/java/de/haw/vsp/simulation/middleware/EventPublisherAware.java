package de.haw.vsp.simulation.middleware;

import de.haw.vsp.simulation.core.SimulationEventPublisher;

/** Allows attaching / replacing the event publisher after construction. */
public interface EventPublisherAware {
    void setEventPublisher(SimulationEventPublisher publisher);
}
