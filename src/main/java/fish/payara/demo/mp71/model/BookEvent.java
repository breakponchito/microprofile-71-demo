package fish.payara.demo.mp71.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Webhook payload emitted when a book is added to or removed from the catalog.
 *
 * [MP 7.1 / OpenAPI 4.1] This class is referenced from the @Webhooks declaration
 * on BookstoreApplication and appears in the OAS 3.1 'webhooks' section of /openapi.
 */
@Schema(name = "BookEvent", description = "Event payload delivered to webhook subscribers")
public class BookEvent {

    public enum Type { ADDED, DELETED }

    @Schema(description = "Event type", enumeration = {"ADDED", "DELETED"})
    private Type type;

    @Schema(description = "The book involved in the event")
    private Book book;

    @Schema(description = "ISO-8601 timestamp of when the event occurred",
            example = "2026-09-22T10:15:30Z")
    private String occurredAt;

    public BookEvent() {}

    private BookEvent(Type type, Book book, String occurredAt) {
        this.type = type;
        this.book = book;
        this.occurredAt = occurredAt;
    }

    public static BookEvent added(Book book) {
        return new BookEvent(Type.ADDED, book, Instant.now().toString());
    }

    public static BookEvent deleted(String isbn) {
        Book stub = new Book();
        stub.setIsbn(isbn);
        return new BookEvent(Type.DELETED, stub, Instant.now().toString());
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
}