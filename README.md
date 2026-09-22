# MicroProfile 7.1 Demo — Bookstore API

A complete example application demonstrating all MicroProfile 7.1 specifications
on Payara Server 7.x, with inline comparisons to MicroProfile 6.1 patterns.

## Specs covered

| Spec | Version in MP 7.1 | Key demo file |
|---|---|---|
| Config | 3.1 | `config/BookstoreConfig.java`, `config/FeatureFlags.java` |
| REST Client | 4.0 | `client/IsbnClient.java` |
| Fault Tolerance | 4.1 | `service/BookService.java` |
| Health | 4.0 | `health/*.java` |
| Metrics | 5.1 | `service/BookService.java`, `resource/BookResource.java` |
| OpenAPI | 4.0 | `resource/BookResource.java`, `model/Book.java` |
| JWT Propagation | 2.1 | `resource/BookResource.java` |
| Telemetry | 2.0 | `service/BookService.java` |

## Build and deploy

```bash
# Build the WAR
mvn clean package

# Deploy to a running Payara Server 7.x instance
cp target/microprofile-71-demo.war $PAYARA_HOME/glassfish/domains/domain1/autodeploy/

# Or with Payara Micro
java -jar payara-micro.jar --deploy target/microprofile-71-demo.war
```

## Endpoints

> **Payara note:** Health and OpenAPI are registered at the **root context** (`/`), not at
> the application context root. Both aggregate across all deployed WARs.
> The WAR context root is the filename without `.war`
> (e.g. `microprofile-71-demo-1.0-SNAPSHOT.war` → `/microprofile-71-demo-1.0-SNAPSHOT`).

```
# Application REST API
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books                    List books
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}             Get one book
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}/enrich      Enrich from external API (Fault Tolerance)
POST   /microprofile-71-demo-1.0-SNAPSHOT/api/books                    Add book (JWT: admin/librarian)
DELETE /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}             Remove book (JWT: admin)
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/config/diagnostics Config sources (JWT: admin)

# MicroProfile Health 4.0  — root context
GET  /health          All checks combined
GET  /health/started  @Startup  (CatalogStartupCheck)
GET  /health/live     @Liveness (ExternalApiLivenessCheck)
GET  /health/ready    @Readiness (DatabaseReadinessCheck)

# MicroProfile OpenAPI 4.1  — root context
GET  /openapi         OpenAPI 3.1 document (YAML; append ?format=JSON for JSON)
```

## Key [CONTRAST] annotations in the code

All source files contain `[MP 7.1 / SpecName X.x]` comments marking new features
and `[CONTRAST — MP 6.1]` comments showing the older approach side-by-side.
These are the main teaching anchors for the training session.

## Training materials

See `TRAINING.md` for the full slide-by-slide presentation outline (~16 slides,
2-hour session, including a hands-on exercise).
