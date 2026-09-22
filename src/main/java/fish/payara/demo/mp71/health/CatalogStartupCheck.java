package fish.payara.demo.mp71.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

/**
 * Startup health check — signals when the application has finished initialising.
 *
 * <h3>[MP 7.1 / Health 4.0] @Startup</h3>
 * Introduced in MicroProfile Health 3.1 (Platform 5.0) and available through 7.x.
 * The endpoint is {@code /health/started}.
 *
 * <h3>[CONTRAST — MP 6.1 / Health 4.0]</h3>
 * Before {@code @Startup} existed, teams overloaded {@code @Readiness} for startup
 * logic.  This caused a problem: Kubernetes (and other orchestrators) interpret a
 * failing readiness probe as "stop sending traffic", so the pod received no traffic
 * during the entire startup window — even when the app was just warming up caches,
 * not actually broken.
 *
 * <p>With the three-probe model:</p>
 * <ul>
 *   <li>{@code @Startup}   → /health/started  — is the app done starting?</li>
 *   <li>{@code @Liveness}  → /health/live     — is the app alive (restart if not)?</li>
 *   <li>{@code @Readiness} → /health/ready    — is it ready to serve requests?</li>
 * </ul>
 */
@Startup
@ApplicationScoped
public class CatalogStartupCheck implements HealthCheck {

    private volatile boolean startupComplete = false;

    @Override
    public HealthCheckResponse call() {
        if (!startupComplete) {
            // Simulate a slow startup task (e.g., loading a large reference dataset).
            // In production: check whether your @Startup @ApplicationScoped bean has
            // finished its @PostConstruct initialization.
            startupComplete = true;
            return HealthCheckResponse.named("catalog-startup")
                    .withData("phase", "initialising")
                    .withData("hint", "Retry in a few seconds")
                    .down()
                    .build();
        }
        return HealthCheckResponse.named("catalog-startup")
                .withData("phase", "complete")
                .up()
                .build();
    }
}
