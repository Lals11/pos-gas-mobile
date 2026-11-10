# POS Gas Mobile (Android)

App en Kotlin y Jetpack Compose para tablet, conectada a Facturación mediante Apps Script.

## Flujo
1. Publica la integración de Facturación como **API Executable** en un entorno de prueba.
2. Habilita Apps Script API en el proyecto estándar de Google Cloud compartido. Registra un cliente OAuth de Android para `com.posgas.mobile` con el SHA-1 de la firma del APK.
3. Configura `gyaApiDeploymentId` con el ID del despliegue ejecutable y `gyaApiScopes` con todos los scopes del proyecto integrado, separados por comas. Se pueden indicar como propiedades de Gradle en el equipo.
4. Compila la app y autoriza una cuenta Google de un operador activo con acceso a los libros de prueba.

## Estética
Se usa tema oscuro con acento naranja `#FF5500`, alineado al estilo actual del sistema web.
