import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;

public class TesterActor extends AbstractActor {
    private final HashMap<String, Integer> storage = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(GetMessage.class)
    }
}
