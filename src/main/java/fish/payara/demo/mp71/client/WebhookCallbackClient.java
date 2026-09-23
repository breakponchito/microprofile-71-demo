package fish.payara.demo.mp71.client;

import fish.payara.demo.mp71.model.BookEvent;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client interface used to deliver BookEvent payloads to subscriber callback URLs.
 *
 * [MP 7.1 / REST Client 4.0] This interface is NOT injected via @RestClient —
 * it is built programmatically in BookWebhookNotifier using:
 *
 *   RestClientBuilder.newBuilder().baseUri(callbackUrl).build(WebhookCallbackClient.class)
 *
 * [CONTRAST — MP 6.1 / REST Client 3.0] baseUri() only accepted java.net.URI, requiring:
 *   RestClientBuilder.newBuilder().baseUri(URI.create(callbackUrl)).build(...)
 *
 * REST Client 4.0 adds baseUri(String) so URI.create() is no longer necessary.
 */
@RegisterRestClient
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface WebhookCallbackClient {

    @POST
    Response deliver(BookEvent event);
}