package fish.payara.demo.mp71.config;

import org.eclipse.microprofile.config.inject.ConfigProperties;
import java.net.URL;

/*
 * [MP 7.1 / Config 3.1] @ConfigProperties groups all properties sharing a common
 * prefix into a single injectable POJO.
 *
 * [CONTRAST — MP 6.1 / Config 2.x] The old pattern required a separate @Inject
 * @ConfigProperty for every field, repeating the prefix at every injection site:
 *
 *   @Inject @ConfigProperty(name = "bookstore.name")        String name;
 *   @Inject @ConfigProperty(name = "bookstore.max-results") int maxResults;
 *   @Inject @ConfigProperty(name = "bookstore.currency")    String currency;
 *   @Inject @ConfigProperty(name = "bookstore.admin.email") String adminEmail;
 *
 * With @ConfigProperties, the prefix is declared once on the class, and renaming
 * it is a one-line change.  The class itself has no CDI or MicroProfile import —
 * it is a plain POJO, easy to test in isolation.
 */
@ConfigProperties(prefix = "bookstore")
public class BookstoreConfig {

    /** bookstore.name */
    public String name = "Payara Bookstore";

    /** bookstore.max-results — implicit int conversion, no custom Converter needed */
    public int maxResults = 50;

    /** bookstore.currency */
    public String currency = "USD";

    /** bookstore.adminEmail */
    public String adminEmail = "";
}
