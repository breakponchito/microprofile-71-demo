package fish.payara.demo.mp71.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/*
 * [MP 7.1 / OpenAPI 4.0] @Schema now supports the full OpenAPI 3.1 vocabulary,
 * including JSON Schema keywords like 'pattern', 'minimum', 'maxLength', and
 * 'enumeration'.  In MP OpenAPI 3.x (used in MP 6.1) some of these were absent
 * or required workarounds with extensions.
 */
@Schema(name = "Book", description = "A book in the Bookstore catalog")
public class Book {

    @Schema(description = "ISBN-13 identifier",
            example = "978-0-13-468599-1",
            pattern = "^978-[0-9]-[0-9]{2}-[0-9]{6}-[0-9]$")
    private String isbn;

    @Schema(description = "Book title", example = "Effective Java", maxLength = 255)
    private String title;

    @Schema(description = "Author full name", example = "Joshua Bloch")
    private String author;

    @Schema(description = "Publication year", example = "2018", minimum = "1900", maximum = "2100")
    private int publicationYear;

    @Schema(description = "Price in the store's configured currency", example = "49.99", minimum = "0")
    private double price;

    @Schema(description = "Genre category",
            enumeration = {"PROGRAMMING", "SCIENCE", "FICTION", "BIOGRAPHY", "OTHER"})
    private String category;

    @Schema(description = "Copies currently in stock", example = "10", minimum = "0")
    private int stock;

    public Book() {}

    public Book(String isbn, String title, String author,
                int publicationYear, double price, String category) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publicationYear = publicationYear;
        this.price = price;
        this.category = category;
    }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public int getPublicationYear() { return publicationYear; }
    public void setPublicationYear(int y) { this.publicationYear = y; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
