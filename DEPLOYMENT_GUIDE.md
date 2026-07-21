# Navgrow Engineering — Deployment Guide

## Architecture Overview

```
                    ┌─────────────────────────────┐
                    │   navgrow.org (Hostinger)    │
                    │   React SPA → /public_html   │
                    └─────────────┬───────────────┘
                                  │ HTTPS
                    ┌─────────────▼───────────────┐
                    │    Ubuntu VPS (2GB+)          │
                    │  api.navgrow.org → :8080      │
                    │  Nginx + SSL (Let's Encrypt)  │
                    └─────────────┬───────────────┘
                                  │
                    ┌─────────────▼───────────────┐
                    │   Spring Boot API + PG 15    │
                    └──────────────────────────────┘
```

---

## Frontend Deployment (Hostinger Shared Hosting)

### Step 1 — Build

```bash
cd Navgrow
cp .env.example .env
# Edit .env:
#   VITE_API_BASE_URL=https://api.navgrow.org/api
#   VITE_RAZORPAY_KEY_ID=rzp_live_xxxxxxxxx

npm install
npm run build
# Output: /dist
```

### Step 2 — Upload to Hostinger

1. Log in to Hostinger hPanel
2. Go to **Files → File Manager**
3. Navigate to `public_html/`
4. Delete existing files (keep `.htaccess` if present)
5. Upload all contents of `/dist` (not the folder itself)
6. Verify `/dist/index.html` is at `public_html/index.html`

The `public/.htaccess` is already included for SPA routing.

### Step 3 — Verify

- Visit https://navgrow.org — React app loads
- Visit https://navgrow.org/shop — works (SPA routing)
- Check Nginx/browser network tab — API calls go to https://api.navgrow.org

---

## Backend Deployment (Ubuntu 22.04 VPS)

### Step 1 — Server Setup

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java 17
sudo apt install openjdk-17-jdk -y
java -version   # should show 17.x

# Install PostgreSQL 15
sudo apt install postgresql postgresql-contrib -y
sudo systemctl start postgresql && sudo systemctl enable postgresql

# Install Nginx
sudo apt install nginx -y
sudo systemctl start nginx && sudo systemctl enable nginx

# Install Maven (for build)
sudo apt install maven -y
```

### Step 2 — Database Setup

```bash
sudo -u postgres psql << 'SQL'
CREATE DATABASE navgrow_db;
CREATE USER navgrow WITH ENCRYPTED PASSWORD 'YOUR_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE navgrow_db TO navgrow;
ALTER DATABASE navgrow_db OWNER TO navgrow;

-- Enable required PostgreSQL extensions
\c navgrow_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
SQL
```

### Step 3 — Deploy Backend

```bash
# Clone or upload backend files to server
mkdir -p /opt/navgrow && cd /opt/navgrow

# Create .env with production values
cat > .env << 'EOF'
DB_URL=jdbc:postgresql://localhost:5432/navgrow_db
DB_USER=navgrow
DB_PASSWORD=YOUR_STRONG_PASSWORD
JWT_SECRET=YOUR_64_CHAR_RANDOM_SECRET
JWT_EXPIRATION=86400000
JWT_REFRESH=604800000
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=info@navgrow.org
MAIL_PASSWORD=YOUR_GMAIL_APP_PASSWORD
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxx
RAZORPAY_KEY_SECRET=YOUR_SECRET
BASE_URL=https://api.navgrow.org
FRONTEND_URL=https://navgrow.org
PORT=8080
EOF

# Build (from your local machine, then SCP jar)
mvn clean package -DskipTests -Pprod
scp target/navgrow-backend-1.0.0.jar user@your-vps:/opt/navgrow/

# OR build on server:
mvn clean package -DskipTests

# Run with systemd (recommended for production)
```

### Step 4 — Systemd Service

```bash
sudo nano /etc/systemd/system/navgrow.service
```

```ini
[Unit]
Description=Navgrow Engineering Backend API
After=network.target postgresql.service

[Service]
Type=simple
User=navgrow
WorkingDirectory=/opt/navgrow
EnvironmentFile=/opt/navgrow/.env
ExecStart=/usr/bin/java \
  -Dspring.profiles.active=prod \
  -Dserver.port=${PORT} \
  -jar navgrow-backend-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=navgrow

[Install]
WantedBy=multi-user.target
```

```bash
# Create navgrow user
sudo useradd -m -s /bin/bash navgrow
sudo chown -R navgrow:navgrow /opt/navgrow

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable navgrow
sudo systemctl start navgrow
sudo systemctl status navgrow

# View logs
sudo journalctl -u navgrow -f
```

### Step 5 — Nginx Reverse Proxy

```bash
sudo nano /etc/nginx/sites-available/navgrow-api
```

```nginx
server {
    listen 80;
    server_name api.navgrow.org;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.navgrow.org;

    ssl_certificate     /etc/letsencrypt/live/api.navgrow.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.navgrow.org/privkey.pem;

    # Max upload size — MUST be >= Spring's max-request-size (50MB) or uploads fail
    # with "413 Request Entity Too Large" at the proxy before reaching the app.
    # The app itself caps individual files at 8MB (FileUploadController).
    client_max_body_size 50M;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' https://checkout.razorpay.com;" always;
    
    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=30r/m;
    limit_req zone=api burst=60 nodelay;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
        
        # CORS — handled by Spring, but allow preflight
        if ($request_method = OPTIONS) {
            return 204;
        }
    }
    
    # Health check — no rate limit
    location /api/actuator/health {
        limit_req off;
        proxy_pass http://127.0.0.1:8080;
    }
}
```

```bash
# Enable site
sudo ln -s /etc/nginx/sites-available/navgrow-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo nginx -s reload

# SSL certificate (Let's Encrypt)
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d api.navgrow.org
# Auto-renewal is set up automatically
```

### Step 6 — Post-Deployment Checks

```bash
# Check API is running
curl https://api.navgrow.org/api/actuator/health
# Expected: {"status":"UP"}

# Check Swagger
open https://api.navgrow.org/api/swagger-ui.html

# Change default admin password immediately!
curl -X POST https://api.navgrow.org/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@navgrow.org","password":"Admin@123"}'
# Get token, then:
curl -X POST https://api.navgrow.org/api/users/me/change-password \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"Admin@123","newPassword":"YOUR_STRONG_NEW_PASSWORD"}'
```

---

## Gmail App Password Setup (for Email)

1. Go to Google Account → Security → 2-Step Verification (enable it)
2. Go to App Passwords → Generate for "Mail" + "Other device" → name it "Navgrow"
3. Copy the 16-character app password → use as `MAIL_PASSWORD` in `.env`

---

## Razorpay Live Keys

1. Log in to [dashboard.razorpay.com](https://dashboard.razorpay.com)
2. Go to **Settings → API Keys → Generate Live Key**
3. Set `RAZORPAY_KEY_ID` (starts with `rzp_live_`) in backend `.env`
4. Set `VITE_RAZORPAY_KEY_ID` (same key) in frontend `.env`
5. Set `RAZORPAY_KEY_SECRET` in backend `.env`

---

## Backup Strategy

```bash
# Daily PostgreSQL backup (add to crontab)
0 2 * * * pg_dump -U navgrow navgrow_db | gzip > /backups/navgrow_$(date +\%Y\%m\%d).sql.gz

# Retain 30 days
0 3 * * * find /backups -name "navgrow_*.sql.gz" -mtime +30 -delete
```

---

## Monitoring

```bash
# Application logs
sudo journalctl -u navgrow -n 100 -f

# Nginx logs
sudo tail -f /var/log/nginx/error.log

# Database connections
sudo -u postgres psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname='navgrow_db';"

# Check API health
watch -n 30 'curl -s https://api.navgrow.org/api/actuator/health'
```
