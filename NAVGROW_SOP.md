# NAVGROW ENGINEERING SERVICE PVT. LTD.
# Standard Operating Procedure (SOP) — Digital Platform
# Version 2.0 | April 2026

---

## TABLE OF CONTENTS

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Daily Operations](#3-daily-operations)
4. [Admin Panel Guide](#4-admin-panel-guide)
5. [NavBot AI Chatbot Management](#5-navbot-ai-chatbot-management)
6. [Order Management SOP](#6-order-management-sop)
7. [Content Management SOP](#7-content-management-sop)
8. [Customer Support SOP](#8-customer-support-sop)
9. [Shop & Product Management](#9-shop--product-management)
10. [Security Procedures](#10-security-procedures)
11. [Incident Response](#11-incident-response)
12. [Deployment & Updates](#12-deployment--updates)
13. [Monitoring & Health Checks](#13-monitoring--health-checks)
14. [Backup & Recovery](#14-backup--recovery)

---

## 1. SYSTEM OVERVIEW

### Platform Components

| Component | Technology | URL | Purpose |
|-----------|-----------|-----|---------|
| Frontend Website | React 18 + Vite | https://navgrow.org | Customer-facing SPA |
| Backend API | Spring Boot 3.2 (Java 17) | https://api.navgrow.org | REST API |
| Database | PostgreSQL 15 | localhost:5432 (VPS) | Persistent storage |
| Admin Dashboard | React (same SPA) | https://navgrow.org/admin | Internal management |
| NavBot AI | Claude AI via Anthropic API | /api/chat | Customer support chatbot |
| Payment Gateway | Razorpay | razorpay.com/dashboard | Online payments |
| Email | Gmail SMTP | info@navgrow.org | Transactional emails |

### Key Credentials (Store in 1Password or similar)
- Admin login: `admin@navgrow.org` / `[CHANGE FROM Admin@123]`
- Database: See `.env` on VPS at `/opt/navgrow/.env`
- Razorpay: dashboard.razorpay.com (login: info@navgrow.org)
- Anthropic API: console.anthropic.com

---

## 2. ARCHITECTURE

```
Customer Browser
      │
      ▼
navgrow.org (Hostinger)     ← React SPA (19 pages + 5 admin)
      │
      │ HTTPS API calls
      ▼
api.navgrow.org (VPS)       ← Nginx → Spring Boot :8080
      │
      ├──► PostgreSQL :5432  ← All data (orders, users, products...)
      ├──► Anthropic API     ← NavBot AI chatbot responses
      ├──► Razorpay API      ← Payment processing
      └──► Gmail SMTP        ← Email notifications
```

### Data Flow for a Shop Order

```
1. Customer browses /shop (products loaded from /api/products)
2. Adds to cart (localStorage)
3. Clicks Checkout → enters shipping details
4. POST /api/orders → creates DB record + Razorpay order (server-side)
5. Razorpay Checkout.js opens → customer pays
6. POST /api/orders/payment/verify → HMAC verified server-side
7. Order confirmed in DB → email sent to customer
8. Admin sees order in /admin/orders → updates status → customer tracks at /track-order
```

---

## 3. DAILY OPERATIONS

### Morning Checklist (9:00 AM IST)

```
□ Check API health: curl https://api.navgrow.org/api/actuator/health
□ Review new orders at https://navgrow.org/admin/orders
□ Check unread messages at https://navgrow.org/admin/contacts
□ Check new quote requests at https://navgrow.org/admin/quotes
□ Review new job applications at https://navgrow.org/admin (newApplications KPI)
□ Check Razorpay dashboard for any payment failures
□ Verify email inbox at info@navgrow.org for customer queries
```

### Afternoon Checklist (3:00 PM IST)

```
□ Process any pending orders (update status CONFIRMED → PROCESSING → SHIPPED)
□ Respond to unread contact messages (target: 4-hour response)
□ Follow up on open quote requests
□ Check backend logs: sudo journalctl -u navgrow -n 50
```

### End of Day

```
□ Verify database backup ran (check /backups/ on VPS)
□ Update order tracking numbers for shipped orders
□ Review Razorpay settlements
```

---

## 4. ADMIN PANEL GUIDE

### Accessing the Admin Panel

1. Go to `https://navgrow.org`
2. Click **Sign In** (top right)
3. Login with admin credentials
4. The navbar will show **Admin Dashboard** link in the user dropdown
5. Or navigate directly to `https://navgrow.org/admin`

### Dashboard KPIs (https://navgrow.org/admin)

| KPI | What it shows | Action needed if high |
|-----|--------------|----------------------|
| Pending Orders | Orders awaiting confirmation | Process and confirm |
| Revenue (30 days) | Paid order revenue | Track vs. target |
| Unread Messages | Contact form messages not read | Reply within 24h |
| New Quote Requests | Unreviewed quotes | Review and respond |
| Newsletter Subscribers | Active subscribers | — |
| New Applications | Unreviewed job applications | Review within 3 days |

### Order Management (`/admin/orders`)

**Status Flow:**
```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
                                 ↓
                             CANCELLED (if needed)
```

**To update an order:**
1. Click **Manage** on the order row
2. Click the desired status button
3. For SHIPPED: enter tracking number and courier name first
4. Click **Ship** — this simultaneously sets SHIPPED + tracking

**Order statuses explained:**
- `PENDING` — Order placed, payment pending
- `CONFIRMED` — Payment verified by Razorpay (auto-set)
- `PROCESSING` — Preparing for dispatch
- `SHIPPED` — Dispatched with tracking number
- `DELIVERED` — Confirmed delivered
- `CANCELLED` — Cancelled (do not use after payment — use REFUNDED)
- `REFUNDED` — Payment refunded

### Contact Messages (`/admin/contacts`)

1. Unread messages appear with amber highlight
2. Click message to see full detail in right panel
3. Click **Reply via Email** to open pre-filled reply in Gmail
4. Click **Mark Read** after reviewing/replying
5. Filter by **Unread Only** to prioritise

**Response SOP:**
- Simple queries: reply within 4 hours
- Project/quote queries: reply within 24 hours with a formal quote
- Complaints: escalate to director, reply within 2 hours

### Quote Management (`/admin/quotes`)

1. New quotes arrive with status `NEW`
2. Review scope, service type, estimated range
3. Change status to `REVIEWING` when you start working on it
4. Enter formal `Quoted Amount` in ₹
5. Click **Send Quote Email** — opens pre-filled email
6. After client accepts: set status to `ACCEPTED`
7. After project closure: set to `CLOSED`

### Product Management (`/admin/products`)

See Section 9 for full SOP.

---

## 5. NAVBOT AI CHATBOT MANAGEMENT

### What is NavBot?

NavBot is Navgrow's AI-powered customer assistant built on **Claude AI (Anthropic)**. 
It automatically answers questions about services, products, orders, careers, and 
directs customers to the right page.

### NavBot Capabilities

✅ **Can do:**
- Answer questions about all services, pricing ranges, timeline
- Recommend products for customer needs
- Explain the quote calculator and process
- Guide order tracking
- Share contact information and directions
- Answer FAQs about shipping, returns, GST
- Respond in Hindi or English based on user preference
- Provide discount codes (NAVGROW10, FLAT200, RAILWAY15)
- Explain job openings and application process

❌ **Cannot do:**
- Place or cancel orders (redirects to human)
- Check real-time order status (redirects to /track-order)
- Process refunds (redirects to email)
- Make commitments on custom pricing
- Access customer account data

### Monitoring NavBot

**Check chatbot health:**
```bash
curl -X POST https://api.navgrow.org/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Hello"}]}'
```
Expected: `{"reply":"Hi! I'm NavBot..."}` within 5 seconds.

**Rate limits:** 30 messages per IP per hour. Customer gets a polite message if exceeded.

**Check Anthropic usage:**
- Log in to console.anthropic.com
- Go to Usage → check daily/monthly token consumption
- Current model: `claude-opus-4-5`
- Billing: Pay-per-token

### Updating NavBot Knowledge

To update what NavBot knows (new products, services, prices), edit the `SYSTEM_PROMPT` 
in `ChatService.java`, section marked `== YOUR ROLE ==`.

After any system prompt update:
```bash
cd /opt/navgrow
mvn clean package -DskipTests
sudo systemctl restart navgrow
```

### Common NavBot Scenarios

**Customer asks about a product not in prompt:**
NavBot will say it doesn't have that specific info and direct to shop or email.
→ **Action:** Add product to `SYSTEM_PROMPT` in ChatService.java

**Customer reports NavBot giving wrong info:**
→ **Action:** Review the specific interaction, update SYSTEM_PROMPT, restart service

**NavBot unavailable (Anthropic API down):**
NavBot falls back to: *"I'm having a momentary hiccup! Please reach us directly..."*
→ **Action:** Check console.anthropic.com for API status

---

## 6. ORDER MANAGEMENT SOP

### New Order Received

1. Email notification arrives at `info@navgrow.org` (subject: "Order Confirmed – NGO-...")
2. Log in to admin panel
3. Find order in `/admin/orders` (status: CONFIRMED)
4. Verify: customer details, items, payment status (must be PAID)
5. Update status to `PROCESSING`

### Fulfillment Process

**For Product Orders:**
1. Pick items from inventory
2. Pack securely (use bubble wrap for tools, hard cases for instruments)
3. Generate shipping label via courier partner (DTDC, Delhivery, etc.)
4. Update order: status → `SHIPPED`, enter tracking number + courier name
5. Customer receives email with tracking info automatically

**Shipping Partners:**
- DTDC: dtdc.com | Local: Siliguri branch
- Delhivery: deliveree.com
- Indian Speed Post: indiapost.gov.in (for remote areas)

### Return/Refund Process

1. Customer emails `info@navgrow.org` with order number + photos
2. Admin verifies eligibility (within 7 days, valid reason)
3. If approved: arrange reverse pickup via courier
4. On receipt of product: verify condition
5. Initiate refund via Razorpay Dashboard → Payments → Find payment → Refund
6. Update order status to `REFUNDED`
7. Email customer: refund initiated, 7–10 business days

**Non-refundable:** Customised items, opened consumables, items damaged by user.

---

## 7. CONTENT MANAGEMENT SOP

### Publishing a News Article

1. Go to `/admin/news` (Note: currently accessible via API only — see below)
2. POST to `/api/news` with:
   ```json
   {
     "title": "Article Title",
     "content": "Full article text...",
     "excerpt": "2-3 sentence summary",
     "category": "Project Update",
     "imageUrl": "https://...",
     "tags": ["Indian Railways"],
     "status": "PUBLISHED"
   }
   ```
3. Use Postman or Swagger UI: `https://api.navgrow.org/api/swagger-ui.html`
4. Authenticate first: POST `/api/auth/login` → copy `accessToken`
5. Add header: `Authorization: Bearer {token}`

**Article categories:** Project Update | Company News | Industry | Milestone

### Adding a Project to Portfolio

POST `/api/projects` (admin auth required):
```json
{
  "title": "Project Name",
  "category": "Projects",
  "client": "Client Name",
  "location": "Location, State",
  "year": "2026",
  "description": "Project description...",
  "imageUrl": "https://...",
  "featured": true,
  "sortOrder": 1
}
```

### Adding a Tender Notice

POST `/api/tenders` (admin auth):
```json
{
  "refNumber": "NR/CIVIL/2026/XXX",
  "title": "Tender Title",
  "description": "Scope of work...",
  "valueMin": 500000,
  "valueMax": 1500000,
  "deadline": "2026-07-31",
  "status": "OPEN",
  "featured": true
}
```

### Updating Gallery

POST `/api/gallery` (admin auth):
```json
{
  "title": "Photo Title",
  "category": "Projects",
  "location": "Siliguri, WB",
  "year": "2026",
  "imageUrl": "https://...",
  "sortOrder": 1
}
```

---

## 8. CUSTOMER SUPPORT SOP

### Response Time Targets

| Channel | Target Response Time |
|---------|---------------------|
| Contact form (website) | 24 business hours |
| Email (info@navgrow.org) | 24 business hours |
| Phone (+91 89270 70972) | Same day |
| WhatsApp (+91 89270 70972) | 4 business hours |
| NavBot (AI) | Instant (24/7) |

### Handling Common Queries

**"Where is my order?"**
1. Ask for order number (format: NGO-YYYYMMDD-XXXX)
2. Check admin panel or direct to `/track-order`
3. If SHIPPED: share tracking number from admin panel

**"I want to return my product"**
1. Verify: within 7 days of delivery?
2. Ask for photos of product issue
3. If eligible: raise a return request (see Section 6)
4. Send return label to customer

**"Can I get a bulk discount?"**
1. Note requirements (product, quantity, delivery location)
2. Forward to sales team / director
3. Reply within 24 hours with bulk quote
4. For orders >₹50,000 consider 10–15% discount

**"I need a project quote"**
1. Direct to `/quote-calculator` for instant estimate
2. For formal quote: ask for project details, scope, timeline
3. Create quote request in admin panel
4. Reply within 24 hours with formal quotation on letterhead

**Escalation Matrix:**
```
L1: Website/Order/Product query → Customer support executive
L2: Technical/Engineering query → Project manager  
L3: Billing/Legal/Complaint → Director
```

---

## 9. SHOP & PRODUCT MANAGEMENT

### Adding a New Product

1. Go to `/admin/products` → click **Add Product**
2. Fill all required fields:
   - Name, Category, Price (excl. GST), MRP
   - Stock quantity, Description
   - Image URL (host on Cloudinary or similar)
   - Badge (Bestseller / New / Top Rated / ISI Certified)
3. Toggle **Featured** for homepage spotlight
4. Click **Create Product**

### Pricing Rules

- All prices exclude GST (18% added at checkout)
- MRP should be the market/manufacturer price
- Discount = ((MRP - Price) / MRP) × 100
- Minimum margin: 15% above cost price
- B2B bulk orders (>10 units): consult director for pricing

### Inventory Management

- Update stock quantity after each physical dispatch: PATCH `/api/products/{id}/stock?qty=X`
- Low stock alert: set Badge = "Limited Stock" when qty < 20
- Out of stock: set stock = 0 (product becomes "Buy Now" disabled)
- Reorder from supplier when qty < 30 units (for fast-moving items)

### Coupon Management

**Current active coupons:**
- `NAVGROW10` — 10% off all orders (no minimum)
- `FLAT200` — ₹200 off on orders ≥ ₹2,000 (max 500 uses)
- `RAILWAY15` — 15% off for railway departments

**Create seasonal coupons:** Go to `/admin` → use Swagger UI → POST `/api/coupons`

**Expire a coupon:** Set `active=false` via PATCH `/api/coupons/{id}/toggle`

---

## 10. SECURITY PROCEDURES

### Credentials Management

- **Never** share admin passwords in WhatsApp or email
- Use a password manager (Bitwarden, 1Password)
- Rotate JWT secret quarterly: update `.env` on VPS, restart service
- Rotate Razorpay webhook secret if webhook endpoint changes
- Admin password: must be 12+ chars, mix of upper/lower/number/symbol

### After Each Employee Departure

1. Immediately change admin portal password
2. Remove or downgrade their user account (PATCH user role to USER)
3. Rotate Gmail App Password if they had email access
4. Review Razorpay dashboard for sub-user removal

### Suspicious Activity

**Signs of compromise:**
- Orders from unusual locations with high values
- Multiple failed login attempts (check backend logs)
- Unexpected API calls (check Nginx access logs)

**Immediate actions:**
1. Rotate JWT secret (this invalidates all tokens): update `.env`, restart
2. Check PostgreSQL for unauthorized data changes
3. Review Nginx access logs: `sudo tail -200 /var/log/nginx/access.log`
4. Notify Razorpay if payment data may be compromised

---

## 11. INCIDENT RESPONSE

### Severity Levels

| Level | Example | Response Time | Action |
|-------|---------|--------------|--------|
| P1 — Critical | Site down, payment failing | 30 minutes | All hands, immediate fix |
| P2 — High | Admin panel inaccessible | 2 hours | Fix same day |
| P3 — Medium | NavBot down, slow response | 24 hours | Fix next business day |
| P4 — Low | Minor UI bug | 1 week | Add to backlog |

### P1: Site is Down

```bash
# 1. Check backend
sudo systemctl status navgrow
sudo journalctl -u navgrow -n 50

# 2. Check Nginx
sudo nginx -t
sudo systemctl status nginx

# 3. Check database
sudo systemctl status postgresql
sudo -u postgres psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname='navgrow_db';"

# 4. Restart sequence (in order)
sudo systemctl restart postgresql
sleep 5
sudo systemctl restart navgrow
sleep 5
sudo systemctl restart nginx

# 5. Verify
curl https://api.navgrow.org/api/actuator/health
```

### P1: Payment Gateway Down

1. Check status.razorpay.com
2. If Razorpay issue: add notice to website header (TenderBanner component)
3. Direct customers to bank transfer/NEFT until resolved
4. Process orders manually once restored

### P2: NavBot Not Responding

```bash
# Test chatbot endpoint
curl -X POST https://api.navgrow.org/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hello"}]}'

# Check Anthropic API status
# → console.anthropic.com/status

# Check API key validity (look for 401 in logs)
sudo journalctl -u navgrow -n 50 | grep -i "anthropic\|chat"

# If key expired: update .env ANTHROPIC_API_KEY, restart
sudo nano /opt/navgrow/.env
sudo systemctl restart navgrow
```

### P3: Database Performance Issue

```sql
-- Check slow queries
SELECT pid, query, state, query_start
FROM pg_stat_activity
WHERE datname = 'navgrow_db' AND state != 'idle'
ORDER BY query_start;

-- Check table sizes
SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

---

## 12. DEPLOYMENT & UPDATES

### Frontend Update (Zero-downtime)

```bash
# On local machine:
cd Navgrow_final
npm run build              # creates /dist

# Upload /dist via FTP to Hostinger public_html
# Files replace instantly — SPA routing handled by .htaccess
```

### Backend Update (Minimal downtime ~15 seconds)

```bash
# On local machine:
mvn clean package -DskipTests -Pprod
scp target/navgrow-backend-1.0.0.jar user@vps:/opt/navgrow/navgrow-new.jar

# On VPS:
cd /opt/navgrow
cp navgrow-backend-1.0.0.jar navgrow-backup-$(date +%Y%m%d).jar
cp navgrow-new.jar navgrow-backend-1.0.0.jar
sudo systemctl restart navgrow

# Verify
curl https://api.navgrow.org/api/actuator/health
```

### Database Migration (Automatic)

Flyway runs migrations automatically on startup. To add a new migration:
1. Create `src/main/resources/db/migration/V3__Your_Description.sql`
2. Write SQL (must be idempotent and backwards-compatible)
3. Deploy backend — migration runs on next startup

**Never** modify existing V1 or V2 migration files.

### Rollback Procedure

```bash
# Backend rollback
sudo systemctl stop navgrow
cp /opt/navgrow/navgrow-backup-20260421.jar /opt/navgrow/navgrow-backend-1.0.0.jar
sudo systemctl start navgrow
```

---

## 13. MONITORING & HEALTH CHECKS

### Automated Health Monitoring

Set up a cron job on the VPS:
```bash
# Edit crontab
crontab -e

# Add these lines:
*/5 * * * * curl -sf https://api.navgrow.org/api/actuator/health || \
  echo "NAVGROW API DOWN $(date)" | mail -s "ALERT: Navgrow API Down" admin@navgrow.org

# Daily DB backup
0 2 * * * pg_dump -U navgrow navgrow_db | gzip > /backups/navgrow_$(date +\%Y\%m\%d).sql.gz

# Weekly log rotation check  
0 1 * * 1 sudo journalctl --vacuum-time=30d
```

### Key Metrics to Watch

| Metric | Tool | Alert Threshold |
|--------|------|----------------|
| API response time | Nginx logs | > 3 seconds |
| Error rate | journalctl | > 5% of requests |
| DB connections | pg_stat_activity | > 8 concurrent |
| Disk space | df -h | < 20% free |
| Anthropic usage | console.anthropic.com | > 80% of quota |
| Razorpay failures | Razorpay dashboard | > 2 per day |

### Log Locations

| Log | Command |
|-----|---------|
| Backend app | `sudo journalctl -u navgrow -f` |
| Nginx access | `sudo tail -f /var/log/nginx/access.log` |
| Nginx error | `sudo tail -f /var/log/nginx/error.log` |
| PostgreSQL | `sudo tail -f /var/log/postgresql/postgresql-15-main.log` |

---

## 14. BACKUP & RECOVERY

### Backup Schedule

| Data | Frequency | Retention | Location |
|------|-----------|-----------|----------|
| PostgreSQL database | Daily 2 AM | 30 days | /backups/ on VPS |
| Application .jar | Before each deploy | 5 versions | /opt/navgrow/ |
| Uploaded files | Daily | 30 days | /backups/uploads/ |
| Environment config | On change | Forever | Encrypted password manager |

### Backup Commands

```bash
# Manual DB backup
pg_dump -U navgrow navgrow_db | gzip > /backups/navgrow_manual_$(date +%Y%m%d_%H%M).sql.gz

# Restore from backup
gunzip -c /backups/navgrow_20260421.sql.gz | psql -U navgrow navgrow_db
```

### Disaster Recovery

**Scenario: VPS completely lost**

1. Provision new Ubuntu 22.04 VPS
2. Follow `DEPLOYMENT_GUIDE.md` steps 1–5
3. Restore database from most recent backup
4. Update DNS for api.navgrow.org → new VPS IP
5. Frontend is safe (Hostinger is separate)
6. Estimated RTO: 4 hours

---

## APPENDIX A: Useful API Calls (Postman)

```bash
# Login
curl -X POST https://api.navgrow.org/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@navgrow.org","password":"YOUR_PASSWORD"}'

# Get dashboard
curl https://api.navgrow.org/api/admin/analytics/dashboard \
  -H "Authorization: Bearer {TOKEN}"

# Create product
curl -X POST https://api.navgrow.org/api/products \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"New Product","category":"Railway Tools","price":1500,"stockQty":100}'

# Test NavBot
curl -X POST https://api.navgrow.org/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"What services do you offer?"}]}'
```

## APPENDIX B: Contact Directory

| Role | Name | Contact |
|------|------|---------|
| Main inquiry | — | info@navgrow.org |
| Phone/WhatsApp | — | +91 89270 70972 |
| Tech issues | Developer | [your email] |
| Razorpay issues | Razorpay Support | support.razorpay.com |
| Anthropic (NavBot) | Anthropic Support | support.anthropic.com |
| Hosting (Frontend) | Hostinger Support | hpanel.hostinger.com |

---

*Document Owner: Navgrow Engineering Service Pvt. Ltd.*
*Review Cycle: Quarterly | Next Review: July 2026*
*CIN: U74999WB2022PTC256012*
