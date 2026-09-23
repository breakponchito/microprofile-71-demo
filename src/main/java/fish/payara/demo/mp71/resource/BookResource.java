package fish.payara.demo.mp71.resource;

import fish.payara.demo.mp71.config.BookstoreConfig;
import fish.payara.demo.mp71.config.FeatureFlags;
import fish.payara.demo.mp71.model.Book;
import fish.payara.demo.mp71.model.WebhookRegistration;
import fish.payara.demo.mp71.service.BookService;
import fish.payara.demo.mp71.service.BookWebhookNotifier;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * REST resource exposing the Bookstore catalog.
 *
 * <h3>Specs demonstrated</h3>
 * <ul>
 *   <li>OpenAPI 4.1 — {@literal @}APIResponseSchema, {@literal @}SecurityScheme on class,
 *       {@literal @}Webhooks on Application (see BookstoreApplication)</li>
 *   <li>JWT 2.1 — {@literal @}Claim injection, JsonWebToken, {@literal @}RolesAllowed</li>
 *   <li>Config 3.1 — ConfigValue for audit/diagnostics</li>
 *   <li>REST Client 4.0 — EntityPart for multipart (cover upload)</li>
 * </ul>
 */
@RequestScoped
@Path("/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Books", description = "Catalog CRUD and enrichment operations")

/*
 * [MP 7.1 / OpenAPI 4.1] @SecurityScheme can be placed on a resource class,
 * not only on the Application subclass.  Useful for scoping security definitions
 * to specific resources.
 */
@SecurityScheme(
    securitySchemeName = "jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "MP-JWT token issued by the configured identity provider"
)
public class BookResource {

    @Inject private BookService bookService;
    @Inject private BookWebhookNotifier webhookNotifier;
    @Inject @ConfigProperties private BookstoreConfig storeConfig;
    @Inject private FeatureFlags featureFlags;
    @Inject private Config mpConfig;

    /*
     * [MP 7.1 / JWT 2.1] JsonWebToken — the full decoded token is injectable.
     * Individual claims are injectable with @Claim.
     *
     * [CONTRAST — MP 6.1 / JWT 2.0] The API is the same; JWT 2.1 tightens
     * CDI Lite compatibility so REST endpoints work without full CDI beans.
     */
    @Inject
    private JsonWebToken jwt;

    /** Subject claim — typically the user ID from the identity provider. */
    @Inject
    @Claim(standard = Claims.sub)
    private String subject;

    /** Custom claim injected directly — no manual jwt.getClaim("roles") call. */
    @Inject
    @Claim("preferred_username")
    private String preferredUsername;

    // ── GET /api/books ─────────────────────────────────────────────────────

    @GET
    @Operation(
        summary = "List books",
        description = "Returns up to bookstore.max-results books, optionally filtered by category"
    )
    /*
     * [MP 7.1 / OpenAPI 4.1] @APIResponseSchema — convenience shorthand for the
     * common pattern of a 200 response returning a single schema type.
     *
     * [CONTRAST — MP 6.1 / OpenAPI 3.1] Required the full nesting:
     *   @APIResponse(responseCode = "200",
     *     content = @Content(schema = @Schema(implementation = Book.class)))
     */
    @APIResponseSchema(value = Book.class,
                       responseDescription = "List of books in the catalog",
                       responseCode = "200")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response listBooks(
            @QueryParam("category")
            @Parameter(description = "Filter by category: PROGRAMMING, SCIENCE, FICTION, BIOGRAPHY")
            String category) {

        List<Book> books = bookService.findAll(category);

        /*
         * [MP 7.1 / Config 3.1] ConfigValue carries metadata about where a value
         * came from — source name and ordinal — useful for audit logs and support.
         *
         * [CONTRAST — Config 2.x]
         *   config.getValue("bookstore.max-results", Integer.class);
         *   // No way to know: which source provided it? What was the raw string?
         */
        ConfigValue cv = mpConfig.getConfigValue("bookstore.max-results");
        String auditLine = String.format(
            "[Config] max-results='%s' source='%s' ordinal=%d",
            cv.getValue(), cv.getSourceName(), cv.getSourceOrdinal());
        System.out.println(auditLine);

        return Response.ok(books)
                       .header("X-Store-Name",       storeConfig.name)
                       .header("X-Currency",          storeConfig.currency)
                       .header("X-Recommendations",   featureFlags.isRecommendationsEnabled())
                       .build();
    }

    // ── GET /api/books/{isbn} ──────────────────────────────────────────────

    @GET
    @Path("/{isbn}")
    @Operation(summary = "Get a book by ISBN")
    @APIResponse(responseCode = "200", description = "Book found",
                 content = @Content(schema = @Schema(implementation = Book.class)))
    @APIResponse(responseCode = "404", description = "Book not found")
    public Response getBook(@PathParam("isbn") String isbn) {
        Optional<Book> book = bookService.findByIsbn(isbn);
        return book.map(b -> Response.ok(b).build())
                   .orElse(Response.status(Response.Status.NOT_FOUND)
                                   .entity("{\"error\":\"Not found: " + isbn + "\"}")
                                   .build());
    }

    // ── GET /api/books/{isbn}/enrich ───────────────────────────────────────

    @GET
    @Path("/{isbn}/enrich")
    @Operation(
        summary = "Enrich a book from the external ISBN API",
        description = "Applies Fault Tolerance: @Retry (3x, 500ms), @Timeout (3s), " +
                      "@CircuitBreaker (60% failure), @Fallback to cached value"
    )
    @APIResponse(responseCode = "200", description = "Enriched book (may be fallback data)")
    public Response enrichBook(@PathParam("isbn") String isbn) {
        Book enriched = bookService.enrichFromExternalApi(isbn);
        return Response.ok(enriched).build();
    }

    // ── POST /api/books ────────────────────────────────────────────────────

    @POST
    /*
     * [MP 7.1 / JWT 2.1] @RolesAllowed enforced via the JWT 'groups' claim.
     * The server validates the token signature against mp.jwt.verify.publickey.location
     * and checks that the 'groups' claim contains at least one of these roles.
     */
    @RolesAllowed({"admin", "librarian"})
    @Operation(
        summary = "Add a book to the catalog",
        description = "Requires JWT with role 'admin' or 'librarian'. Triggers the 'book-added' webhook."
    )
    @SecurityRequirement(name = "jwt")
    @APIResponse(responseCode = "201", description = "Book added")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Caller lacks required role")
    public Response addBook(Book book) {
        /*
         * [JWT 2.1] 'subject' and 'preferredUsername' were injected by @Claim —
         * no jwt.getClaim("sub") call needed.
         */
        System.out.printf("[Audit] user='%s' (%s) added isbn='%s'%n",
            preferredUsername, subject, book.getIsbn());

        Book created = bookService.addBook(book);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    // ── DELETE /api/books/{isbn} ───────────────────────────────────────────

    @DELETE
    @Path("/{isbn}")
    @RolesAllowed("admin")
    @Operation(summary = "Remove a book from the catalog",
               description = "Triggers the 'book-deleted' webhook on success.")
    @SecurityRequirement(name = "jwt")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Not found")
    public Response deleteBook(@PathParam("isbn") String isbn) {
        boolean removed = bookService.deleteBook(isbn);
        return removed ? Response.noContent().build()
                       : Response.status(Response.Status.NOT_FOUND).build();
    }

    // ── POST /api/books/webhooks ───────────────────────────────────────────

    /*
     * [MP 7.1 / OpenAPI 4.1] This endpoint lets clients register a callback URL.
     * The webhook contract (payload shape, expected response codes) is documented
     * in the @Webhooks declaration on BookstoreApplication — a new OAS 3.1 feature
     * absent from OAS 3.0 (used by MP OpenAPI 3.x in MP 6.1).
     */
    @POST
    @Path("/webhooks")
    @Operation(
        summary = "Register a webhook callback URL",
        description = "The server will POST BookEvent payloads to this URL when books are added or deleted. " +
                      "See the 'webhooks' section of the OpenAPI document for the callback contract."
    )
    @APIResponse(responseCode = "201", description = "Callback URL registered")
    @APIResponse(responseCode = "400", description = "Missing or blank callbackUrl")
    public Response registerWebhook(WebhookRegistration registration) {
        if (registration == null || registration.getCallbackUrl() == null
                || registration.getCallbackUrl().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"callbackUrl is required\"}")
                           .build();
        }
        webhookNotifier.register(registration.getCallbackUrl());
        return Response.status(Response.Status.CREATED)
                       .entity("{\"registered\":\"" + registration.getCallbackUrl() + "\"}")
                       .build();
    }

    // ── POST /api/books/{isbn}/cover ───────────────────────────────────────

    /*
     * [MP 7.1 / REST Client 4.0] EntityPart is Jakarta EE 10's standard multipart type.
     * It replaces vendor-specific implementations (RESTEasy MultipartInput, Jersey
     * FormDataBodyPart, etc.) that were required in MP 6.1 / Jakarta EE 9.1.
     *
     * [CONTRAST — MP 6.1]
     *   // RESTEasy-specific — not portable:
     *   public Response upload(MultipartInput input) { ... }
     *
     * [NEW — Jakarta EE 10 / MP 7.x]
     *   public Response upload(List<EntityPart> parts) { ... }
     */
    @POST
    @Path("/{isbn}/cover")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Upload a cover image for a book",
        description = "Accepts a multipart/form-data request with a 'cover' file part. " +
                      "Demonstrates REST Client 4.0 EntityPart — the portable Jakarta EE 10 " +
                      "replacement for vendor-specific multipart types."
    )
    @APIResponse(responseCode = "200", description = "Cover received — returns filename and size")
    @APIResponse(responseCode = "400", description = "Missing 'cover' part in the request")
    @APIResponse(responseCode = "404", description = "Book not found")
    public Response uploadCover(@PathParam("isbn") String isbn, List<EntityPart> parts) {
        if (bookService.findByIsbn(isbn).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\":\"Book not found: " + isbn + "\"}")
                           .build();
        }

        Optional<EntityPart> coverPart = parts.stream()
                .filter(p -> "cover".equals(p.getName()))
                .findFirst();

        if (coverPart.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"Missing multipart field: cover\"}")
                           .build();
        }

        EntityPart part = coverPart.get();
        String filename = part.getFileName().orElse("unknown");
        long sizeBytes;
        try {
            sizeBytes = part.getContent().readAllBytes().length;
        } catch (IOException e) {
            sizeBytes = -1;
        }

        String json = String.format(
            "{\"isbn\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d}",
            isbn, filename, sizeBytes);
        return Response.ok(json).build();
    }

    // ── GET /api/books/config/diagnostics ─────────────────────────────────

    /**
     * Admin endpoint that dumps all active ConfigSources and selected effective values.
     * Illustrates Config 3.1's ConfigSource iteration and ConfigValue metadata.
     */
    @GET
    @Path("/config/diagnostics")
    @RolesAllowed("admin")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Dump active configuration sources and effective values")
    @SecurityRequirement(name = "jwt")
    public Response configDiagnostics() {
        StringBuilder sb = new StringBuilder("=== Active ConfigSources (highest ordinal wins) ===\n");

        /*
         * [Config 3.1] getConfigSources() returns all sources in ordinal order.
         * Default ordinals:
         *   System properties       = 400
         *   Environment variables   = 300
         *   microprofile-config.properties = 100
         * Custom sources (e.g. Vault, cluster) declare their own ordinal.
         */
        mpConfig.getConfigSources().forEach(source ->
            sb.append(String.format("  [%4d] %s%n", source.getOrdinal(), source.getName())));

        sb.append("\n=== Effective values and their sources ===\n");
        String[] keys = {
            "bookstore.name",
            "bookstore.max-results",
            "bookstore.currency",
            "bookstore.adminEmail",
            "bookstore.feature.recommendations.enabled",
            "isbn-lookup-api/mp-rest/url"
        };
        for (String key : keys) {
            ConfigValue cv = mpConfig.getConfigValue(key);
            if (cv.getValue() != null) {
                sb.append(String.format("  %-48s = %-25s [%s, ord=%d]%n",
                    cv.getName(), cv.getValue(), cv.getSourceName(), cv.getSourceOrdinal()));
            } else {
                sb.append(String.format("  %-48s = <not configured>%n", key));
            }
        }

        sb.append("\n=== JWT caller ===\n");
        sb.append(String.format("  subject: %s%n", subject));
        sb.append(String.format("  username: %s%n", preferredUsername));
        sb.append(String.format("  groups: %s%n", jwt.getGroups()));

        sb.append("\n=== Webhooks ===\n");
        sb.append(String.format("  registered callbacks: %d%n", webhookNotifier.registeredCount()));

        return Response.ok(sb.toString()).build();
    }
}