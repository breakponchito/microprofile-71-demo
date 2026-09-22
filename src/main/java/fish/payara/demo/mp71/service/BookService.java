package fish.payara.demo.mp71.service;

import fish.payara.demo.mp71.client.IsbnClient;
import fish.payara.demo.mp71.client.IsbnLookupResult;
import fish.payara.demo.mp71.config.BookstoreConfig;
import fish.payara.demo.mp71.config.FeatureFlags;
import fish.payara.demo.mp71.model.Book;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core business service for the Bookstore.
 *
 * <h3>Specs demonstrated</h3>
 * <ul>
 *   <li>Fault Tolerance 4.1 — {@literal @}Retry, {@literal @}Timeout,
 *       {@literal @}CircuitBreaker, {@literal @}Fallback, {@literal @}Bulkhead</li>
 *   <li>Telemetry 2.1 — injectable {@code Tracer} and {@code Meter},
 *       {@literal @}WithSpan, {@literal @}SpanAttribute, OTel custom metrics</li>
 *   <li>REST Client 4.0 — {@literal @}RestClient injection</li>
 * </ul>
 *
 * <p>Note: MicroProfile Metrics ({@code @Counted}, {@code @Timed}) was removed in
 * MP 7.0. Custom metrics now use the OTel {@code Meter} API injected via Telemetry 2.1.</p>
 */
@ApplicationScoped
public class BookService {

    private final Map<String, Book> catalog = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    private IsbnClient isbnClient;

    @Inject
    @ConfigProperties
    private BookstoreConfig storeConfig;

    @Inject
    private FeatureFlags featureFlags;

    /*
     * [MP 7.1 / Telemetry 2.1] Tracer and Meter are both injectable as CDI beans.
     * The server wires the correct OTel SDK instance for this application automatically.
     *
     * [CONTRAST — Telemetry 1.x] Required a static lookup:
     *   GlobalOpenTelemetry.getTracer("bookstore")
     * Meter was not injectable in 1.x; custom metrics required MP Metrics or
     * the GlobalOpenTelemetry singleton.
     */
    @Inject
    private Tracer tracer;

    /*
     * [NEW — Telemetry 2.0] Meter is now injectable as a CDI bean.
     * MicroProfile Metrics (@Counted, @Timed) was removed from MP 7.0+;
     * this Meter is the replacement for application-defined metrics.
     */
    @Inject
    private Meter meter;

    private LongCounter    booksAddedCounter;
    private DoubleHistogram listDurationHistogram;

    /*
     * Instruments must be created once and reused — creating them per-invocation
     * produces duplicate registrations in the OTel SDK.
     */
    @PostConstruct
    private void initMetrics() {
        booksAddedCounter = meter.counterBuilder("bookstore.books.added")
            .setDescription("Total books added to the catalog")
            .setUnit("{book}")
            .build();

        listDurationHistogram = meter.histogramBuilder("bookstore.list.duration")
            .setDescription("Time taken to list books from the catalog")
            .setUnit("s")
            .build();
    }

    // ── READ operations ────────────────────────────────────────────────────

    /*
     * [MP 7.1 / Telemetry 2.1] @WithSpan creates an OTel span automatically —
     * no try/finally needed.
     *
     * [CONTRAST — Telemetry 1.x]
     *   Span span = tracer.spanBuilder("BookService.findAll").startSpan();
     *   try (Scope scope = span.makeCurrent()) { ... }
     *   finally { span.end(); }
     *
     * Latency is also recorded via the OTel DoubleHistogram below, replacing
     * the @Timed annotation from MP Metrics 5.1 (removed in MP 7.0).
     */
    @WithSpan("BookService.findAll")
    public List<Book> findAll(String category) {
        long start = System.nanoTime();
        List<Book> result = catalog.values().stream()
                .filter(b -> category == null || category.equalsIgnoreCase(b.getCategory()))
                .limit(storeConfig.maxResults)
                .collect(Collectors.toList());
        listDurationHistogram.record((System.nanoTime() - start) / 1e9);
        return result;
    }

    /*
     * [Telemetry 2.1] @SpanAttribute captures the method parameter as a span
     * attribute tag — visible in any OTel-compatible backend (Jaeger, Zipkin,
     * Grafana Tempo, etc.).
     */
    @WithSpan("BookService.findByIsbn")
    public Optional<Book> findByIsbn(@SpanAttribute("book.isbn") String isbn) {
        return Optional.ofNullable(catalog.get(isbn));
    }

    // ── EXTERNAL enrichment — full Fault Tolerance chain ──────────────────

    /*
     * [MP 7.1 / Fault Tolerance 4.1] All five FT annotations composed on one method.
     * Execution order: Bulkhead → CircuitBreaker → Timeout → Retry → method.
     * If all retries fail, Fallback is invoked.
     *
     * [CONTRAST — MP 6.1 / FT 4.0] API is unchanged. FT 4.1 adds automatic
     * OTel metric emission for each policy (ft.retry.calls.total,
     * ft.timeout.executionDuration, ft.circuitbreaker.state.total, etc.) —
     * no application code needed to get FT observability.
     *
     * @Bulkhead   — caps concurrent callers to 5; extras wait in a queue of 10.
     * @CircuitBreaker — opens after 60% failure rate in a window of 10 requests;
     *                   stays open for 10 seconds.
     * @Timeout    — individual call must complete within 3 seconds.
     * @Retry      — up to 3 retries with 500 ms delay; never retry on bad input.
     * @Fallback   — returns a cached/default Book when retries are exhausted.
     */
    @WithSpan("BookService.enrichFromExternalApi")
    @Bulkhead(value = 5, waitingTaskQueue = 10)
    @CircuitBreaker(requestVolumeThreshold = 10,
                    failureRatio = 0.6,
                    delay = 10, delayUnit = ChronoUnit.SECONDS,
                    successThreshold = 2)
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 3,
           delay = 500, delayUnit = ChronoUnit.MILLIS,
           abortOn = IllegalArgumentException.class)
    @Fallback(fallbackMethod = "enrichFromCacheFallback")
    public Book enrichFromExternalApi(@SpanAttribute("book.isbn") String isbn) {
        IsbnLookupResult remote = isbnClient.findByIsbn(isbn);
        Book book = catalog.getOrDefault(isbn, new Book());
        book.setIsbn(remote.getIsbn());
        book.setTitle(remote.getTitle());
        book.setAuthor(remote.getAuthor());
        book.setPublicationYear(remote.getYear());
        return book;
    }

    /** Fallback: return the cached book, or a placeholder when nothing is cached. */
    public Book enrichFromCacheFallback(String isbn) {
        return catalog.getOrDefault(isbn,
                new Book(isbn, "Title unavailable", "Unknown", 0, 0.0, "OTHER"));
    }

    // ── WRITE operations ───────────────────────────────────────────────────

    /*
     * [MP 7.1 / Telemetry 2.1] OTel LongCounter replaces the removed @Counted
     * annotation from MP Metrics 5.1. The counter was initialised in @PostConstruct.
     *
     * OTel export: bookstore.books.added (counter) visible in any OTLP backend.
     */
    @WithSpan("BookService.addBook")
    public Book addBook(Book book) {
        catalog.put(book.getIsbn(), book);
        booksAddedCounter.add(1);
        return book;
    }

    @WithSpan("BookService.deleteBook")
    public boolean deleteBook(@SpanAttribute("book.isbn") String isbn) {
        return catalog.remove(isbn) != null;
    }

    // ── Health helpers ─────────────────────────────────────────────────────

    public int totalBooks() {
        return catalog.size();
    }

    public boolean isCatalogReady() {
        return true; // In production: verify DB connectivity or initial data load
    }

    public boolean isExternalApiReachable() {
        try {
            isbnClient.findByIsbn("probe-9999999999999");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
