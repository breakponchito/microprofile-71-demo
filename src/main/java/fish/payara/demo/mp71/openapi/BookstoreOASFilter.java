package fish.payara.demo.mp71.openapi;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Programmatically adds the OAS 3.1 'webhooks' section to the generated document.
 *
 * [MP 7.1 / OpenAPI 4.1] MicroProfile OpenAPI 4.x exposes the OAS 3.1 webhooks map
 * via OpenAPI.addWebhook(name, PathItem).  There is no annotation shorthand — the
 * programmatic OASFilter API is the correct way to declare webhooks.
 *
 * OASFilter.filterOpenAPI() is called after all annotations have been scanned, so
 * the model already contains every @Path, @Operation, and @Schema before this runs.
 *
 * Registered via: mp.openapi.filter=fish.payara.demo.mp71.openapi.BookstoreOASFilter
 * in microprofile-config.properties.
 *
 * [CONTRAST — MP 6.1 / OpenAPI 3.1] OAS 3.0 had no 'webhooks' keyword; outbound
 * callbacks were undocumented or required x-webhooks vendor extensions.
 */
public class BookstoreOASFilter implements OASFilter {

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        openAPI.addWebhook("book-added",   buildWebhook(
            "Book added to catalog",
            "Delivered to all registered callback URLs when a new book is added. " +
            "Return 410 Gone to signal the server to deregister the subscription.",
            "#/components/schemas/BookEvent",
            true));

        openAPI.addWebhook("book-deleted", buildWebhook(
            "Book removed from catalog",
            "Delivered to all registered callback URLs when a book is deleted.",
            "#/components/schemas/BookEvent",
            false));
    }

    private PathItem buildWebhook(String summary, String description,
                                  String schemaRef, boolean include410) {
        Schema schema = OASFactory.createSchema().ref(schemaRef);

        MediaType mediaType = OASFactory.createMediaType().schema(schema);

        Content content = OASFactory.createContent()
                .addMediaType("application/json", mediaType);

        RequestBody requestBody = OASFactory.createRequestBody()
                .required(true)
                .content(content);

        APIResponses responses = OASFactory.createAPIResponses()
                .addAPIResponse("200",
                    OASFactory.createAPIResponse().description("Webhook acknowledged"));

        if (include410) {
            responses.addAPIResponse("410",
                OASFactory.createAPIResponse()
                    .description("Subscriber gone — server will deregister this URL"));
        }

        return OASFactory.createPathItem()
                .POST(OASFactory.createOperation()
                    .summary(summary)
                    .description(description)
                    .requestBody(requestBody)
                    .responses(responses));
    }
}