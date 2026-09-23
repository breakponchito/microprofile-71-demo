package fish.payara.demo.mp71.service;

import fish.payara.demo.mp71.client.WebhookCallbackClient;
import fish.payara.demo.mp71.model.BookEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds registered webhook callback URLs and delivers BookEvent payloads asynchronously.
 *
 * [MP 7.1 / REST Client 4.0] Uses RestClientBuilder.newBuilder().baseUri(String) — the
 * new String overload added in REST Client 4.0 eliminates the URI.create() boilerplate
 * required in REST Client 3.0 (MP 6.1):
 *
 *   [OLD — MP 6.1] RestClientBuilder.newBuilder().baseUri(URI.create(url)).build(...)
 *   [NEW — MP 7.0] RestClientBuilder.newBuilder().baseUri(url).build(...)
 *
 * Webhook failures are silently swallowed — a subscriber going away must never break
 * the main catalog operation.
 */
@ApplicationScoped
public class BookWebhookNotifier {

    private final CopyOnWriteArrayList<String> callbackUrls = new CopyOnWriteArrayList<>();

    public void register(String callbackUrl) {
        callbackUrls.addIfAbsent(callbackUrl);
    }

    public void notifyAsync(BookEvent event) {
        for (String url : callbackUrls) {
            try {
                /*
                 * [REST Client 4.0] baseUri(String) — no URI.create() needed.
                 * Each call builds a short-lived client; in production prefer a
                 * shared executor or async @Asynchronous CDI method.
                 */
                WebhookCallbackClient client = RestClientBuilder.newBuilder()
                        .baseUri(url)
                        .build(WebhookCallbackClient.class);
                client.deliver(event);
            } catch (Exception ignored) {
                // Subscriber unreachable — log in production, skip here for demo clarity
            }
        }
    }

    public int registeredCount() {
        return callbackUrls.size();
    }
}