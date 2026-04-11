# Supabase Connection Configuration

## Your Supabase Details

```
Host:     aws-1-us-west-2.pooler.supabase.com
Port:     5432
Database: postgres
User:     postgres.cjzajtezmuehdytwbbxy
```

## Configuration Files

### `.env` (Sensitive - DO NOT COMMIT)
```dotenv
SUPABASE_JDBC_URL=postgresql://postgres.cjzajtezmuehdytwbbxy:No12blame2512@aws-1-us-west-2.pooler.supabase.com:5432/postgres
SUPABASE_DB_USER=postgres.cjzajtezmuehdytwbbxy
SUPABASE_DB_PASSWORD=No12blame2512
```

### `application.yml` (Checked into Git)
```yaml
spring:
  datasource:
    url: ${SUPABASE_JDBC_URL:jdbc:postgresql://localhost:5432/newsletter}
    username: ${SUPABASE_DB_USER:postgres}
    password: ${SUPABASE_DB_PASSWORD:postgres}
```

### `EnvironmentConfig.java` (Auto-loads .env)
- Located: `src/main/java/com/realnewsletter/config/EnvironmentConfig.java`
- Automatically loads variables from `.env` file at startup
- No manual configuration needed

## Running the Application

```bash
mvn spring-boot:run
```

The application will:
1. Load environment variables from `.env`
2. Connect to your Supabase instance
3. Run Flyway migrations
4. Start the REST API on `http://localhost:8080`

## Connection Pool Configuration

The application uses HikariCP with these settings:
- Connection timeout: 30 seconds
- Max pool size: 5 connections
- Prepared statement caching: Disabled (pgBouncer compatibility)

## Testing

Tests use H2 in-memory database and don't require Supabase:
```bash
mvn test
```

## Important Security Notes

⚠️ **NEVER commit `.env` to version control!**
- `.env` is in `.gitignore`
- Use `.env.dev` as a template for new developers
- Each developer should create their own `.env` from `.env.dev`

For production deployment:
- Use environment variables in your deployment platform (Docker, Heroku, etc.)
- Do not embed credentials in configuration files

