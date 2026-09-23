# MicroProfile 7.1 — Training Presentation
## "What Changed from 6.1 to 7.0/7.1 and Why It Matters"

Target audience: Java developers familiar with Jakarta EE and MicroProfile 6.x.
Duration: ~90 minutes (slides + live demo against the Bookstore API).

> **Scope:** Only the five specifications that changed between MicroProfile 6.1 and 7.0/7.1.
> Unchanged specs (Config 3.1, Health 4.0, JWT 2.1) appear in the overview table only.

---

## Part A — Presentation Slides

---

### Slide 1 — Title

**MicroProfile 7.0 / 7.1: What Changed and Why**
*From MicroProfile 6.1 — focusing on the five specs that actually changed*

Demo application: `microprofile-71-demo` — a Bookstore REST API

---

### Slide 2 — Version Overview

- MicroProfile targets **Jakarta EE 10 Core Profile** (CDI 4.0 Lite, Jakarta REST 3.1, JSON-B 3.0)
- Changes arrived in two steps: MP 7.0 (bigger changes) then MP 7.1 (incremental)

| Spec            | MP 6.1 | MP 7.0      | MP 7.1        | Status              |
|-----------------|--------|-------------|---------------|---------------------|
| Config          | 3.1    | 3.1         | 3.1           | **Unchanged**       |
| Fault Tolerance | 4.0    | 4.0         | **4.1**       | Changed in 7.1      |
| Health          | 4.0    | 4.0         | 4.0           | **Unchanged**       |
| JWT Propagation | 2.1    | 2.1         | 2.1           | **Unchanged**       |
| Metrics         | 5.1    | **Removed** | Removed       | **Breaking — gone** |
| OpenAPI         | 3.1    | **4.0**     | **4.1**       | Changed in 7.0+7.1  |
| REST Client     | 3.0    | **4.0**     | 4.0           | Changed in 7.0      |
| Telemetry       | 1.1    | **2.0**     | **2.1**       | Changed in 7.0+7.1  |

---

### Slide 3 — The Demo Application

> **Payara URL note:** Health and OpenAPI are served at the **root context** (`/`), not at the
> WAR context root. Both aggregate across all deployed applications.

```
# Application REST API  (context root = microprofile-71-demo-1.0-SNAPSHOT)
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}/enrich
POST   /microprofile-71-demo-1.0-SNAPSHOT/api/books      (JWT: admin or librarian)
DELETE /microprofile-71-demo-1.0-SNAPSHOT/api/books/{isbn}  (JWT: admin only)
GET    /microprofile-71-demo-1.0-SNAPSHOT/api/books/config/diagnostics  (JWT: admin)

# MicroProfile Health 4.0  — root context
GET  /health   /health/started   /health/live   /health/ready

# MicroProfile OpenAPI 4.1  — root context
GET  /openapi         (YAML, OAS 3.1 format)
GET  /openapi?format=JSON
```

---

### Slide 4 — REST Client 4.0: What's New (MP 7.0)

**Change:** aligned with Jakarta EE 10 (from EE 9.1 in REST Client 3.0).

```java
// [NEW — 4.0] baseUri(String) — no manual URI.create() needed
// [NEW — 4.0] header(String, Object) — attach static headers at build time
IsbnClient client = RestClientBuilder.newBuilder()
    .baseUri("https://openlibrary.org")
    .header("X-App-Name", "bookstore")
    .build(IsbnClient.class);

// [NEW — 4.0] EntityPart replaces vendor-specific multipart types
@POST @Path("/upload")
@Consumes(MediaType.MULTIPART_FORM_DATA)
Response uploadCoverImage(List<EntityPart> parts);
```

**Demo file:** `client/IsbnClient.java`

---

### Slide 5 — Fault Tolerance 4.1: Automatic OTel Metrics (MP 7.1)

**Change:** FT 4.1 auto-emits OTel metrics for every policy. No `@Counted`/`@Timed` needed.

| OTel metric | Type | What it measures |
|---|---|---|
| `ft.retry.calls.total` | Counter | Retry outcomes (valueReturned / exceptionRetried / maxRetriesReached) |
| `ft.timeout.executionDuration` | Histogram | Per-attempt execution time (seconds) |
| `ft.circuitbreaker.state.total` | Counter | Time in open / closed / half-open state |
| `ft.bulkhead.executionsRunning` | UpDownCounter | Current concurrent callers |
| `ft.bulkhead.executionsWaiting` | UpDownCounter | Callers queued for a slot |

**Demo file:** `service/BookService.java` — `enrichFromExternalApi()`

---

### Slide 6 — Metrics Removed: Migrate to Telemetry 2.1 (MP 7.0)

`@Counted`, `@Timed`, `@Gauge` **will not compile** against the MP 7.0+ BOM.

```java
// [OLD — MP 6.1] — does NOT compile against MP 7.x BOM
@Counted(name = "bookstore_books_added_total")
public Book addBook(Book book) { ... }

// [NEW — MP 7.1] inject OTel Meter as a CDI bean
@Inject Meter meter;
private LongCounter booksAddedCounter;

@PostConstruct
void initMetrics() {
    booksAddedCounter = meter.counterBuilder("bookstore.books.added")
        .setUnit("{book}").build();  // create once — never recreate per request
}

public Book addBook(Book book) {
    catalog.put(book.getIsbn(), book);
    booksAddedCounter.add(1);
    return book;
}
```

**Platform metrics auto-emitted:** `http.server.request.duration`, `jvm.memory.used`, `jvm.gc.duration`

**Demo file:** `service/BookService.java` — `initMetrics()`, `addBook()`, `findAll()`

---

### Slide 7 — OpenAPI 4.1: OAS 3.1 Output and Breaking Changes (MP 7.0 + 7.1)

**MP 7.1 (OpenAPI 4.1):** `/openapi` now produces **OpenAPI 3.1** format (JSON Schema 2020-12).

```java
// nullable removed
// [OLD]  @Schema(nullable = true)
// [NEW]  @Schema(type = {SchemaType.STRING, SchemaType.NULL})

// exclusiveMinimum changed from Boolean to numeric BigDecimal
// [OLD]  @Schema(exclusiveMinimum = true)
// [NEW]  @Schema(exclusiveMinimum = "0")

// @SecurityRequirement removed from inside @Operation
// [OLD]  @Operation(security = @SecurityRequirement(name = "jwt"))
// [NEW]  @SecurityRequirement(name = "jwt")  // standalone on the method
```

**Demo file:** `resource/BookResource.java`, `model/Book.java`

---

### Slide 8 — Telemetry 2.1: Injectable Meter and Breaking Changes (MP 7.0 + 7.1)

```java
// [NEW in 2.0] Injectable Meter — replaces MP Metrics entirely
@Inject Meter meter;

// [UNCHANGED since 1.0] @WithSpan / @SpanAttribute
@WithSpan("BookService.findAll")
public List<Book> findAll(String category) { ... }

public Optional<Book> findByIsbn(@SpanAttribute("book.isbn") String isbn) { ... }
```

**Breaking changes — update dashboards:**

| Telemetry 1.x | Telemetry 2.x |
|---|---|
| `http.method` | `http.request.method` |
| `http.url` | `url.full` |
| `http.status_code` | `http.response.status_code` |

SDK is now **shared** across all apps (was per-app in 1.x) — verify `OTEL_SDK_DISABLED` config.

**Demo file:** `service/BookService.java`

---

## Part B — Live Demo and Testing Guide

This section provides all test data, JWT tokens, and curl commands needed to demonstrate
every feature without assuming prior knowledge of the application.

---

### Setup

#### 1. Build and start the application

```bash
cd microprofile-71-demo
mvn clean package -q
java -jar /path/to/payara-micro.jar \
     --deploy target/microprofile-71-demo-1.0-SNAPSHOT.war
```

Wait for the log line: `Payara Micro ... ready in ... ms`

#### 2. Base URLs

```
APP_BASE=http://localhost:8080/microprofile-71-demo-1.0-SNAPSHOT
```

All health and OpenAPI requests go to `http://localhost:8080` (root context).

---

### JWT Test Tokens

The application verifies JWTs using the RSA-256 public key at
`META-INF/public-key.pem` (configured via `mp.jwt.verify.publickey.location`).
The tokens below are pre-signed with the matching private key and expire 2031-01-01.

**Token claims summary:**

| Token | sub | preferred_username | groups | Valid for |
|-------|-----|---------------------|--------|-----------|
| ADMIN | user-001 | alice | admin, librarian | POST, DELETE, /config/diagnostics |
| LIBRARIAN | user-002 | bob | librarian | POST only |
| READONLY | user-003 | carol | (none) | Read-only endpoints only |

Copy the full token string (no line breaks) into the `Authorization: Bearer` header.

**ADMIN token (alice — roles: admin, librarian):**
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMSIsInN1YiI6InVzZXItMDAxIiwidXBuIjoiYWxpY2VAYm9va3N0b3JlLmxvY2FsIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiYWxpY2UiLCJncm91cHMiOlsiYWRtaW4iLCJsaWJyYXJpYW4iXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE5MjQ1NjAwMDB9.o4Jf6pv1Em3yDvclw0PVBVr1srKD32CbphY32YNpdTJ567sQzSHRZsDJCzMm9bCBofHjD6IYunGbJ9WNxWJz1pxVzwbcrKhW-OdsDQALyQhge_JqBoq9oFcOfcgbckQkPp1Xuvg6maTiImpa6UGUO_RfiwXv-S7EV8pxyGVkdn_DbSCPg6q9kawDIEFavWp1tAGwjspc_Am-GRneIW5Nd9Zo5QuHmgO0w_J28BwFzFV2teWZw71XER70RhOyc1TRJPmF2TI6g525QCpu0Zb-X0Xp5es7pggn97dQttrvV4opLVEyamprgu8IRXwHSZbBElY2FhgDasj91bNUX-Kz5g
```

**LIBRARIAN token (bob — roles: librarian):**
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMiIsInN1YiI6InVzZXItMDAyIiwidXBuIjoiYm9iQGJvb2tzdG9yZS5sb2NhbCIsInByZWZlcnJlZF91c2VybmFtZSI6ImJvYiIsImdyb3VwcyI6WyJsaWJyYXJpYW4iXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE5MjQ1NjAwMDB9.QzZ6oyd_h0vuCYL40xL3x5FEjsge_9pvqC2uCU3U0xPNhyhYuwEB9DMqGH5IEK8ZUye-CEr-JlN6hYvMsSP-J2xtyhhhI7KH_vFBiJPWcsaxwUXW3SXhZUKd7guTelBVVVhFQfZ6T9FGusLKOoickkqQZrT4AtppfW-eWKYhRQ55GL1d5w1zsA78CRy5J3s1sY5wlIQvKmk1gDZKWYmWi1g4W3M0owP7AhDdB85qXG7JNg4CUBDwfnglIjO-Uz1gTi7C3clxbY_tUYiNVkMbaQT0UcSDGAXyUptZIp-pT07-n06GXaDu0K3mlI2KWQmO2FxD_jtQP2ZsuYWU691w8w
```

**READONLY token (carol — no roles):**
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMyIsInN1YiI6InVzZXItMDAzIiwidXBuIjoiY2Fyb2xAYm9va3N0b3JlLmxvY2FsIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiY2Fyb2wiLCJncm91cHMiOltdLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTkyNDU2MDAwMH0.A2s2UkQrCUpP4n00CWybU6lUgBEMOWNghb039TMmrv7CzpT1uXqr12v8e--_kGCgkaDRCHwgDkB1GB1IfnhhHL50m2L31NXeqNt-8qoLRoZuNwbFA1aeNYMSN_A-KfUx-N9kDX_dSC24uiD7gsNS95JRpDHhEUrhkZeS2mVj8zHwhd_-j_NvaoIPvc-zGDXlrAmHJEhW183cpQnV4QSgo5-T3UWT8-yWfN5BZXNd5WMVFeF1MpWrGxNlN2EJ5scgILR1WYT8fBxy22StGrex7lJKmfcYZt0bZd0ja_FZVqSifjDW8nAB8TsFPAuMzFD3b1kFBExcOoXqeLhosQ3X9A
```

Set shell variables to avoid repeating them:

```bash
ADMIN_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMSIsInN1YiI6InVzZXItMDAxIiwidXBuIjoiYWxpY2VAYm9va3N0b3JlLmxvY2FsIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiYWxpY2UiLCJncm91cHMiOlsiYWRtaW4iLCJsaWJyYXJpYW4iXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE5MjQ1NjAwMDB9.o4Jf6pv1Em3yDvclw0PVBVr1srKD32CbphY32YNpdTJ567sQzSHRZsDJCzMm9bCBofHjD6IYunGbJ9WNxWJz1pxVzwbcrKhW-OdsDQALyQhge_JqBoq9oFcOfcgbckQkPp1Xuvg6maTiImpa6UGUO_RfiwXv-S7EV8pxyGVkdn_DbSCPg6q9kawDIEFavWp1tAGwjspc_Am-GRneIW5Nd9Zo5QuHmgO0w_J28BwFzFV2teWZw71XER70RhOyc1TRJPmF2TI6g525QCpu0Zb-X0Xp5es7pggn97dQttrvV4opLVEyamprgu8IRXwHSZbBElY2FhgDasj91bNUX-Kz5g"

LIBRARIAN_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMiIsInN1YiI6InVzZXItMDAyIiwidXBuIjoiYm9iQGJvb2tzdG9yZS5sb2NhbCIsInByZWZlcnJlZF91c2VybmFtZSI6ImJvYiIsImdyb3VwcyI6WyJsaWJyYXJpYW4iXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE5MjQ1NjAwMDB9.QzZ6oyd_h0vuCYL40xL3x5FEjsge_9pvqC2uCU3U0xPNhyhYuwEB9DMqGH5IEK8ZUye-CEr-JlN6hYvMsSP-J2xtyhhhI7KH_vFBiJPWcsaxwUXW3SXhZUKd7guTelBVVVhFQfZ6T9FGusLKOoickkqQZrT4AtppfW-eWKYhRQ55GL1d5w1zsA78CRy5J3s1sY5wlIQvKmk1gDZKWYmWi1g4W3M0owP7AhDdB85qXG7JNg4CUBDwfnglIjO-Uz1gTi7C3clxbY_tUYiNVkMbaQT0UcSDGAXyUptZIp-pT07-n06GXaDu0K3mlI2KWQmO2FxD_jtQP2ZsuYWU691w8w"

READONLY_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRyYWluaW5nLWtleS0xIn0.eyJpc3MiOiJodHRwczovL2FjY291bnRzLnBheWFyYS5maXNoIiwianRpIjoiYTFiMmMzZDQtMDAwMyIsInN1YiI6InVzZXItMDAzIiwidXBuIjoiY2Fyb2xAYm9va3N0b3JlLmxvY2FsIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiY2Fyb2wiLCJncm91cHMiOltdLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTkyNDU2MDAwMH0.A2s2UkQrCUpP4n00CWybU6lUgBEMOWNghb039TMmrv7CzpT1uXqr12v8e--_kGCgkaDRCHwgDkB1GB1IfnhhHL50m2L31NXeqNt-8qoLRoZuNwbFA1aeNYMSN_A-KfUx-N9kDX_dSC24uiD7gsNS95JRpDHhEUrhkZeS2mVj8zHwhd_-j_NvaoIPvc-zGDXlrAmHJEhW183cpQnV4QSgo5-T3UWT8-yWfN5BZXNd5WMVFeF1MpWrGxNlN2EJ5scgILR1WYT8fBxy22StGrex7lJKmfcYZt0bZd0ja_FZVqSifjDW8nAB8TsFPAuMzFD3b1kFBExcOoXqeLhosQ3X9A"

APP=http://localhost:8080/microprofile-71-demo-1.0-SNAPSHOT
```

---

### Test Data — Book Records

Five books are used throughout the demo. Add them to the catalog during the POST exercises.

```json
// Book 1 — used for basic CRUD and OpenAPI demo
{
  "isbn": "978-0-13-468599-1",
  "title": "Effective Java",
  "author": "Joshua Bloch",
  "publicationYear": 2018,
  "price": 45.99,
  "category": "PROGRAMMING"
}

// Book 2 — used for Fault Tolerance enrich demo
{
  "isbn": "978-0-13-235088-4",
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "publicationYear": 2008,
  "price": 38.50,
  "category": "PROGRAMMING"
}

// Book 3 — used for category filter demo
{
  "isbn": "978-0-20-163361-0",
  "title": "The Pragmatic Programmer",
  "author": "David Thomas",
  "publicationYear": 2019,
  "price": 49.95,
  "category": "PROGRAMMING"
}

// Book 4 — used for DELETE demo (admin only)
{
  "isbn": "978-0-59-651798-1",
  "title": "Head First Design Patterns",
  "author": "Eric Freeman",
  "publicationYear": 2021,
  "price": 59.99,
  "category": "PROGRAMMING"
}

// Book 5 — used for role-based access demo (librarian can add, not delete)
{
  "isbn": "978-0-74-325290-9",
  "title": "The Hitchhiker's Guide to the Galaxy",
  "author": "Douglas Adams",
  "publicationYear": 1979,
  "price": 12.99,
  "category": "FICTION"
}
```

---

### Step-by-Step Demo

Each step below identifies: the spec being demonstrated, the exact curl command,
the expected HTTP status, and what to observe in the response.

---

#### Step 1 — Health checks (MicroProfile Health 4.0)

**Spec:** Health 4.0 — three-probe model (`@Startup`, `@Liveness`, `@Readiness`).
**URL:** Root context — not the WAR context root.

**1a. All health checks combined**

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

Expected status: `200 OK` (or `503 Service Unavailable` if any check is DOWN).

Expected response on first call (CatalogStartupCheck returns DOWN on first invocation):
```json
{
  "status": "DOWN",
  "checks": [
    { "name": "catalog-startup",            "status": "DOWN",
      "data": { "phase": "initialising", "hint": "Retry in a few seconds" } },
    { "name": "isbn-lookup-api-liveness",   "status": "UP",
      "data": { "url": "https://openlibrary.org", "reachable": true } },
    { "name": "bookstore-catalog-readiness","status": "UP",
      "data": { "bookCount": 0, "catalogReady": true } }
  ]
}
```

On the second call all three will be UP.

**1b. Individual probes** — show that each maps to a different Kubernetes probe type:

```bash
# @Startup — Kubernetes startupProbe: is the app done initializing?
curl -s http://localhost:8080/health/started | python3 -m json.tool

# @Liveness — Kubernetes livenessProbe: restart the container if DOWN
curl -s http://localhost:8080/health/live | python3 -m json.tool

# @Readiness — Kubernetes readinessProbe: stop traffic if DOWN
curl -s http://localhost:8080/health/ready | python3 -m json.tool
```

**What to explain:** Before `@Startup` (added in Health 3.1), teams misused `@Readiness`
for startup logic — causing pods to receive no traffic during warm-up. The three-probe
model is unchanged in MP 7.x (Health 4.0 is the same in 6.1 and 7.1), but it is a
prerequisite for understanding the demo app.

---

#### Step 2 — List books — no auth required (Config, Telemetry)

**Spec:** REST endpoint, response headers from Config 3.1, OTel Telemetry 2.1.

```bash
curl -s -i $APP/api/books
```

Expected status: `200 OK`

Expected response headers (set by `BookResource` from injected `BookstoreConfig`):
```
X-Store-Name: Payara Bookstore (Dev)
X-Currency: EUR
X-Recommendations: false
```

Expected body: empty array (no books added yet):
```json
[]
```

**Filter by category** (once books are added in Step 4):
```bash
curl -s "$APP/api/books?category=PROGRAMMING" | python3 -m json.tool
```

---

#### Step 3 — OpenAPI document (MicroProfile OpenAPI 4.1)

**Spec:** OpenAPI 4.1 — `/openapi` is at the root context, produces OAS 3.1 format.

**3a. Fetch YAML document**
```bash
curl -s http://localhost:8080/openapi
```

Look for `openapi: 3.1.0` at the top — confirms OAS 3.1 output (MP 7.1 change).
In MP 6.1 (OpenAPI 3.1 spec) this endpoint produced OAS 3.0 format.

**3b. Fetch JSON document**
```bash
curl -s "http://localhost:8080/openapi?format=JSON" | python3 -m json.tool
```

**What to point out:**
- `openapi: "3.1.0"` — version string confirming OAS 3.1
- `info.title: "Bookstore API"` — from `@OpenAPIDefinition` on `BookstoreApplication`
- `components.securitySchemes.jwt` — from `@SecurityScheme` on `BookResource`
- Verify the `price` field uses `exclusiveMinimum: 0` (numeric, OAS 3.1 style — not a boolean)
- Verify nullable fields use `type: [string, "null"]` not `nullable: true`

---

#### Step 4 — Add books (JWT required — roles: admin or librarian)

**Spec:** JWT 2.1 (`@RolesAllowed`), Telemetry 2.1 (OTel `LongCounter` for `bookstore.books.added`).

**4a. Add Book 1 as alice (admin)**

```bash
curl -s -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "isbn": "978-0-13-468599-1",
    "title": "Effective Java",
    "author": "Joshua Bloch",
    "publicationYear": 2018,
    "price": 45.99,
    "category": "PROGRAMMING"
  }' | python3 -m json.tool
```

Expected status: `201 Created`
Expected body: the created book (echo of the request body).

Check the server log for the audit line:
```
[Audit] user='alice' (user-001) added isbn='978-0-13-468599-1'
```

This log line comes from the `@Claim`-injected fields in `BookResource`:
```java
@Inject @Claim(standard = Claims.sub)          String subject;         // → "user-001"
@Inject @Claim("preferred_username")           String preferredUsername; // → "alice"
```

**4b. Add Book 2 as alice (admin)**

```bash
curl -s -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "isbn": "978-0-13-235088-4",
    "title": "Clean Code",
    "author": "Robert C. Martin",
    "publicationYear": 2008,
    "price": 38.50,
    "category": "PROGRAMMING"
  }' | python3 -m json.tool
```

**4c. Add Book 5 as bob (librarian — has permission)**

```bash
curl -s -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $LIBRARIAN_TOKEN" \
  -d '{
    "isbn": "978-0-74-325290-9",
    "title": "The Hitchhiker'\''s Guide to the Galaxy",
    "author": "Douglas Adams",
    "publicationYear": 1979,
    "price": 12.99,
    "category": "FICTION"
  }' | python3 -m json.tool
```

Expected status: `201 Created` — librarian role is sufficient for POST.

**4d. Try to add a book as carol (no roles — expected 403)**

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $READONLY_TOKEN" \
  -d '{"isbn":"978-0-00-000000-0","title":"Unauthorized","author":"Nobody","publicationYear":2024,"price":0.0,"category":"FICTION"}'
```

Expected: `403 Forbidden` — carol has no `admin` or `librarian` group in her JWT `groups` claim.

**4e. Try with no token — expected 401**

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -d '{"isbn":"978-0-00-000000-0","title":"No Auth","author":"Nobody","publicationYear":2024,"price":0.0,"category":"FICTION"}'
```

Expected: `401 Unauthorized`

---

#### Step 5 — Get a specific book — no auth required

```bash
curl -s $APP/api/books/978-0-13-468599-1 | python3 -m json.tool
```

Expected status: `200 OK`
Expected body:
```json
{
  "isbn": "978-0-13-468599-1",
  "title": "Effective Java",
  "author": "Joshua Bloch",
  "publicationYear": 2018,
  "price": 45.99,
  "category": "PROGRAMMING"
}
```

**Try a non-existent ISBN — expected 404:**

```bash
curl -s -o /dev/null -w "%{http_code}" $APP/api/books/978-0-00-000000-0
```

Expected: `404 Not Found`

---

#### Step 6 — Fault Tolerance: enrich from external ISBN API

**Spec:** Fault Tolerance 4.1 — full annotation chain + automatic OTel metrics.
The `enrichFromExternalApi()` method in `BookService` has all five FT annotations:
`@Bulkhead` → `@CircuitBreaker` → `@Timeout` → `@Retry(3)` → `@Fallback`.

**6a. Enrich an existing book (external API reachable)**

```bash
curl -s $APP/api/books/978-0-13-468599-1/enrich | python3 -m json.tool
```

Expected status: `200 OK`
The external API (`https://openlibrary.org`) is called. If it responds, the title/author
may be updated. If it is slow or unreachable, after 3 seconds `@Timeout` triggers,
`@Retry` retries up to 3 times with 500ms delays, then `@Fallback` returns the cached book.

**6b. Enrich a non-cached ISBN (demonstrates @Fallback)**

Use an ISBN that is NOT in the catalog — the fallback creates a placeholder:

```bash
curl -s $APP/api/books/978-0-00-000000-9/enrich | python3 -m json.tool
```

Expected body (fallback response):
```json
{
  "isbn": "978-0-00-000000-9",
  "title": "Title unavailable",
  "author": "Unknown",
  "publicationYear": 0,
  "price": 0.0,
  "category": "OTHER"
}
```

**What to explain — FT 4.1 OTel metrics:**
These are emitted automatically with no code in the application:
- `ft.retry.calls.total{result="exceptionRetried"}` — increments when the external call fails and is retried
- `ft.retry.calls.total{result="maxRetriesReached"}` — increments when all 3 retries are exhausted
- `ft.timeout.executionDuration` — histogram of per-attempt duration
- `ft.circuitbreaker.state.total` — if the circuit opens after repeated failures

If you have an OTel backend (Jaeger, Grafana) running at `http://localhost:4317`,
these metrics appear automatically. Otherwise check the Payara Micro log for FT-related entries.

---

#### Step 7 — Delete a book (admin only)

**Spec:** JWT 2.1 `@RolesAllowed("admin")` — librarian cannot delete.

**7a. Delete Book 4 as alice (admin — succeeds)**

First add it:
```bash
curl -s -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "isbn": "978-0-59-651798-1",
    "title": "Head First Design Patterns",
    "author": "Eric Freeman",
    "publicationYear": 2021,
    "price": 59.99,
    "category": "PROGRAMMING"
  }'
```

Then delete:
```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $APP/api/books/978-0-59-651798-1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `204 No Content`

**7b. Try to delete as bob (librarian — expected 403)**

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $APP/api/books/978-0-13-468599-1 \
  -H "Authorization: Bearer $LIBRARIAN_TOKEN"
```

Expected: `403 Forbidden` — `@RolesAllowed("admin")` requires the `admin` group; bob only has `librarian`.

---

#### Step 8 — Config diagnostics (admin only)

**Spec:** Config 3.1 `ConfigValue` — shows which ConfigSource provided each value and its ordinal.

```bash
curl -s $APP/api/books/config/diagnostics \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected status: `200 OK`
Expected body (plain text):
```
=== Active ConfigSources (highest ordinal wins) ===
  [ 400] SysPropConfigSource
  [ 300] EnvConfigSource
  [ 100] PropertiesConfigSource[microprofile-config.properties]

=== Effective values and their sources ===
  bookstore.name                             = Payara Bookstore (Dev)          [PropertiesConfigSource, ord=100]
  bookstore.max-results                      = 20                              [PropertiesConfigSource, ord=100]
  bookstore.currency                         = EUR                             [PropertiesConfigSource, ord=100]
  bookstore.adminEmail                       = admin@bookstore.local           [PropertiesConfigSource, ord=100]
  bookstore.feature.recommendations.enabled  = false                           [PropertiesConfigSource, ord=100]
  isbn-lookup-api/mp-rest/url                = https://openlibrary.org         [PropertiesConfigSource, ord=100]

=== JWT caller ===
  subject: user-001
  username: alice
  groups: [admin, librarian]
```

**What to explain:** `ConfigValue.getSourceName()` and `getSourceOrdinal()` let you audit
exactly where a value came from — useful for debugging environment-specific config overrides.
Override a value with a system property and rerun to see ordinal 400 win over 100:

```bash
curl -s $APP/api/books/config/diagnostics \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  # (restart Payara Micro with -Dbookstore.max-results=5 to show override)
```

---

#### Step 9 — Telemetry: metrics, logs, and traces to an OTLP backend

**Spec:** Telemetry 2.1 — `@WithSpan`, `@SpanAttribute`, injectable `Meter`, OTel Logs Bridge API.

##### 9a. Start the observability stack

`grafana/otel-lgtm` is a single container that accepts all three OTel signals and routes them to the right backend:

| Signal | Backend inside lgtm | View in Grafana |
|---|---|---|
| Traces | Grafana Tempo | Explore → Tempo |
| Metrics | Prometheus | Explore → Prometheus |
| Logs | Loki | Explore → Loki |

```bash
podman run -d \
  --name lgtm \
  --restart always \
  -p 3000:3000 \
  -p 4317:4317 \
  -p 4318:4318 \
  grafana/otel-lgtm:latest
```

Open Grafana at `http://localhost:3000` (no login required locally).

##### 9b. Start Payara Micro with all three exporters

> **Key point:** `otel.*` properties in `microprofile-config.properties` are read too late for the
> OTel SDK initialisation. Pass them as JVM system properties (`-D`) so the SDK picks them up at boot.

```bash
java \
  -Dotel.service.name=bookstore-api \
  -Dotel.sdk.disabled=false \
  -Dotel.traces.exporter=otlp \
  -Dotel.metrics.exporter=otlp \
  -Dotel.logs.exporter=otlp \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.metric.export.interval=10000 \
  -Dotel.traces.sampler=parentbased_traceidratio \
  -Dotel.traces.sampler.arg=1.0 \
  -jar /path/to/payara-micro.jar \
  --deploy target/microprofile-71-demo-1.0-SNAPSHOT.war
```

##### 9c. Generate traffic and verify each signal

```bash
# Generate list calls (bookstore.list.duration histogram + BookService.findAll span)
for i in {1..10}; do curl -s $APP/api/books > /dev/null; done

# Add a book — emits bookstore.books.added counter AND a structured OTel log record
curl -s -X POST $APP/api/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"isbn":"978-0-20-163361-0","title":"The Pragmatic Programmer","author":"David Thomas","publicationYear":2019,"price":49.95,"category":"PROGRAMMING"}'
```

**Traces** — Grafana → Explore → datasource `Tempo` → search service `bookstore-api`:
- `BookService.findAll`, `BookService.addBook`, `BookService.findByIsbn` spans
- Each span carries `book.isbn` attribute from `@SpanAttribute`

**Metrics** — Grafana → Explore → datasource `Prometheus`:
```promql
# Books added (LongCounter)
bookstore_books_added_total

# List latency 95th percentile (DoubleHistogram)
histogram_quantile(0.95, rate(bookstore_list_duration_seconds_bucket[1m]))

# Fault Tolerance auto-metrics (emitted by FT 4.1, no app code needed)
ft_retry_calls_total
ft_timeout_execution_duration_seconds_bucket
ft_circuitbreaker_state_total

# Platform metric from Payara (no app code needed)
http_server_request_duration_seconds_count
```

**Logs** — Grafana → Explore → datasource `Loki`:
```logql
{service_name="bookstore-api"}
```

Each `POST /api/books` call emits a structured log record via the OTel Logs Bridge API
(`openTelemetry.getLogsBridge()` in `BookService.addBook`) with attributes:
- `book.isbn`, `book.title`, `book.author`
- `trace_id` and `span_id` — automatically injected because `addBook` runs inside a `@WithSpan` span

Click the `trace_id` value in any Loki log entry → Grafana jumps to the matching Tempo trace.
This log-to-trace correlation is the core value of the OTel Logs Bridge API.

> **Why LogsBridge and not JUL?**
> Payara Micro does not automatically bridge `java.util.logging` into the OTel SDK.
> `otel.logs.exporter=otlp` tells the SDK *how* to export, but application code must
> submit records explicitly via `OpenTelemetry.getLogsBridge()`. This is by design in
> MicroProfile Telemetry 2.x — the runtime provides the bridge API; the application
> chooses what structured data to emit.

**What to explain — Telemetry 2.1 breaking change:**
HTTP span attributes were renamed. If you have saved Jaeger queries or Grafana dashboards
that filter by `http.method`, `http.url`, or `http.status_code`, they will return no results
against a Telemetry 2.x runtime. Rename them to `http.request.method`, `url.full`,
`http.response.status_code`.

---

#### Step 10 — Hands-on Exercise

**Task:** Add a live "discount" feature using the specs that changed in MP 7.x.

1. **In `BookService`** — add a `LongCounter` named `bookstore.discounted.requests` using `@Inject Meter`:
   ```java
   private LongCounter discountedRequestsCounter;

   @PostConstruct
   void initMetrics() {
       // existing counters ...
       discountedRequestsCounter = meter.counterBuilder("bookstore.discounted.requests")
           .setDescription("Requests where a discount was applied")
           .setUnit("{request}")
           .build();
   }
   ```

2. **In `BookResource`** — read `bookstore.discount.enabled` via `Supplier<Boolean>` (no restart needed):
   ```java
   @Inject
   @ConfigProperty(name = "bookstore.discount.enabled", defaultValue = "false")
   private Supplier<Boolean> discountEnabled;
   ```

3. **In `BookResource.listBooks()`** — if enabled, apply a 10% discount and increment the counter:
   ```java
   if (discountEnabled.get()) {
       books = books.stream()
           .map(b -> new Book(b.getIsbn(), b.getTitle(), b.getAuthor(),
                              b.getPublicationYear(), b.getPrice() * 0.90, b.getCategory()))
           .collect(Collectors.toList());
       bookService.recordDiscountedRequest();  // delegates to the counter
   }
   ```

4. **Verify:**
   ```bash
   # Restart Payara Micro with the flag enabled (Supplier re-reads on every call)
   java -jar payara-micro.jar \
        -Dbookstore.discount.enabled=true \
        --deploy target/microprofile-71-demo-1.0-SNAPSHOT.war

   # List books — prices should be 10% lower
   curl -s $APP/api/books | python3 -m json.tool

   # Health should still be UP
   curl -s http://localhost:8080/health/ready | python3 -m json.tool

   # OpenAPI should show updated response schema
   curl -s "http://localhost:8080/openapi?format=JSON" | python3 -m json.tool
   ```

---

### Slide 11 — Migration Checklist: MP 6.1 → 7.1 (Changed Specs Only)

| Area | What to change |
|---|---|
| **MP Metrics (BREAKING)** | Remove `@Counted`, `@Timed`, `@Gauge` — won't compile. Inject `Meter` from Telemetry 2.1. |
| Custom metrics | Replace with `meter.counterBuilder()` / `meter.histogramBuilder()` in `@PostConstruct` |
| FT metrics | FT 4.1 auto-emits OTel metrics — remove any manual `@Counted`/`@Timed` on FT-annotated methods |
| **REST Client (BREAKING)** | Programmatic: use `RestClientBuilder.baseUri(String)` and `.header()` new overloads (4.0) |
| REST Client multipart | Use `EntityPart` (Jakarta EE 10) — remove vendor-specific multipart types |
| **OpenAPI (BREAKING)** | Upgrade tooling consuming `/openapi` to OAS 3.1-compatible versions |
| OpenAPI `nullable = true` | Replace with `type = {SchemaType.STRING, SchemaType.NULL}` |
| OpenAPI `exclusiveMinimum` Boolean | Change to numeric `BigDecimal` value |
| OpenAPI `Schema.type` single value | Update `OASFilter` implementations: `type` is now `List<SchemaType>` |
| OpenAPI `@Operation(security=...)` | Move to standalone `@SecurityRequirement` on the method |
| **Telemetry (BREAKING)** | Update dashboards: `http.method` → `http.request.method`, `http.url` → `url.full` |
| Telemetry SDK shared | SDK now shared across apps — verify `OTEL_SDK_DISABLED` per-app config |
| **Payara URLs** | Health: `GET /health` (root context). OpenAPI: `GET /openapi` (root context). |
