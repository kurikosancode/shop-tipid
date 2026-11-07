# ShopTipid

A command-line shop written in Java for Data Structures and Algorithms. Customers browse, buy and get recommendations; admins manage products
and users. No GUI and no web: terminal only.

## Requirements

- Java 17 or newer (JDK)
- Maven 3.9+
- Internet on the first build only (Maven downloads Gson)
- Recommended: Windows Terminal, macOS Terminal or any Linux terminal (they show the colors)

## Run it

Run every command from the project root (the folder with `pom.xml`).

```
mvn -q compile exec:java
```

Build a runnable jar and run it:

```
mvn clean package
java -jar target/recommendation-engine-1.0.0.jar
```

Turn colors off if you see strange characters like `←[36m`:

```
java -jar target/recommendation-engine-1.0.0.jar --no-color
```
(PowerShell with Maven: `mvn -q compile exec:java "-Dexec.args=--no-color"`.
Or set the environment variable `NO_COLOR=1` before running.)

The program reads and writes the `data/` folder next to where you start it. If
`data/products.json` is missing or corrupted, the built-in 90-product catalog inside the jar
is restored automatically.

## Default account

| Role  | Username | Password |
|-------|----------|----------|
| Admin | admin    | admin123 |

Customers create their own account with **Sign up**.

## Features

**Customer:** browse by category / label, search by name, product details, cart (add, change,
remove, view), checkout, Buy now, purchase history with full receipts, profile with spending
per category, "Recommended for you", recommendation settings (ranking parameter, weights,
optional price limit), Bundle Builder.

**Admin:** add / edit / remove products, view all users with profiles and order history,
remove users (with confirmation; cannot remove yourself or the last admin), sales overview
(total revenue, best-selling products, most active users).

## Where each course requirement is in the code

| Requirement | Where | Data structure and complexity |
|---|---|---|
| Hash table | `AuthService` (users), `ProductService` (products, category index, label index), `CartService` (carts), `OrderService` (orders by user) | `HashMap`: O(1) average lookup |
| Similarity graph | `structures/SimilarityGraph`, `structures/Edge` | `HashMap<String, List<Edge>>` adjacency list; build O(n^2), update after checkout O(k^2) |
| BFS | `SimilarityGraph.collectCandidates` | `ArrayDeque` queue, depth 1-2, O(n + m) |
| Heap / priority queue | `RecommendationService.recommend` | `PriorityQueue` ordered by the user's ranking parameter, O(m log m) |
| Backtracking | `BundleService.Search.explore` (own code) | choose / recurse / un-choose, pruned by budget and by a score bound |

Edge weight = `(sameLabel ? 3 : 0) + (sameCategory ? 1 : 0) + coPurchaseCount * 0.5`

Recommendation score = `w1*similarity + w2*popularity + w3*rating + w4*priceFit`
(every factor normalized to 0..1). All weights and constants are in `config/Config.java`.

## Project structure

```
shop-tipid/
├── pom.xml
├── README.md
├── data/
│   ├── users.json
│   ├── products.json
│   └── orders.json
└── src/main/java/com/shop/
    ├── Main.java
    ├── config/      Config
    ├── model/       User, Role, Product, Order, OrderItem, CartLine,
    │                RankBy, RecommendationSettings
    ├── structures/  SimilarityGraph, Edge
    ├── service/     AuthService, ProductService, CartService, OrderService,
    │                RecommendationService, BundleService
    ├── storage/     JsonStore
    └── ui/          ScreenUtil, ColorUtil, InputUtil, ProductView, AuthMenu,
                     CustomerMenu, HistoryMenu, RecommendationScreens,
                     RecommendationMenu, AdminMenu
```

## Data files

- `users.json`: username, role, join date, salted SHA-256 password hash, and each user's
  recommendation settings. Passwords are never stored in plain text.
- `products.json`: the catalog (id, name, price, category, label, rating, purchaseCount, stock).
- `orders.json`: every order with its lines. Each line stores the price at purchase.

Everything is saved after every change. Do not edit the files while the program is running
(the program rewrites them from memory).

## Sample terminal output

Colors are not shown here. Numbers are illustrative; your values depend on the data.

Customer menu:

```
+==============================================================+
|                       SHOP  |  juan_dc                       |
+==============================================================+

  [1] Browse by category / label
  [2] Search by name
  [3] Recommended for you
  [4] View cart (3 items)
  [5] Purchase history
  [6] My profile
  [7] Recommendation settings
  [8] Bundle Builder
  [0] Log out

Choose an option:
```

Receipt:

```
+==============================================================+
|                           RECEIPT                            |
+==============================================================+

Order ID:  ORD-00007
Date:      2026-11-07 15:42
Customer:  juan_dc

Item                      Qty           Price             Total
----------------------------------------------------------------
Jogger Sweatpants           2      PHP 799.00      PHP 1,598.00
Yoga Block Set of 2         1      PHP 499.00        PHP 499.00
----------------------------------------------------------------
                                             TOTAL: PHP 2,097.00

Prices shown are the prices at the time of purchase.
```

Suggestions after that purchase:

```
+==============================================================+
|                      YOU MAY ALSO LIKE                       |
+==============================================================+

Because you bought: Jogger Sweatpants, Yoga Block Set of 2
Ranked by: Best match

 1  Non-Slip Yoga Mat 6mm                           PHP 799.00
    Fitness > Yoga
    Same label: Yoga | Popularity 74 | Rating 4.6

 2  Stretch Denim Jeans                           PHP 1,499.00
    Clothing > Pants
    Same label: Pants | Popularity 77 | Rating 4.6

 3  Slim Fit Chino Pants                         PHP 1,196.00
    Clothing > Pants
    Same label: Pants | Popularity 62 | Rating 4.5

 4  Yoga Strap with D-Rings 8ft                     PHP 349.00
    Fitness > Yoga
    Same label: Yoga | Popularity 17 | Rating 4.2

 5  Classic Cotton Crew Neck T-Shirt                PHP 349.00
    Clothing > Shirt
    Same category: Clothing | Popularity 91 | Rating 4.4

Enter a number to add that item to your cart (0 = back):
```

Bundle Builder (budget PHP 5,000, up to 4 items, all categories):

```
+==============================================================+
|                         YOUR BUNDLE                          |
+==============================================================+

Item                            Category                   Price
----------------------------------------------------------------
SPF 50 Sunscreen Lotion 100ml   Beauty                PHP 499.00
Atomic Habits                   Books                 PHP 599.00
SoundCore Wireless Earbuds      Electronics         PHP 1,999.00
Salted Potato Chips 150g        Groceries              PHP 89.00
----------------------------------------------------------------
Total price:   PHP 3,186.00
Budget:        PHP 5,000.00
Left over:     PHP 1,814.00
Match score:   2.95

Backtracking explored 120 branches, pruned 35 over-budget items and 48 low-score branches.

Add all of these items to your cart? (y/n):
```

## Known limitations

- **Password hashing:** salted SHA-256 (as the course allows) is fast. A real system would use
  bcrypt, scrypt or Argon2. There is no password change or reset, and no limit on how many
  login attempts can be made overall (only 3 per login screen).
- **Hidden password input:** works only when Java has a real console. Under `mvn exec:java`
  the prompt may fall back to visible typing (it is labeled "(visible)").
- **Colors on Windows:** the classic PowerShell/CMD console may print raw color codes.
  Use Windows Terminal or `--no-color`.
- **One program at a time:** the JSON files are not locked. Two running copies, or editing a
  file by hand while it runs, will overwrite each other's changes.
- **Carts are not saved.** They live in memory and are lost on log out or exit.
- **Removed users:** their past orders stay in `orders.json` for the sales records. A new
  account created later with the same username would see that old history.
- **No admin creation screen:** only the default admin exists. To make more admins, change a
  user's `role` to `ADMIN` in `users.json` while the program is closed.
- **"Viewed" products are not tracked:** recommendations are based on purchased products only
  (and co-purchases), not on what a user has merely looked at.
- **Money uses `double`** (rounded to 2 decimals). Fine for a demo, but real systems use
  `BigDecimal` or integer centavos.
- **Graph rebuild:** the graph is rebuilt in O(n^2) after admin product changes. This is fast
  for ~100 products but would need an incremental update for large catalogs.
- **Bundle Builder:** limited to 4 items and one item per category; its score is a simple
  rating / popularity / similarity mix.
- **Search** is a simple case-insensitive "contains" match on the product name.
- **Recommendations with a price limit:** a very low limit can leave nothing to recommend
  (the screen says so and suggests changing the limit).