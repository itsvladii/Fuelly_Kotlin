# Progetto TMOB

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
