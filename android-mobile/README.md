# POS Gas Mobile (Android)

App en Kotlin y Jetpack Compose para tablet, conectada a Facturación mediante Apps Script.

Para retomar la integración, consulta el [contrato y los pasos de publicación de la API](https://github.com/Lals11/POS-GYA-API/blob/main/CONTRATO_ANDROID.md). El APK conectado necesita el ID del **Ejecutable de API** que se publique desde el proyecto de Facturación.

## Probar en Android Studio

1. Abre la carpeta `android-mobile` como proyecto y espera la sincronización de Gradle. Si Studio solicita el SDK, instala **Android 14 (API 34)** y usa el JDK integrado de Android Studio.
2. Selecciona la configuración `app`, elige una tablet virtual con Google Play o conecta un dispositivo y pulsa **Run ▶**.
3. Pulsa **Probar diseño** en la pantalla inicial para ver las tarjetas con saldos de ejemplo y probar sus botones sin conectar una cuenta. Esta vista solo está disponible en la compilación de depuración y no guarda movimientos.

## Flujo
1. Publica la integración de Facturación como **API Executable** en un entorno de prueba.
2. Habilita Apps Script API en el proyecto estándar de Google Cloud compartido. Registra un cliente OAuth de Android para `com.posgas.mobile` con el SHA-1 de la firma del APK.
3. Configura `gyaApiDeploymentId` con el ID del despliegue ejecutable y `gyaApiScopes` con todos los scopes del proyecto integrado, separados por comas. En Windows, puedes poner esas propiedades en `%USERPROFILE%\.gradle\gradle.properties`. Si compilas en otro equipo, usa `gradlew.bat :app:signingReport` y registra la huella SHA-1 de `debug` de ese equipo si es distinta.
4. Compila la app y autoriza una cuenta Google de un operador activo con acceso a los libros de prueba.

## Estética
Se usa tema oscuro con acento naranja `#FF5500`, alineado al estilo actual del sistema web.
