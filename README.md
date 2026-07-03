<div align="center">

# 🔍 CodeLens AI

### Semantic Code Intelligence Engine for Java Repositories

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Gemini](https://img.shields.io/badge/Gemini-Embeddings-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/)

**Index any public Java GitHub repository → Parse its AST → Generate embeddings → Search semantically → Visualize dependencies**

[Features](#-features) · [Architecture](#-architecture) · [Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Tech Stack](#-tech-stack)

</div>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🧠 **Semantic Code Search** | Ask natural language questions like *"Where is the JWT token generated?"* and get ranked results with cosine similarity scores |
| 🌐 **Interactive Dependency Graph** | Visualize class containment, method calls, and inter-class dependencies using an interactive Vis.js network graph |
| 🔬 **AST-Based Parsing** | Uses JavaParser to extract classes, methods, call relationships, and source code from any Java repository |
| 📊 **Impact Analysis** | BFS-based traversal to find all upstream callers affected by a code change, with configurable depth |
| ⚡ **Async Indexing Pipeline** | Non-blocking background processing — submit a repo URL and get a 202 response while indexing happens in parallel |
| 🔐 **JWT Authentication** | Secure user registration/login with BCrypt password hashing and JWT bearer tokens |
| 🗄️ **Vector Store (pgvector)** | Code embeddings stored in PostgreSQL via Spring AI's pgvector integration with HNSW indexing |
| 🔁 **Auto-Retry with Backoff** | Transient failures (API rate limits, DB contention) are retried automatically with exponential backoff |
| 💾 **Redis Caching** | Search results are cached in Redis to avoid redundant embedding API calls |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CodeLens AI Backend                         │
│                                                                     │
│  ┌──────────┐    ┌──────────────┐    ┌───────────────────────────┐  │
│  │ Auth     │    │ Repo         │    │ Search                    │  │
│  │ Controller│    │ Controller   │    │ Controller                │  │
│  └────┬─────┘    └──────┬───────┘    └──────────┬────────────────┘  │
│       │                 │                       │                   │
│  ┌────▼─────┐    ┌──────▼───────┐    ┌──────────▼────────────────┐  │
│  │ JWT      │    │ Indexing     │    │ Search Service            │  │
│  │ Service  │    │ Service      │    │ (Vector Search + Cache)   │  │
│  └──────────┘    │ (Async)      │    └──────────┬────────────────┘  │
│                  └──┬───┬───┬───┘               │                   │
│                     │   │   │                   │                   │
│              ┌──────┘   │   └──────┐            │                   │
│              ▼          ▼          ▼            ▼                   │
│  ┌───────────────┐ ┌────────┐ ┌──────────┐ ┌────────────┐          │
│  │ GitHub Service│ │AST     │ │ Gemini   │ │ pgvector   │          │
│  │ (Fetch files) │ │Parser  │ │Embedding │ │ Store      │          │
│  └───────────────┘ └────────┘ └──────────┘ └────────────┘          │
└─────────────────────────────────────────────────────────────────────┘
         │                                         │
    ┌────▼────┐                           ┌────────▼────────┐
    │ GitHub  │                           │  PostgreSQL 16  │
    │  API    │                           │  + pgvector     │
    └─────────┘                           │  + Redis 7      │
                                          └─────────────────┘
```

### How It Works

1. **Register** — User submits a public GitHub Java repository URL
2. **Fetch** — `GitHubService` clones all `.java` files via the GitHub API
3. **Parse** — `JavaAstService` uses JavaParser to extract classes, methods, call graphs, and source code
4. **Embed** — `GeminiEmbeddingService` generates 768-dimensional vector embeddings for each code entity
5. **Store** — Entities go to PostgreSQL, embeddings go to pgvector, dependency edges link them
6. **Search** — Natural language queries are embedded and matched against stored vectors using cosine similarity
7. **Visualize** — The frontend renders an interactive dependency graph with Vis.js

---

## 🚀 Quick Start

### Prerequisites

- **Java 21** (or Docker)
- **Docker & Docker Compose**
- **Gemini API Key** — [Get one here](https://aistudio.google.com/app/apikey)

### 1. Clone the Repository

```bash
git clone https://github.com/Monishrajpalanivelu/CodeLens-AI.git
cd CodeLens-AI
```

### 2. Configure Environment Variables

Create a `.env` file in the project root:

```env
GEMINI_API_KEY=your_gemini_api_key_here
JWT_SECRET=your_secure_random_jwt_secret_here
```

> **💡 Tip:** Generate a secure JWT secret with:
> ```bash
> openssl rand -base64 32
> ```

### 3. Run with Docker Compose (Recommended)

```bash
docker-compose up --build
```

This spins up:
- **PostgreSQL 16** with pgvector extension on port `5432`
- **Redis 7** on port `6379`
- **CodeLens AI Backend** on port `8080`

### 4. Open the Application

Navigate to **[http://localhost:8080](http://localhost:8080)** in your browser.

### Alternative: Run Locally (Without Docker)

If you have PostgreSQL and Redis running locally:

```bash
# Set environment variables
export GEMINI_API_KEY=your_key
export JWT_SECRET=your_secret

# Build and run
./mvnw spring-boot:run
```

---

## 📖 API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register a new user account |
| `POST` | `/api/auth/login` | Login and receive JWT token |

**Register Request:**
```json
{
  "username": "developer",
  "email": "dev@example.com",
  "password": "securepassword"
}
```

**Login Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "developer",
  "email": "dev@example.com"
}
```

> All endpoints below require `Authorization: Bearer <token>` header.

---

### Repositories

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/repos` | Register & index a GitHub repository |
| `GET` | `/api/repos` | List all your repositories |
| `GET` | `/api/repos/{id}` | Get repository details |
| `GET` | `/api/repos/{id}/status` | Poll indexing status (`PENDING` → `INDEXING` → `DONE`/`FAILED`) |
| `GET` | `/api/repos/{id}/graph` | Get dependency graph (nodes + edges) |
| `POST` | `/api/repos/{id}/retry` | Retry a failed indexing job |
| `DELETE` | `/api/repos/{id}` | Delete repository and all indexed data |

**Register Repository:**
```json
{
  "githubUrl": "https://github.com/spring-projects/spring-petclinic"
}
```

---

### Semantic Search

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/search?repoId={id}&q={query}&topK={k}` | Semantic code search with cosine similarity |

**Example:**
```
GET /api/search?repoId=1&q=How are users authenticated&topK=10
```

**Response:**
```json
[
  {
    "entityId": 42,
    "name": "authenticate",
    "entityType": "FUNCTION",
    "filePath": "src/main/java/com/example/AuthService.java",
    "snippet": "public Authentication authenticate(String username, String password) {...}",
    "score": 0.8723
  }
]
```

---

### Impact Analysis

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/impact/{entityId}?repoId={id}&maxDepth={n}` | BFS impact analysis — find all affected callers |
| `GET` | `/api/impact/{entityId}/direct?repoId={id}` | Direct dependents only |

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.3 |
| **Security** | Spring Security + JWT (jjwt 0.12.5) |
| **Database** | PostgreSQL 16 + pgvector |
| **Vector Store** | Spring AI pgvector (HNSW index, cosine distance) |
| **Embeddings** | Google Gemini `gemini-embedding-001` (768 dimensions) |
| **Caching** | Redis 7 + Spring Cache |
| **AST Parsing** | JavaParser 3.26 |
| **DB Migrations** | Flyway |
| **Resilience** | Spring Retry (exponential backoff) |
| **Frontend** | Vanilla HTML/CSS/JS, Vis.js, Prism.js |
| **Containerization** | Docker (multi-stage build) |

---

## 📂 Project Structure

```
CodeLens-AI/
├── src/main/java/com/codelens/ai/
│   ├── AiApplication.java            # Entry point with .env loader
│   ├── ai/
│   │   ├── GeminiEmbeddingService.java    # Gemini API embedding client
│   │   └── VectorSearchService.java       # pgvector search operations
│   ├── ast/
│   │   └── JavaAstService.java            # JavaParser AST extraction
│   ├── auth/
│   │   ├── AuthController.java            # Login/Register endpoints
│   │   ├── JwtService.java                # JWT token generation/validation
│   │   └── JwtAuthenticationFilter.java   # Security filter chain
│   ├── config/
│   │   ├── AsyncConfig.java               # Thread pool for async indexing
│   │   ├── RedisConfig.java               # Redis cache configuration
│   │   ├── SecurityConfig.java            # Spring Security setup
│   │   └── VectorStoreConfig.java         # pgvector store bean
│   ├── controller/
│   │   ├── RepoController.java            # Repository CRUD + graph
│   │   ├── SearchController.java          # Semantic search endpoint
│   │   └── ImpactController.java          # Impact analysis endpoint
│   ├── dto/                               # Request/Response DTOs
│   ├── graph/
│   │   ├── DependencyGraph.java           # In-memory graph for BFS
│   │   └── ImpactResult.java              # Impact analysis result model
│   ├── model/                             # JPA entities
│   ├── repository/                        # Spring Data JPA repositories
│   └── service/
│       ├── GitHubService.java             # GitHub API file fetcher
│       ├── GraphService.java              # Graph construction + BFS
│       ├── IndexingService.java           # Async indexing pipeline
│       └── SearchService.java             # Semantic search with caching
├── src/main/resources/
│   ├── application.properties             # App configuration
│   ├── db/migration/                      # Flyway SQL migrations
│   └── static/                            # Frontend (HTML/CSS/JS)
├── docker-compose.yml                     # Full stack orchestration
├── Dockerfile                             # Multi-stage Java build
└── pom.xml                                # Maven dependencies
```

---

## ⚙️ Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | ✅ | Google Gemini API key for generating code embeddings |
| `JWT_SECRET` | ✅ | Secret key for signing JWT tokens (use a strong random value) |
| `SPRING_DATASOURCE_URL` | ❌ | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/codelens`) |
| `SPRING_DATASOURCE_USERNAME` | ❌ | Database username (default: `postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | ❌ | Database password (default: `postgres`) |
| `SPRING_DATA_REDIS_HOST` | ❌ | Redis host (default: `localhost`) |
| `SPRING_DATA_REDIS_PORT` | ❌ | Redis port (default: `6379`) |

---

## 📄 Database Schema

The application uses **Flyway** for automatic schema management. Key tables:

| Table | Purpose |
|-------|---------|
| `users` | Registered user accounts (username, email, bcrypt password) |
| `repositories` | Tracked GitHub repos with indexing status |
| `code_entities` | Parsed classes and methods with source code and vector doc IDs |
| `dependencies` | Dependency edges (CONTAINS, CALLS, DEPENDS_ON) |
| `vector_store` | Spring AI managed table for pgvector embeddings |

---

## 🧪 Running Tests

```bash
./mvnw test
```

Tests use an in-memory H2 database and skip external services (Redis, Gemini, pgvector).

---

## 📜 License

This project is open-source and available for educational and personal use.

---

