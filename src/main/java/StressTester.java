import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import javafx.util.Pair;
import org.asynchttpclient.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class StressTester {
    private static final Duration timeout = Duration.ofSeconds(5);

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
        ActorRef cachingActor = system.actorOf(Props.create(CachingActor.class));
        final Http http = Http.get(system);
        final ActorMaterializer materializer =
                ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createFlow(materializer, cachingActor);
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
                    System.out.println("Started parsing request");
                    Query q = req.getUri().query();
                    System.out.println("Getting url");
                    String url = q.get("testUrl").get();
                    System.out.println("Getting count");
                    int count = Integer.parseInt(q.get("count").get());
                    System.out.println(url);
                    System.out.println(count);
                    return new Pair<>(url, count);
                })
                .mapAsync(1, (Pair<String, Integer> p) -> {
                    CompletionStage<Object> stage = Patterns.ask(cachingActor, new GetMessage(p.getKey()), timeout);
                    return stage.thenCompose((Object res) -> {
                        if ((long)res >= 0) {
                            return CompletableFuture.completedFuture(new Pair<>(p.getKey(), (long)res));
                        }
                        Flow<Pair<String, Integer>, Long, NotUsed> flow =
                                Flow.<Pair<String, Integer>>create()
                                .mapConcat(pair ->  new ArrayList<>(Collections.nCopies(pair.getValue(), pair.getKey())))
                                .mapAsync(p.getValue(), (String url) -> {
                                    long startTime = System.nanoTime();
                                    asyncHttpClient().prepareGet(url).execute();
                                    long stopTime = System.nanoTime();
                                    long execTime = stopTime - startTime;
                                    return CompletableFuture.completedFuture(execTime);
                                });
                        return Source.single(p)
                                .via(flow)
                                .toMat(Sink.fold((long)0, Long::sum), Keep.right())
                                .run(materializer)
                                .thenApply(sum -> new Pair<>(p.getKey(), sum/p.getValue()));
                    });
                })
                .map((Pair<String, Long> p) -> {
                    cachingActor.tell(new StoreMessage(p.getKey(), p.getValue()), ActorRef.noSender());
                    return HttpResponse.create().withEntity(p.getValue().toString());
                });
    }
}
