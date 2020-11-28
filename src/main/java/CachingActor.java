import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;

public class CachingActor extends AbstractActor {
    private final HashMap<String, Long> storage = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(GetMessage.class, msg -> {
                    getSender().tell(storage.getOrDefault(msg.getUrl(), (long)-1), ActorRef.noSender());
                })
                .match(StoreMessage.class, msg -> {
                    storage.putIfAbsent(msg.getUrl(), msg.getTime());
                })
                .build();
    }
}
