# TraditionalCafe

اپ مدیریت و حسابداری کافه سنتی.

## فاز فعلی
- اسکلت اولیه Android
- شناسه دائمی اپ: `com.hazhanhasani.traditionalcafe`
- امضای Release ثابت و قابل اعتبارسنجی
- GitHub Actions برای ساخت APK امضاشده
- Cloudflare Worker پایه و مسیر Health Check
- داشبورد اولیه RTL برای شروع توسعه میزها، قلیان، حساب دفتری و هزینه‌ها

## معماری هدف
- Android/PWA Client
- Cloudflare Worker API
- Cloudflare D1
- احراز هویت چندکاربره و سطح دسترسی
- مدیریت میز و تسویه چندروشی
- حساب دفتری مشتریان
- گزارش سود و زیان

## Cloudflare
فایل `wrangler.toml` در ریشه پروژه قرار دارد، بنابراین دستور فعلی Cloudflare یعنی:

```bash
npx wrangler deploy
```

اکنون Worker مشخصی برای Deploy دارد و دیگر نباید خطای «Could not detect a directory containing static files» رخ دهد.

Health endpoint:

```
GET /api/health
```

## Android Signing
کلید خصوصی امضا عمداً داخل GitHub قرار نمی‌گیرد. تنظیمات کامل در `docs/SIGNING.md` است.
