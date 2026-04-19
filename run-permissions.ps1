# PowerShell script to grant permissions to realnewsuser
# This uses the pg_restore/psql compatible commands via docker or direct postgres connection

# Configuration
$pgHost = "localhost"
$pgPort = 5432
$pgDatabase = "realnews"
$pgSuperUser = "postgres"
$pgPassword = "postgres"  # Change this to your actual postgres password

# SQL commands to run
$sqlCommands = @"
-- Grant all privileges on the public schema to realnewsuser
GRANT ALL PRIVILEGES ON SCHEMA public TO realnewsuser;

-- Grant privileges on all existing tables in public schema
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO realnewsuser;

-- Grant privileges on all sequences (for auto-increment/serial columns)
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO realnewsuser;

-- Set default privileges for future tables created in public schema
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO realnewsuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO realnewsuser;

-- Set default privileges for future functions
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO realnewsuser;

-- Also grant permissions on the schema itself to the postgres role's owned objects
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA public GRANT ALL ON TABLES TO realnewsuser;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA public GRANT ALL ON SEQUENCES TO realnewsuser;
"@

Write-Host "Attempting to grant permissions to realnewsuser on the public schema..."
Write-Host "Host: $pgHost, Port: $pgPort, Database: $pgDatabase"
Write-Host ""

# Try method 1: Using PGPASSWORD environment variable (for psql)
Write-Host "Method 1: Attempting with PostgreSQL command-line client..."
$env:PGPASSWORD = $pgPassword

# Try different possible locations
$psqlPaths = @(
    "psql",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe",
    "C:\Program Files\PostgreSQL\15\bin\psql.exe",
    "C:\Program Files\PostgreSQL\14\bin\psql.exe",
    "C:\PostgreSQL\bin\psql.exe"
)

$psqlFound = $false
foreach ($psqlPath in $psqlPaths) {
    if (Test-Path $psqlPath -ErrorAction SilentlyContinue) {
        Write-Host "Found psql at: $psqlPath"
        Write-Host "Running SQL commands..."

        try {
            $sqlCommands | & $psqlPath -h $pgHost -p $pgPort -U $pgSuperUser -d $pgDatabase -w
            $psqlFound = $true
            Write-Host ""
            Write-Host "✓ Permissions granted successfully!" -ForegroundColor Green
            break
        } catch {
            Write-Host "Error executing SQL: $_" -ForegroundColor Yellow
        }
    }
}

if (-not $psqlFound) {
    Write-Host ""
    Write-Host "Could not find psql executable." -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install PostgreSQL Client tools or run this SQL manually:"
    Write-Host $sqlCommands
    Write-Host ""
    Write-Host "To run manually:"
    Write-Host "1. Open pgAdmin or a PostgreSQL admin tool"
    Write-Host "2. Connect to the 'realnews' database as 'postgres' user"
    Write-Host "3. Paste the SQL commands above"
    Write-Host "4. Execute"
}

# Clean up
Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

