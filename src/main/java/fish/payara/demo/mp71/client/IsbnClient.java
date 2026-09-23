package fish.payara.demo.mp71.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Type-safe REST Client for an external ISBN lookup service.
 *
 * <h3>[MP 7.1 / REST Client 4.0] Key features shown here</h3>
 * <ul>
 *   <li><b>configKey</b> — short, stable key used in microprofile-config.properties instead
 *       of the fully-qualified interface name.  Both styles work, but configKey is cleaner
 *       for long interface names.</li>
 *   <li><b>@ClientHeaderParam with a compute method</b> — generates a header value by
 *       calling a static method at request time.  This avoids implementing a full
 *       {@code ClientRequestFilter} for simple, static-ish header values.</li>
 * </ul>
 *
 * <pre>
 * [CONTRAST — MP 6.1 / REST Client 3.0]
 * Dynamic headers required a ClientRequestFilter registered with @RegisterClientHeaders:
 *
 *   @RegisterClientHeaders(VersionHeaderFactory.class)        // factory class
 *   @RegisterRestClient(baseUri = "https://openlibrary.org")  // hardcoded URI in annotation
 *   public interface IsbnClient { ... }
 *
 *   public class VersionHeaderFactory implements ClientHeadersFactory {
 *     public MultivaluedMap<String,String> update(...) {
 *       map.putSingle("X-Request-Source", "payara-bookstore/1.0");
 *       return map;
 *     }
 *   }
 * </pre>
 *
 * <p>Config properties read by this client:</p>
 * <pre>
 *   isbn-lookup-api/mp-rest/url=https://openlibrary.org
 *   isbn-lookup-api/mp-rest/connectTimeout=2000
 *   isbn-lookup-api/mp-rest/readTimeout=3000
 * </pre>
 */
@RegisterRestClient(configKey = "isbn-lookup-api")
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/books")
public interface IsbnClient {

    /**
     * Look up a single book by ISBN-13.
     *
     * [REST Client 4.0] @ClientHeaderParam value references a static method by
     * fully-qualified name in curly braces.  The method is called at request time,
     * so it can include version strings, request IDs, or other computed values.
     */
    @GET
    @Path("/{isbn}")
    @ClientHeaderParam(name = "X-Request-Source",
                       value = "{fish.payara.demo.mp71.client.IsbnClient.requestSource}")
    @ClientHeaderParam(name = "Accept-Language", value = "en")
    IsbnLookupResult findByIsbn(@PathParam("isbn") String isbn);

    /** Compute method referenced by @ClientHeaderParam above. */
    static String requestSource() {
        return "payara-bookstore/1.0";
    }

    /*
     * [REST Client 4.0] EntityPart — portable multipart type (Jakarta EE 10).
     *
     * Shown here as a commented example of the interface pattern.
     * The actual cover upload endpoint is on BookResource (POST /api/books/{isbn}/cover)
     * to keep this client focused on the external ISBN service.
     *
     * [CONTRAST — MP 6.1 / REST Client 3.0] Multipart required vendor-specific types:
     *   @POST @Path("/{isbn}/cover") @Consumes(MULTIPART_FORM_DATA)
     *   Response upload(@PathParam("isbn") String isbn,
     *                   MultipartInput input);       // RESTEasy-specific
     *   // or
     *   Response upload(@PathParam("isbn") String isbn,
     *                   FormDataBodyPart part);      // Jersey-specific
     *
     * [NEW — REST Client 4.0 / Jakarta EE 10]
     *   @POST @Path("/{isbn}/cover") @Consumes(MULTIPART_FORM_DATA)
     *   Response uploadCoverImage(@PathParam("isbn") String isbn,
     *                             List<EntityPart> parts);   // portable
     */
}
