# 🛒 PeerKart - E-Commerce Order Management Backend

Credits to my mom for coming up with the silly name PeerKart :)

A reactive Spring Boot backend for managing e-commerce orders — built with WebFlux, R2DBC, PostgreSQL, and JWT authentication. This README captures everything about the current state of the project and where we're planning to take it next.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Database Design](#database-design)
- [API Reference](#api-reference)
- [Authentication Flow](#authentication-flow)
- [Background Jobs](#background-jobs)
- [Design Decisions & Assumptions](#design-decisions--assumptions)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [v2 Proposal — Item Catalogue](#v2-proposal--item-catalogue)

---

## Tech Stack

| Concern | Choice                             | Why |
|---|------------------------------------|---|
| Framework | Spring Boot 4.0 + WebFlux          | Fully non-blocking, reactive end-to-end |
| Database | PostgreSQL                         | Reliable, relational, great with R2DBC |
| DB Access | Spring Data R2DBC                  | Reactive database driver — no blocking JDBC |
| Auth | JWT (JJWT 0.12.6) + Spring Security | Stateless, no session management needed |
| Serialization | Jackson Databind          | Industry standard |
| Documentation | Springdoc OpenAPI (Swagger UI)     | Auto-generated, interactive API docs |
| Testing | JUnit 5 + Mockito + StepVerifier   | Reactive-aware test assertions |
| Boilerplate | Lombok                             | Keeps models and DTOs clean |

---

## Project Structure

```
src/main/java/com/kev/ecom/
├── config/
│   ├── SecurityConfig.java          # Route-level security rules
│   └── OpenApiConfig.java          # Swagger UI
├── controller/
│   ├── AuthController.java         # POST /api/auth/register, /login
│   └── OrderController.java        # CRUD operations on orders
├── dto/
│   ├── auth/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── AuthResponse.java
│   └── order/
│       ├── CreateOrderRequest.java
│       └── OrderResponse.java
├── enums/
│   └── OrderStatus.java            # Order status enum: PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
├── exception/
│   ├── OrderNotFoundException.java
│   ├── OrderCancellationException.java
│   └── GlobalExceptionHandler.java # Consistent JSON error responses
├── filter/
│   └── JwtAuthenticationFilter.java
├── model/
│   ├── auth/
│   │   └── User.java
│   │   ├── AuthenticatedUser.java
│   └── order/
│       ├── Order.java
│       ├── OrderItem.java
│       ├── OrderItemMapping.java   # Join table — orders ↔ items
│
├── repository/
│   ├── UserRepository.java
│   ├── OrderRepository.java
│   ├── OrderItemRepository.java
│   └── OrderItemMappingRepository.java
├── scheduler/
│   └── OrderStatusScheduler.java   # Auto-promotes PENDING → PROCESSING every 5 min
└── service/
│    ├── AuthService.java
│    └── OrderService.java
├── util/
    ├── JwtUtil.java
    └── PeerKart.java
```

---

## Database Design

Three tables, two relationships. Kept intentionally simple.

```
users ──(1:N)── orders ──(1:N)── order_item_mappings ──(N:1)── items
```

### `users`
Stores registered accounts. Passwords are BCrypt-hashed — the raw password never touches the database.

### `orders`
One row per order. Belongs to a user via `user_id`. Status starts as `PENDING` and moves forward from there. The `total_amount` is calculated at order creation time and stored — not recomputed on every read.

### `items`
Standalone item rows. These are the actual products being purchased. They have no direct foreign key to `orders` — the relationship lives in the mapping table.

### `order_item_mappings`
The explicit join table between `orders` and `items`. One row per item per order. A `UNIQUE (order_id, item_id)` constraint prevents the same item appearing twice in one order.

**Why a separate mapping table instead of a direct FK on `items`?**
An item can appear in many orders, and an order can contain many items — that's a many-to-many relationship. A direct FK only works for one-to-many. The mapping table is the standard and correct approach here.

**Why not a JPA `@ManyToMany`?**
We're using R2DBC, not JPA. R2DBC has no lazy loading or join support — all relationships must be loaded manually via separate queries composed in the service layer. The mapping table makes this explicit and transparent.

---

## API Reference

Swagger UI is available at **`http://localhost:8080/swagger-ui.html`** when the app is running. Use the **Authorize** button to paste your JWT token once and have it applied to all requests.

### Auth — no token required

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create a new account. Returns a JWT token. |
| `POST` | `/api/auth/login` | Log in with email + password. Returns a JWT token. |

**Register request:**
```json
{
  "full_name": "Jane Smith",
  "email": "jane@example.com",
  "password": "mypassword123"
}
```

**Auth response (same shape for register and login):**
```json
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "user_id": 1,
  "email": "jane@example.com",
  "full_name": "Jane Smith"
}
```

### Orders — Bearer token required

All order endpoints require `Authorization: Bearer <token>` in the request header. Orders are always scoped to the authenticated user — you can only see and modify your own orders.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders/create-order` | Place a new order |
| `GET` | `/api/orders` | List your orders (optional `?status=PENDING` filter) |
| `GET` | `/api/orders/get-order/{id}` | Get a specific order by ID |
| `DELETE` | `/api/orders/cancel-order/{id}` | Cancel an order (only if PENDING) |

**Create order request:**
```json
{
  "items": [
    { "item_id": "SKU-001", "item_name": "Wireless Mouse", "quantity": 1, "unit_price": 29.99 },
    { "item_id": "SKU-002", "item_name": "USB Hub", "quantity": 2, "unit_price": 15.00 }
  ]
}
```

---

## Authentication Flow

```
1. POST /api/auth/register or /login
        ↓
2. Server verifies credentials (BCrypt password check)
        ↓
3. JWT token generated — contains userId + email, signed with HS256
        ↓
4. Client stores the token and sends it as:
   Authorization: Bearer <token>
        ↓
5. JwtAuthenticationFilter intercepts every request,
   validates the token, and writes AuthenticatedUser
   into the reactive security context
        ↓
6. Controllers read the principal via @AuthenticationPrincipal —
   userId is extracted and passed to the service layer
```

The JWT contains `userId` and `email` as claims. It does **not** contain a role — the current version has a single user type. See the v2 proposal for where role-based access is heading.

Tokens expire after **24 hours** by default for uninterrupted development in local. This is configured via `app.jwt.expiration-ms` in `application.yml`. Override it with the `JWT_EXPIRATION_MS` environment variable.

---

## Background Jobs

The `OrderStatusScheduler` runs every **5 minutes** and automatically promotes all `PENDING` orders to `PROCESSING`. This simulates a fulfilment system picking up new orders.

```
PENDING ──(auto, every 5 min)──► PROCESSING ──► SHIPPED ──► DELIVERED
                                                              
PENDING ──(user cancels)──► CANCELLED
```

A few things worth knowing about how this works:

- `@EnableScheduling` on `EcommerceApplication` activates the scheduler.
- The job calls `OrderService.promotePendingOrders()` which returns a `Mono<Long>`.
- `@Scheduled` runs on a plain thread and knows nothing about reactive pipelines, so the scheduler explicitly calls `.subscribe()` to kick off execution. Without this, nothing happens.
- The count of promoted orders is logged at INFO level on every run.

---

## Design Decisions & Assumptions

Here are the deliberate choices made during development, and the reasoning behind them.

**Reactive all the way through.**
Using WebFlux + R2DBC means there's no blocking anywhere in the stack — from HTTP handling down to the database driver. This makes the application efficient under load but does add complexity, especially around testing reactive pipelines (`StepVerifier` over `.block()`).

**JWT is stateless — no refresh tokens.**
The current implementation issues a single access token that expires after 24 hours. There's no refresh token mechanism. This is a conscious simplification — adding refresh tokens is straightforward but out of scope for v1.

**Passwords are BCrypt-hashed, never stored or logged in plain text.**
The `AuthService` encodes the password before saving and the raw password is never stored. The `User` model stores `password_hash`, not `password`.

**Order ownership is enforced at the service layer, not just the route level.**
When you fetch or cancel an order, the service checks that `order.userId == authenticatedUser.userId`. If not, it throws `OrderNotFoundException` — returning a 404 rather than a 403. This is intentional: leaking the existence of an order to the wrong user is itself an information disclosure.

**Price and item name are stored inline on order items.**
This is the standard e-commerce "snapshot" pattern. If you later update a product's name or price in the catalogue, historical orders stay unchanged. An order is a contract — it should reflect what was agreed at purchase time, not what the catalogue says today.

**`CANCELLED` is a terminal state.**
Once an order is cancelled, it cannot be reactivated. Only `PENDING` orders can be cancelled — anything that has moved to `PROCESSING` or beyond is considered to have left the warehouse.

**No pagination on list endpoints.**
`GET /api/orders` returns all of a user's orders. In production you'd want cursor or page-based pagination, but for v1 the scope is kept simple.

**`ON DELETE CASCADE` on child tables.**
Deleting a user cascades to their orders. Deleting an order cascades to `order_item_mappings`. This keeps the database clean without requiring manual cleanup in the application layer.

**Schema initialisation via `schema.sql`.**
The app uses `spring.sql.init.mode=always` which runs `schema.sql` on every startup. Tables are created with `IF NOT EXISTS` so this is safe to run repeatedly. For a production environment, replace this with Flyway or Liquibase for proper migration versioning.

**Jackson configured in code, not `application.yml`.**
`JacksonConfig` registers the `ObjectMapper` bean and wires it into WebFlux's codec pipeline directly. `application.yml` Jackson properties are intentionally absent — they would target Spring Boot's auto-configured mapper which backs off when you define your own bean, making the YAML settings silently ineffective.

---

## Getting Started

**Prerequisites:** Java 21, Maven, PostgreSQL running locally.

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE ecommerce;"

# 2. Clone and build
git clone <your-repo-url>
cd ecommerce-backend
mvn clean package -DskipTests

# 3. Set your JWT secret (optional — a default is provided for local dev)
export JWT_SECRET=your-secret-key-at-least-32-characters

# 4. Run
mvn spring-boot:run
```

The app starts on **`http://localhost:8080`**.
Swagger UI is at **`http://localhost:8080/swagger-ui.html`**.

Override them with environment variables in production (see below).

### Environment Variables

The application can be configured using the following environment variables:

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | R2DBC Connection URL | `r2dbc:postgresql://localhost:5432/ecommerce` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `PORT` | Server port | `8080` |
| `JWT_SECRET` | Secret key for JWT signing | (see `application.yml`) |
| `JWT_EXPIRATION_MS` | JWT expiration time in milliseconds | `86400000` (24 hours) |
| `ORDER_PROMOTION_RATE` | Scheduler rate for order promotion in ms | `300000` (5 mins) |
| `SQL_INIT_MODE` | SQL initialization mode (`always`, `never`) | `always` |
| `LOG_LEVEL` | Application logging level | `DEBUG` |

---

## Running Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=OrderServiceTest

# Run with verbose output
mvn test -Dsurefire.useFile=false
```

**What's tested:**

| Test Class | Type | What it covers |
|---|---|---|
| `AuthServiceTest` | Unit | Register (happy path, duplicate email, DB errors), login (valid credentials, wrong password, user not found) |
| `OrderServiceTest` | Unit | Create order, get by ID, list with/without filter, cancel (all status rejections), scheduler promotion |
| `AuthControllerTest` | WebFlux slice | HTTP status codes, request validation, error responses |
| `OrderControllerTest` | WebFlux slice | Authenticated vs unauthenticated requests, service error propagation |

Service tests use `@ExtendWith(MockitoExtension.class)` — no Spring context, fast.
Controller tests use `@WebFluxTest` with `@Import(SecurityConfig.class)` — loads only the web layer plus real security rules.
`StepVerifier` is used throughout for reactive pipeline assertions. `.block()` is never used in tests.

---

## v2 Proposal — Item Catalogue

Right now, customers describe items themselves when placing an order — they send the name and price in the request body. That's not how a real shop works. In v2 we want items to be **predefined in the database**, and customers simply pick from the catalogue.

### What we're thinking

**A new `GET /api/items` endpoint** that returns all available items in the catalogue. Customers browse this list, pick the items they want, and use the returned `item_id` values in their create-order request.

**The `CreateOrderRequest` simplifies** — instead of sending name and price (which the client shouldn't control), the request becomes just a list of `{ item_id, quantity }` pairs. The server looks up the name and price, validates stock, and takes care of the rest.

### Schema changes

Rather than creating a new table, we'd repurpose the existing `items` table — adding `stock`, `active`, and `created_at` columns to turn it into a proper catalogue. The `order_item_mappings` table would get `quantity` and `price_at_order` columns to snapshot what was actually purchased.

```sql
-- items becomes the product catalogue
ALTER TABLE items
    ADD COLUMN stock      INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN active     BOOLEAN   NOT NULL DEFAULT true,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();

-- mappings hold the per-order snapshot
ALTER TABLE order_item_mappings
    ADD COLUMN quantity       INTEGER       NOT NULL DEFAULT 1,
    ADD COLUMN price_at_order NUMERIC(15,2) NOT NULL DEFAULT 0;
```

### Why snapshot price in the mapping table?

Same reason we snapshot it today — an order is a contract. If an item goes from ₹499 to ₹599 next week, orders placed this week should still show ₹499. Storing the price at order time in `order_item_mappings` gives us that guarantee.

### Stock validation

When an order is created, the service would:
1. Look up each `item_id` in the catalogue
2. Check that the item is `active = true`
3. Check that `stock >= requested quantity`
4. Deduct the stock atomically before confirming the order
5. Snapshot the name and price into the mapping row

### What about admin item management?

An optional `POST /api/items` and `PUT /api/items/{id}` for admins to add and update catalogue items. This would require reintroducing a `role` field — either stored in the JWT claims (simpler) or looked up from the `users` table on each request (keeps the token lean). We've intentionally left this for later since the immediate goal is just the catalogue listing.

### Files that would change

| File | Change |
|---|---|
| `schema.sql` | Alter `items` and `order_item_mappings` as above |
| `OrderItem.java` | Add `stock`, `active`, `createdAt` |
| `OrderItemMapping.java` | Add `quantity`, `priceAtOrder` |
| `ItemResponse.java` | New DTO for catalogue listing |
| `ItemController.java` | New — `GET /api/items` |
| `ItemService.java` | New — fetch active items |
| `CreateOrderRequest.java` | Replace item fields with just `itemId` + `quantity` |
| `OrderService.java` | Look up items, validate stock, snapshot, deduct |

This is a self-contained change — nothing in the auth or scheduler flow needs to touch.

---

*Built iteratively with care. Questions, feedback, or contributions welcome!*