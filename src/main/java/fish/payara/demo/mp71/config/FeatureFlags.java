package fish.payara.demo.mp71.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

/**
 * Demonstrates two Config 3.1 injection styles that were not available in Config 2.x:
 * <ol>
 *   <li>{@code Supplier<T>} — re-reads the value from the ConfigSource on every
 *       {@code .get()} call, enabling runtime toggle without a restart.</li>
 *   <li>{@code List<T>} — comma-separated values are split and converted
 *       automatically; no manual {@code split(",")} needed.</li>
 * </ol>
 *
 * <pre>
 * [CONTRAST — MP 6.1 / Config 2.x]
 * Injecting 'boolean' froze the value at CDI initialization time:
 *
 *   @Inject @ConfigProperty(name = "bookstore.feature.recommendations.enabled")
 *   private boolean recommendationsEnabled;  // frozen at startup — restart required to change
 *
 * Injecting a list required a manual split:
 *
 *   @Inject @ConfigProperty(name = "bookstore.feature.preview.categories")
 *   private String previewCategoriesRaw;     // "fiction,sci-fi"
 *   List<String> cats = Arrays.asList(previewCategoriesRaw.split(","));
 * </pre>
 */
@ApplicationScoped
public class FeatureFlags {

    /*
     * [MP 7.1 / Config 3.1] Supplier<Boolean> — value is re-read on every .get() call.
     * Operations can toggle this in the backing ConfigSource (e.g., a database or
     * cluster source) and the change takes effect immediately, without restart.
     */
    @Inject
    @ConfigProperty(name = "bookstore.feature.recommendations.enabled", defaultValue = "false")
    private Supplier<Boolean> recommendationsEnabled;

    /*
     * [MP 7.1 / Config 3.1] List<String> — comma-separated values are split
     * and converted by the Config runtime.  Escaping: use \, for a literal comma.
     * Also works with Set<T>, T[], Optional<List<T>>.
     */
    @Inject
    @ConfigProperty(name = "bookstore.feature.preview.categories", defaultValue = "fiction,sci-fi")
    private List<String> previewCategories;

    /*
     * [Config 3.1] Implicit converter — java.net.URL has a String constructor,
     * so no custom Converter implementation or registration is needed.
     *
     * [CONTRAST — Config 2.x] You had to implement Converter<URL> and register it
     * in META-INF/services/org.eclipse.microprofile.config.spi.Converter.
     */
    @Inject
    @ConfigProperty(name = "bookstore.cdn.base-url", defaultValue = "https://cdn.bookstore.local")
    private URL cdnBaseUrl;

    /** Called on every request — re-reads from the ConfigSource live. */
    public boolean isRecommendationsEnabled() {
        return recommendationsEnabled.get();
    }

    public List<String> getPreviewCategories() {
        return previewCategories;
    }

    public URL getCdnBaseUrl() {
        return cdnBaseUrl;
    }
}
