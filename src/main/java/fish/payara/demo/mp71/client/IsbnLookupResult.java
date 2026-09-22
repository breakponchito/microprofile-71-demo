package fish.payara.demo.mp71.client;

/** DTO returned by the external ISBN lookup API. */
public class IsbnLookupResult {
    private String isbn;
    private String title;
    private String author;
    private int year;

    public String getIsbn()  { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
}
