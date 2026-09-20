# Android permanent signing

برای اینکه نسخه جدید Android روی نسخه قبلی نصب شود، دو چیز باید همیشه ثابت بماند:

1. `applicationId`
2. کلید امضای Release

## شناسه دائمی اپ

```
com.hazhanhasani.traditionalcafe
```

این شناسه از اولین نسخه Release نباید تغییر کند.

## Certificate SHA-256

کلید رسمی اولیه پروژه با این اثرانگشت ساخته شده است:

```
4B:B8:12:E9:1C:69:57:E5:5F:34:32:06:A2:DF:C8:F7:F2:8E:CB:94:90:EC:F4:86:FF:4E:F9:D2:75:39:84:E2
```

GitHub Actions قبل از Build این مقدار را بررسی می‌کند. اگر اشتباهاً Keystore دیگری وارد شود، Build متوقف می‌شود تا APK ناسازگار منتشر نشود.

## GitHub Actions Secrets

در Repository Settings > Secrets and variables > Actions چهار Secret زیر را ایجاد کنید:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Alias رسمی:

```
traditionalcafe
```

فایل JKS و رمزهای اصلی هرگز نباید Commit شوند.

## قانون نسخه‌ها

برای هر انتشار:
- `versionCode` باید بزرگ‌تر از نسخه قبلی باشد.
- `versionName` می‌تواند مثل `0.1.0`, `0.2.0`, `1.0.0` باشد.
- APK باید همیشه با همین Keystore رسمی ساخته شود.

در صورت از دست رفتن کلید امضا، امکان نصب آپدیت APK روی نسخه‌ای که قبلاً با این کلید نصب شده وجود نخواهد داشت. بنابراین حداقل دو نسخه Backup آفلاین از فایل JKS نگه دارید.
