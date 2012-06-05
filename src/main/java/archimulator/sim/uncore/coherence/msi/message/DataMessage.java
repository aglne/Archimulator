package archimulator.sim.uncore.coherence.msi.message;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.msi.controller.Controller;
import archimulator.sim.uncore.coherence.msi.flow.CacheCoherenceFlow;

public class DataMessage extends CoherenceMessage {
    private Controller sender;
    private int tag;
    private int numAcks;

    public DataMessage(Controller generator, CacheCoherenceFlow producerFlow, Controller sender, int tag, int numAcks, MemoryHierarchyAccess access) {
        super(generator, producerFlow, CoherenceMessageType.DATA, access);
        this.sender = sender;
        this.tag = tag;
        this.numAcks = numAcks;
    }

    public Controller getSender() {
        return sender;
    }

    public int getTag() {
        return tag;
    }

    public int getNumAcks() {
        return numAcks;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: DataMessage{%d, sender=%s, tag=0x%08x, numAcks=%d}", getBeginCycle(), getGenerator(), getId(), sender, tag, numAcks);
    }
}
