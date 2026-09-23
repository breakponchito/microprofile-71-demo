package fish.payara.demo.mp71;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;

/*
 * [MP 7.1 / JWT 2.1] @LoginConfig declares the MP-JWT authentication mechanism.
 * The realmName is surfaced in OpenAPI security schemes.
 *
 * [MP 7.1 / OpenAPI 4.1] @OpenAPIDefinition at the Application level sets global
 * API metadata.  OpenAPI 4.1 produces OAS 3.1 format output (JSON Schema 2020-12
 * dialect) from the /openapi endpoint.
 *
 * [MP 7.1 / OpenAPI 4.1] Webhooks are declared programmatically via OASFilter
 * (see BookstoreOASFilter).  MicroProfile OpenAPI 4.x exposes the OAS 3.1 'webhooks'
 * map through OpenAPI.addWebhook(name, PathItem) — there is no annotation shorthand;
 * the filter runs after annotation scanning so it can augment the model.
 *
 * [CONTRAST — MP 6.1 / OpenAPI 3.1] OAS 3.0 (produced by MP OpenAPI 3.x) had no
 * 'webhooks' field; outbound callbacks required x-webhooks extensions or were left
 * undocumented entirely.
 */
@ApplicationScoped
@LoginConfig(authMethod = "MP-JWT", realmName = "bookstore")
@ApplicationPath("/api")
@OpenAPIDefinition(
    info = @Info(
        title = "Bookstore API",
        version = "1.0",
        description = "MicroProfile 7.1 demo application — contrasts with MP 6.1 patterns",
        contact = @Contact(name = "Payara Demo Team", email = "demo@payara.fish"),
        license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
    ),
    servers = @Server(url = "/microprofile-71-demo-1.0-SNAPSHOT", description = "Local Payara instance")
)
public class BookstoreApplication extends Application {
    // JAX-RS scans for @Path-annotated classes — no explicit class registration needed.
}