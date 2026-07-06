# Progetto Programmazione Mobile (Barcan Vladut, Mosca Matteo, Mandolini Mirco)

## Avviso
Effettuare la registrazione e il login manualmente, NON tramite Google in quanto è necessario registrare lo SHA256 della build su Google Cloud!

## Come impostare le chiavi API

1. Vai su build.gradle.kts (Module :app) e scrivi questo:
``` java
import java.util.Properties
...
android {
...
defaultConfig {
buildConfigField("String", "EV_API_KEY", "\"${localProps["EV_API_KEY"]}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps["SUPABASE_KEY"]}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"]}\"")
  }
}
...
buildFeatures {
        viewBinding = true
        buildConfig= true
    }
```
2. Su local.properties, aggiungi le tue EV_API_KEY, SUPABASE_KEY, SUPABASE_URL
3. Vai su Build->Compile All Sources (o qualcosa del genere idk)
4. Dove ti serve le chiavi, scrivi:
``` kotlin
var api_key= BuildConfig.LA_TUA_CHIAVE
```

## CHIAVI DA INSERIRE SU LOCAL.PROPERTIES AFFINCHE FUNZIONI TUTTO
``` kotlin
MAPS_API_KEY="AIzaSyDAER7tRV4TOxChc4vA48rfkOCHZaXkXso"
SUPABASE_URL=https://ivimwqhiviuawwbctejd.supabase.co
SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml2aW13cWhpdml1YXd3YmN0ZWpkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNDUzNTUsImV4cCI6MjA4OTkyMTM1NX0.9gI0sZB0qIG6bLywe7bTwci4KUKZ_z7CFcE7BQCKhk0
```
