# ISP360 — Proje bağlamı

Türkiye'deki küçük/orta ölçekli internet servis sağlayıcıları (İSS) için abone faturalandırma sistemi.
**Mimari ve yol haritası: [`docs/TEKNIK_MIMARI.md`](docs/TEKNIK_MIMARI.md).** Bir işe başlamadan önce oku;
mimari bir karar değişirse önce o belgeyi güncelle, bir iş bitince §15'teki yol haritasında işaretle.

## Temel kararlar
- **Müşteri sunucusuna kurulan (on-premise), tek firmalı ürün.** Her kurulum tek İSS. tenant_id / RLS yok.
- Gelir: kurulum + yıllık destek + **kesinleşen fatura başına ücret** → lisans ve kullanım sayacı ticari çekirdek.
- Kaynak kod müşteriye verilmez; Docker imajı teslim edilir. Müşteriye özel kod dalı açılmaz.
- Kurulumda bileşen sayısı az tutulur (uygulama + PostgreSQL). Redis, kuyruk sistemi, React yok (şimdilik).

## Teknoloji
Java 21, Spring Boot 3.5, Spring Data JPA + JdbcClient, PostgreSQL 16, Flyway, Spring Security, Thymeleaf.

## Komutlar
```bash
pg_ctlcluster 16 main start                 # bulut ortamında; yerelde: docker compose up -d
mvn test                                    # billing_test veritabanı gerekir
BILLING_ADMIN_PASSWORD=demo1234 mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## Kod kuralları
- Para: `BigDecimal`, veritabanında `NUMERIC(14,2)`, 2 hane HALF_UP. Vergi: ÖİV net üzerinden, KDV (net + ÖİV) üzerinden.
- Tarih hesapları enjekte edilen `Clock` ile (Europe/Istanbul); `LocalDate.now()` doğrudan çağrılmaz.
- İş kuralları servis/domain sınıflarında; API (`*Controller`) ve panel (`web/*PageController`) aynı servisleri kullanır.
- Panel `open-in-view` kapalı: şablonlara entity değil `*View` kayıtları verilir.
- Mükerrer kayıt korumaları veritabanında da UNIQUE kısıtıyla yapılır.
- Şema değişikliği yalnızca yeni Flyway migration ile (`V<n>__açıklama.sql`); eski migration düzenlenmez.
- Kesinleşmiş finansal kayıtlar silinmez/güncellenmez; düzeltme ters kayıtla.
- Kullanıcıya görünen metinler, hata mesajları ve kod yorumları Türkçe.
- Her davranış değişikliğine test: hesaplama için birim testi, akış için `*IntegrationTest` (MockMvc + gerçek PostgreSQL).
