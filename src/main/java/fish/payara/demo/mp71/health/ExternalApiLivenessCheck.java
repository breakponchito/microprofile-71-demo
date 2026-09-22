package fish.payara.demo.mp71.health;

import fish.payara.demo.mp71.service.BookService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness check — signals whether the application process is alive and functional.
 *
 * <p>A DOWN response here tells the orchestrator to <em>restart</em> this pod/container.
 * Only use it for unrecoverable conditions (e.g., a deadlocked thread pool, a
 * permanently broken dependency that cannot self-heal).</p>
 *
 * <p>Endpoint: {@code GET /health/live}</p>
 */
@Liveness
@ApplicationScoped
public class ExternalApiLivenessCheck implements HealthCheck {

    @Inject
    private BookService bookService;

    @Inject
    @ConfigProperty(name = "isbn-lookup-api/mp-rest/url", defaultValue = "unconfigured")
    private String apiUrl;

    @Override
    public HealthCheckResponse call() {
        boolean reachable = bookService.isExternalApiReachable();

        return HealthCheckResponse.named("isbn-lookup-api-liveness")
                .withData("url", apiUrl)
                .withData("reachable", reachable)
                .withData("note",
                    "DOWN triggers container restart — circuit breaker handles transient failures")
                .status(reachable)
                .build();
    }
}
