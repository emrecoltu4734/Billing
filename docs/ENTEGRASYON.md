# ISP360 Entegrasyon Rehberi (v1)

Bu belge, İSS'nin kendi sistemlerini (CRM, abone yönetimi, ödeme altyapısı, kendi ekranları) ISP360
faturalandırma çekirdeğine nasıl bağlayacağını anlatır. Buradaki API uçları ve giriş tabloları
**ISP360 ile aranızdaki sözleşmedir**: v1 boyunca geriye uyumsuz değişiklik yapılmaz.

## 1. Genel bakış

ISP360'a iki yoldan veri verilir. İkisi de aynı iş kurallarından geçer.

```
Sizin sistemleriniz
 ├── REST API (/api/v1)  ───────────────►  anlık işlemler, sonuç hemen döner
 └── Giriş tabloları (in_*) ──► Import batch (her dakika) ──►  toplu veri, sonuç satıra yazılır
                                                                   │
                                     ISP360 core tabloları ◄───────┘  (yalnızca ISP360 yazar)
                                               │
                         Fatura günü ──► Faturalandırma batch'i ──► Fatura
```

| Ne zaman hangisi? | |
|---|---|
| Kendi ekranınızdan anlık işlem (abone aç, kısıtla, tahsilat al) ve sonucu kullanıcıya hemen göstermek | **REST API** |
| Gece/gün içi toplu aktarım, mevcut sisteminizden düzenli besleme, ilk veri yükleme | **Giriş tabloları** |
| Ekran geliştirmek istemiyorsanız | ISP360 yönetim paneli (hazır) |

> **Core tablolara (`customer`, `subscription`, `invoice`, `payment` vb.) doğrudan yazmayın ve okumayın.**
> Bu tabloların yapısı sürümler arasında değişebilir. Sözleşme yalnızca bu belgedeki API ve `in_*`
> tablolarıdır. Entegrasyon veritabanı kullanıcınızın yetkileri buna göre verilir (§6).

## 2. Kimlik eşlemesi: `external_ref`

Her abone, abonelik ve ödeme sizin sisteminizdeki kimliğiyle (`external_ref`, en fazla 64 karakter)
gönderilir. ISP360 kendi kimliğini üretir ve eşlemeyi saklar. Sonraki tüm işlemlerde kendi kimliğinizi
kullanırsınız, ISP360 kimliklerini bilmeniz gerekmez.

| Kayıt | Sizin kimliğiniz | Örnek |
|---|---|---|
| Abone | `in_customer.external_ref` | `CRM-10045` |
| Abonelik / hizmet | `in_subscription_event.external_ref` | `SRV-88231` |
| Ödeme işlemi | `in_payment.external_ref` | ödeme kuruluşunun işlem no'su |

## 3. Giriş tabloları

### 3.1 Ortak kurallar

- Yalnızca `INSERT` yaparsınız. `status` alanını göndermeyin, varsayılanı `NEW` olur.
- Satırlar **eklenme sırasıyla** işlenir. Tablolar şu sırayla işlenir: abone → abonelik olayı → tahsilat.
  Aynı aktarımda önce aboneyi, sonra aboneliğini gönderebilirsiniz.
- Her satır bağımsızdır. Hatalı bir satır diğerlerini durdurmaz ve core'da iz bırakmaz.
- İşlenen satırlar silinmez. Sonuç aynı satıra yazılır:

| Kolon | Anlamı |
|---|---|
| `status` | `NEW` (bekliyor) · `DONE` (işlendi) · `ERROR` (hatalı) · `DUPLICATE` (daha önce işlenmiş, yok sayıldı) |
| `error_code` | `VALIDATION` · `NOT_FOUND` · `BUSINESS_RULE` · `INTERNAL` |
| `error_message` | Türkçe açıklama |
| `core_id` | Oluşan veya değişen ISP360 kaydının kimliği |
| `processed_at` | İşlenme zamanı |

- **Hatalı satır nasıl düzeltilir?** Veriyi kaynağında düzeltip **yeni bir satır** gönderin. Eksik olan ISP360
  tarafındaysa (ör. tarife tanımlı değil), eksik tamamlandıktan sonra panelden "Hatalıları yeniden dene"ye
  basılabilir veya `POST /api/v1/imports/{tablo}/retry` çağrılabilir.
- Aktarım varsayılan olarak her dakika çalışır. Hemen çalıştırmak için `POST /api/v1/imports/run`.
- Sonuçları izlemek için kendi satırlarınızı `status <> 'NEW'` koşuluyla okuyabilirsiniz.

### 3.2 `in_customer`: abone ekleme / güncelleme

`external_ref` ilk kez geliyorsa abone oluşturulur. Daha önce geldiyse bilgileri güncellenir (UPSERT).
Her satırda tüm alanlar gönderilir: boş gönderilen alan boşaltılır.

| Kolon | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `external_ref` | varchar(64) | ✔ | Sizin abone kimliğiniz |
| `type` | varchar(20) | ✔ | `INDIVIDUAL` (bireysel) · `CORPORATE` (kurumsal) |
| `name` | varchar(200) | ✔ | Ad soyad / unvan |
| `tax_id` | varchar(11) | ✔ | TCKN (11 hane) veya VKN (10 hane) |
| `tax_office` | varchar(100) | | Vergi dairesi |
| `phone` | varchar(20) | | |
| `email` | varchar(200) | | Geçerli e-posta |
| `address` | varchar(500) | | |
| `bill_cycle_code` | varchar(20) | | Fatura dönemi kodu (ör. `C15`). Yeni abonede boşsa varsayılan dönem; **mevcut abonede boşsa dönemi değişmez.** |

```sql
INSERT INTO in_customer (external_ref, type, name, tax_id, phone, email)
VALUES ('CRM-10045', 'INDIVIDUAL', 'Ayşe Yılmaz', '12345678901', '05321234567', 'ayse@example.com');
```

### 3.3 `in_subscription_event`: abonelik yaşam döngüsü

| Kolon | Tip | Açıklama |
|---|---|---|
| `external_ref` | varchar(64) | Sizin abonelik/hizmet kimliğiniz (zorunlu) |
| `operation` | varchar(20) | `ACTIVATE` · `SUSPEND` · `RESUME` · `TERMINATE` (zorunlu) |
| `customer_external_ref` | varchar(64) | `ACTIVATE` için zorunlu |
| `plan_code` | varchar(50) | `ACTIVATE` için zorunlu: ISP360'ta tanımlı tarife kodu |
| `effective_date` | date | `ACTIVATE`: hizmet başlangıcı · `TERMINATE`: **son hizmet günü (dahil)** |

| İşlem | Kural |
|---|---|
| `ACTIVATE` | Aynı `external_ref` ile ikinci kez gönderilemez (`BUSINESS_RULE`). Tarife satışa açık olmalı. |
| `SUSPEND` | Yalnızca aktif abonelik kısıtlanır. Kısıtlı abonelik **faturalanmaya devam eder.** |
| `RESUME` | Yalnızca kısıtlı abonelik açılır. |
| `TERMINATE` | Son hizmet gününe kadar kıst olarak faturalanır. |

```sql
INSERT INTO in_subscription_event (external_ref, operation, customer_external_ref, plan_code, effective_date)
VALUES ('SRV-88231', 'ACTIVATE', 'CRM-10045', 'FIBER100', DATE '2026-11-16');

INSERT INTO in_subscription_event (external_ref, operation, effective_date)
VALUES ('SRV-88231', 'TERMINATE', DATE '2027-03-31');
```

> Tarife değişikliği (`CHANGE_PLAN`) bir sonraki sürümde eklenecek. O zamana kadar eski aboneliği
> sonlandırıp yenisini açabilirsiniz.

### 3.4 `in_payment`: tahsilat

| Kolon | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `external_ref` | varchar(64) | ✔ | Ödeme işlem kimliği. **Aynı kimlik tekrar gelirse `DUPLICATE` olur, ikinci kez kaydedilmez.** Ödeme kuruluşu bildirimlerini güvenle tekrar gönderebilirsiniz. |
| `invoice_no` | varchar(16) | ✔ | Ödenen faturanın numarası |
| `customer_external_ref` | varchar(64) | | Ayrıldı: faturaya bağlı olmayan (cari hesaba) ödeme için, sonraki sürüm |
| `amount` | numeric(14,2) | ✔ | Sıfırdan büyük, en fazla faturanın kalan tutarı |
| `method` | varchar(20) | ✔ | `CASH` · `BANK_TRANSFER` · `CREDIT_CARD` · `ONLINE` |
| `paid_at` | timestamptz | | Ödeme zamanı; boşsa işlenme zamanı |
| `reference` | varchar(100) | | Dekont no vb. |

```sql
INSERT INTO in_payment (external_ref, invoice_no, amount, method, paid_at)
VALUES ('IYZ-552190331', 'ISP2026000000123', 396.00, 'ONLINE', TIMESTAMPTZ '2026-12-05 14:21:00+03');
```

## 4. REST API

- Adres: `https://<isp360-sunucusu>/api/v1/...`
- Kimlik doğrulama: HTTP Basic (ISP360 kullanıcısı). Entegrasyon için ayrı bir kullanıcı açılması önerilir.
- Canlı doküman: `/swagger-ui.html`. Makine okunur OpenAPI tanımı: `/v3/api-docs`.
- Hatalar RFC 7807 biçiminde döner: `400` doğrulama, `404` bulunamadı, `409` iş kuralı.

| Method | Uç | İşlem |
|---|---|---|
| POST · GET | `/api/v1/customers` | Abone oluştur (isteğe bağlı `billCycleCode`) · listele/ara (`?q=`) |
| GET | `/api/v1/customers/{id}` | Abone detayı (`billCycleCode` dahil) |
| PUT | `/api/v1/customers/{id}/bill-cycle` | Aboneyi başka fatura dönemine taşı: `{"billCycleCode":"C15"}` |
| POST · GET | `/api/v1/bill-cycles` | Fatura dönemi oluştur: `{"code":"C15","name":"Ayın 15'i","cycleDay":15}` · listele |
| POST · GET | `/api/v1/plans` | Tarife oluştur · listele |
| POST | `/api/v1/subscriptions` | Abonelik başlat |
| GET | `/api/v1/subscriptions?customerId=` | Abonenin abonelikleri |
| POST | `/api/v1/subscriptions/{id}/suspend` · `/resume` · `/terminate` | Durum geçişleri |
| POST · GET | `/api/v1/bill-runs` | Dönemi faturala: `{"cycleCode":"C15","period":"2026-11"}` (`cycleCode` boşsa varsayılan) · geçmiş |
| GET | `/api/v1/bill-runs/{id}` | Çalıştırma ayrıntısı ve faturalanamayan aboneler |
| GET | `/api/v1/invoices?customerId=` · `/api/v1/invoices/{id}` | Faturalar |
| GET | `/api/v1/invoices/overdue` | Vadesi geçmiş faturalar |
| POST | `/api/v1/payments` | Tahsilat |
| POST | `/api/v1/imports/run` | Giriş tablolarını hemen işle |
| GET | `/api/v1/imports/status` | Tablo bazında bekleyen/işlenen/hatalı sayıları |
| POST | `/api/v1/imports/{customers\|subscription-events\|payments}/retry` | Hatalı satırları yeniden kuyruğa al |

```bash
curl -u entegrasyon:şifre -H 'Content-Type: application/json' \
     -d '{"type":"INDIVIDUAL","name":"Ayşe Yılmaz","taxId":"12345678901"}' \
     https://isp360.ornek.local/api/v1/customers
```

## 5. Fatura dönemleri (bill cycle)

- Her abone bir fatura dönemine bağlıdır; günü 1–28 arasıdır. `cycle_day = D` olan dönemde `"2026-11"`
  dönemi **D Kasım – (D-1) Aralık** aralığını kapsar ve **D Aralık'ta** faturalanır. D = 1 ise takvim ayıdır.
- Dönem bitmeden faturalanamaz (`409`).
- Bir dönem tamamen faturalandıktan sonra tekrar çalıştırılamaz (`409`). Bazı aboneler faturalanamadıysa
  çalıştırma `COMPLETED_WITH_ERRORS` durumunda biter; **aynı dönem tekrar gönderildiğinde yalnızca kalan
  aboneler faturalanır.**
- Çalıştırma durumları: `RUNNING` · `COMPLETED` · `COMPLETED_WITH_ERRORS`.
- Abone başka bir döneme taşındığında daha önce faturalanan günler tekrar faturalanmaz; yeni dönemin ilk
  faturası kaldığı günden başlar (kıst).
- Otomatik faturalama dönem bazında açılır; açık olan dönemler fatura gününde gece faturalanır.

## 6. Veritabanı kullanıcısı (giriş tabloları için)

Kurulumda giriş tablolarına erişecek ayrı bir veritabanı kullanıcısı açılır. Bu kullanıcı core
tablolara erişemez (doğrulandı: core tabloya erişimde `permission denied` alır):

```sql
CREATE ROLE isp360_entegrasyon LOGIN PASSWORD '...';
GRANT USAGE ON SCHEMA public TO isp360_entegrasyon;
GRANT SELECT, INSERT ON in_customer, in_subscription_event, in_payment TO isp360_entegrasyon;
GRANT USAGE ON SEQUENCE in_customer_id_seq, in_subscription_event_id_seq, in_payment_id_seq TO isp360_entegrasyon;
```

## 7. Sözleşme ve sürüm kuralları

ISP360 v1 boyunca şunları **yapmaz**:

- API ucunu, giriş tablosunu veya kolonu kaldırmak ya da adını değiştirmek
- Bir alanın anlamını veya biçimini değiştirmek
- İsteğe bağlı bir alanı zorunlu yapmak

ISP360 v1 içinde şunları **yapabilir**, entegrasyonunuz bunlara hazır olmalıdır:

- Yeni uç, yeni giriş tablosu, yeni **boş bırakılabilir** kolon eklemek
- Yanıtlara yeni alan eklemek (bilinmeyen alanları yok sayın)
- Yeni `operation`, `error_code`, `method` veya durum (`status`) değeri eklemek

Geriye uyumsuz bir değişiklik gerekirse `/api/v2` açılır ve v1 en az 12 ay birlikte çalışır.
