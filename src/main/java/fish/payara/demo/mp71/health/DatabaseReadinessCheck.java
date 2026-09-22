package fish.payara.demo.mp71.health;

import fish.payara.demo.mp71.service.BookService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness check — signals whether the application can currently serve traffic.
 *
 * <p>A DOWN response here tells the load balancer / orchestrator to stop routing
 * new requests to this instance.  Use it for conditions that are temporary and
 * recoverable (e.g., database connection pool exhausted, dependency degraded).</p>
 *
 * <p>Endpoint: {@code GET /health/ready}</p>
 */
@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {

    @Inject
    private BookService bookService;

    @Override
    public HealthCheckResponse call() {
        boolean ready = bookService.isCatalogReady();
        int bookCount = bookService.totalBooks();

        return HealthCheckResponse.named("bookstore-catalog-readiness")
                .withData("bookCount", bookCount)
                .withData("catalogReady", ready)
                .status(ready)
                .build();
    }
}
