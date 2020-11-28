import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import javafx.util.Pair;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class StressTester {
    private static final Timeout timeout = Timeout.create(Duration.ofSeconds(5));

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
        final Http http = Http.get(system);
        final ActorMaterializer materializer =
                ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createFlow(materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
    }

    private static Flow<HttpRequest, HttpResponse, NotUsed> createFlow(ActorMaterializer materializer,
                                                                       ActorRef cachingActor) {
        return Flow.of(HttpRequest.class)
                .map((req) -> {
                    Query q = req.getUri().query();
                    String url = q.get("testUrl").get();
                    int count = Integer.parseInt(q.get("count").get());
                    return new Pair<>(url, count);
                })
                .mapAsync(1, (Pair<String, Integer> p) -> {
                    CompletionStage<Object> stage = Patterns.ask(cachingActor, new GetMessage(p.getKey()), timeout);

                })
    }
}
