# ISP Billing

Küçük internet servis sağlayıcıları (fiber, kablosuz/WISP, site interneti) için abone faturalandırma sistemi.

**Durum:** MVP iskeleti. Abone → tarife → abonelik → aylık fatura dönemi (bill run) → fatura → tahsilat akışı çalışıyor.

## Teknoloji
- Java 21, Spring Boot 3.5
- PostgreSQL 16, Flyway (şema: `src/main/resources/db/migration`)

## Çalıştırma

```bash
docker compose up -d          # PostgreSQL (billing + billing_test veritabanları)
mvn spring-boot:run           # http://localhost:8080
mvn test                      # birim + uçtan uca testler (billing_test veritabanını kullanır)
```

Veritabanı bağlantısı ortam değişkenleriyle değiştirilebilir: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TEST_DB_URL`.

## Faturalandırma kuralları

| Kural | Açıklama |
|---|---|
| Dönem | Takvim ayı (1'i – ay sonu) |
| Kıst (prorate) | `aylık ücret × aktif gün / dönemdeki gün`. Başlangıç ve bitiş günleri dahil |
| ÖİV | Net tutar üzerinden |
| KDV | `(net + ÖİV) × KDV oranı`: ÖİV, KDV matrahına dahil |
| Yuvarlama | Her adımda 2 hane, HALF_UP |
| Fatura | Abone başına dönemde tek fatura, her abonelik bir satır |
| Tekrar koruması | Aynı dönem iki kez çalıştırılamaz (`bill_run.period_start` UNIQUE), bir abonelik aynı dönemde iki kez faturalanamaz (`invoice_line` UNIQUE) |
| Askıya alınmış abonelik | Faturalanmaya devam eder (borçtan kısıtlama hizmeti durdurur, sözleşmeyi değil) |
| Sonlandırılmış abonelik | `end_date`'e kadar kıst olarak faturalanır |
| Fatura no | e-Arşiv biçimi: `ÖNEK(3) + YIL(4) + SIRA(9)` → `ISP2026000000001` |

> ⚠️ ÖİV/KDV oranları tarifede tutulur ve kurallar canlıya çıkmadan önce bir mali müşavirle doğrulanmalıdır.

## API

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/api/customers` | Abone oluştur |
| GET | `/api/customers`, `/api/customers/{id}` | Abone listesi / detayı |
| POST | `/api/plans` | Tarife oluştur |
| GET | `/api/plans`, `/api/plans/{id}` | Tarife listesi / detayı |
| POST | `/api/subscriptions` | Abonelik başlat |
| GET | `/api/subscriptions?customerId=` | Abonenin abonelikleri |
| POST | `/api/subscriptions/{id}/suspend` · `/resume` · `/terminate` | Kısıtla / aç / sonlandır |
| POST | `/api/bill-runs` | Dönemi faturala: `{"period":"2026-11"}` |
| GET | `/api/bill-runs` | Çalıştırma geçmişi |
| GET | `/api/invoices?customerId=`, `/api/invoices/{id}` | Faturalar / fatura detayı (satırlarla) |
| GET | `/api/invoices/overdue` | Borçlu listesi (vadesi geçmiş, ödenmemiş) |
| POST | `/api/payments` | Tahsilat: `{"invoiceId":1,"amount":100.00,"method":"ONLINE"}` |

### Örnek akış

```bash
H='Content-Type: application/json'
curl -XPOST localhost:8080/api/customers -H "$H" -d '{"type":"INDIVIDUAL","name":"Ayşe Yılmaz","taxId":"12345678901"}'
curl -XPOST localhost:8080/api/plans -H "$H" -d '{"code":"FIBER100","name":"Fiber 100 Mbps","monthlyFee":300,"oivRate":10,"vatRate":20}'
curl -XPOST localhost:8080/api/subscriptions -H "$H" -d '{"customerId":1,"planId":1,"startDate":"2026-11-10"}'
curl -XPOST localhost:8080/api/bill-runs -H "$H" -d '{"period":"2026-11"}'
curl "localhost:8080/api/invoices?customerId=1"
```

## Yol haritası
1. **Tahsilat:** iyzico / PayTR ödeme linki, kayıtlı karttan otomatik çekim
2. **Hatırlatma:** Vadesi yaklaşan/geçen faturalar için SMS ve e-posta
3. **PDF fatura**
4. **e-Fatura / e-Arşiv:** Özel entegratör API entegrasyonu
5. **RADIUS:** Borçlu aboneyi otomatik kısıtlama, ödeyince açma
6. **Panel:** İSS personeli için web arayüzü, kimlik doğrulama, çoklu firma (multi-tenant)
7. **BTK raporları** ve Excel'den abone aktarımı
