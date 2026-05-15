# Navgrow Engineering Service — Backend API

> Spring Boot 3.2 | Java 17 | PostgreSQL 15 | JWT | Razorpay | Flyway

## Quick Start

```bash
# Prerequisites: Java 17, Maven 3.9+, PostgreSQL 15+

# 1. Create database
psql -U postgres -c "CREATE DATABASE navgrow_db;"
psql -U postgres -c "CREATE USER navgrow WITH PASSWORD 'your_password';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE navgrow_db TO navgrow;"

# 2. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 3. Run
mvn spring-boot:run -Dspring.profiles.active=dev

# 4. Swagger UI
open http://localhost:8080/api/swagger-ui.html
```

## Project Structure

```
src/main/java/com/navgrow/
├── NavgrowApplication.java      # @SpringBootApplication entry point
├── config/                      # Security, CORS, Razorpay, Swagger
├── controller/                  # 15 REST controllers
├── entity/                      # 16 JPA entities
├── enums/                       # 8 enum types
├── repository/                  # 14 Spring Data repositories
├── security/                    # JwtUtil, JwtAuthFilter
├── service/                     # EmailService, PasswordResetService
│   └── impl/                    # UserDetailsServiceImpl
├── exception/                   # GlobalExceptionHandler + custom exceptions
└── util/                        # SlugUtil, OrderNumberGenerator
src/main/resources/
├── application.yml              # Main config (dev + prod profiles)
└── db/migration/
    ├── V1__Initial_Schema.sql   # 13 tables, 8 enums, indexes, triggers
    └── V2__Password_Reset_Reviews_Coupons.sql  # Extensions
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2 (Java 17) |
| Security | Spring Security + JWT (JJWT 0.12.5, HS512) |
| Database | PostgreSQL 15 + Flyway migrations |
| ORM | Spring Data JPA + Hibernate 6 |
| Payment | Razorpay Java SDK 1.4.5 |
| Email | Spring Mail (JavaMailSender) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9 |

## Default Admin

- **Email:** `admin@navgrow.org`
- **Password:** `Admin@123`
- ⚠️ **Change immediately after first login!**

## Production Deployment

```bash
mvn clean package -DskipTests
java -Dspring.profiles.active=prod -jar target/navgrow-backend-1.0.0.jar
```

See `DEPLOYMENT_GUIDE.md` for full Nginx + Ubuntu setup.
