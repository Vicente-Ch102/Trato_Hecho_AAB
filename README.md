# Trato Hecho — Android integrado con Spring Boot

Proyecto Android nativo (Kotlin, Jetpack Compose, MVVM, Room, DataStore, Retrofit) conectado a los cuatro microservicios existentes de Trato Hecho. Se entrega **código fuente** completo; requiere Android Studio para generar el APK.

## 1. Requisitos
- Android Studio con Android SDK **API 37** y JDK **21** (versiones indicadas por el proyecto de origen).
- Backend iniciado con Docker Compose y los servicios disponibles en el PC:
  - Identity: `http://localhost:8081`
  - Customers: `http://localhost:8082`
  - Commercial: `http://localhost:8083`
  - Billing: `http://localhost:8084`
- **No hay contraseñas de demostración en Android**: usar las cuentas reales creadas en Identity Service. Nunca incluirlas en el código.

## 2. Conexión al backend
- **Emulador Android:** funciona con el valor predeterminado `10.0.2.2`, que se redirige al localhost del PC.
- **Teléfono físico:** el dispositivo y el PC deben estar en la misma red; cambia la IP en Gradle usando `-PtratoHechoBackendHost=192.168.1.50` (reemplaza por la IP del PC).
- El **AndroidManifest de depuración** habilita HTTP sin TLS solo para pruebas locales. **No uses este HTTP en producción**; para publicar debe habilitarse TLS/HTTPS, configurar host y quitar dependencias de red local.
- El firewall de Windows debe permitir la conexión a 8081–8084 desde el dispositivo.
- En Android Studio: abrir carpeta `TratoHecho`, sincronizar Gradle y ejecutar configuración `app`.

Para verificar conectividad desde el PC: `http://localhost:8081/actuator/health`, y lo mismo para 8082, 8083 y 8084.

## 3. Compilar y ejecutar pruebas
En Windows PowerShell, desde esta carpeta:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest  # requiere emulador/dispositivo
```

Para un teléfono físico:

```powershell
.\gradlew.bat :app:assembleDebug -PtratoHechoBackendHost=192.168.1.50
```

Los tests unitarios utilizan JUnit, MockWebServer y Kotlin coroutines. Los instrumentados utilizan Room en memoria y AndroidJUnit4.

## 4. Qué está conectado
| Puerto | Operaciones Android |
|---|---|
| 8081 | login JWT, refresco de sesión, cierre de sesión, listado/creación/actualización de usuarios (ADMIN) |
| 8082 | consultar, crear, actualizar y desactivar clientes, sincronización y reintentos |
| 8083 | cotizaciones, ítems, cambio de estado, seguimientos, impuestos y catálogo de productos/servicios |
| 8084 | facturas con folio del servidor, vencimiento, notas y transiciones de estado |

- Room conserva una copia local para las pantallas y permite redactar clientes y cotizaciones sin conexión. Los registros se muestran como **PENDIENTE**, **SINCRONIZANDO**, **ERROR** o **SINCRONIZADO** según las respuestas reales.
- Al entrar en sesión y recuperar conectividad se ejecuta la sincronización ordenada: impuestos → catálogo → clientes → cotizaciones → facturas.
- `remoteId` (UUID) relaciona el objeto del backend con el ID `Long` existente en Room. La migración **3 → 4** añade columnas sin borrar datos.
- Los JWT y refresh tokens se guardan cifrados con **Android Keystore (AES-GCM)**. Las claves no están en DataStore ni hardcodeadas. Los respaldos de Android se deshabilitan para la app.
- Los roles de Identity son `ADMIN`, `VENDEDOR` y `FACTURACION`. Android mapea `VENDEDOR` a su perfil local `USUARIO`.

## 5. Limitaciones fieles al contrato actual
- **Supervisor**: Identity Service no define rol `SUPERVISOR` ni relaciones supervisor-vendedor. La gestión de supervisores del prototipo es un concepto local heredado y no equivale a una autorización del backend.
- **Órdenes de compra** y **tipos de unidad**: no hay endpoints en los controladores entregados. Esas funciones siguen guardándose solo en Room. La UI lo indica.
- **Responsable/propietario de cotización**: Commercial Service no devuelve el dueño en `QuoteResponse`; los registros descargados sin dueño conocido se consideran visibles según el listado del servidor y NO se pueden filtrar por vendedor con garantía. La autorización real la hace Spring Security.
- **Descuentos y unidades de ítems**: QuoteRequest no ofrece campos explícitos. El descuento se aplica al precio unitario y la unidad se incluye en la descripción; los importes son calculados por el servidor. No se realiza conversión cambiaria en el backend; USD/UF se tratan como monedas de la cotización.
- **Facturas**: Billing no implementa `Idempotency-Key`. Para reducir duplicados, Android consulta facturas antes de crear otra por la misma cotización, pero esa protección no sustituye una regla de unicidad en el servidor.
- **Estados de factura**: BORRADOR → EMITIDA/ANULADA; EMITIDA → PAGADA/ANULADA; PAGADA/ANULADA finales. Solo se editan facturas BORRADOR.
- **Modo sin conexión**: clientes y borradores de cotizaciones se almacenan localmente; crear facturas, editar usuarios/impuestos/catálogo y registrar seguimientos requieren conexión. Una sesión previamente autenticada permite consultar Room sin conexión si los tokens aún están disponibles localmente.
- **Datos previos**: los registros demo del tenant numérico 1/2 se preservan para no destruir datos antiguos, pero la sesión nueva utiliza una clave local derivada del tenant UUID de Identity.

## 6. Puntos de entrada
- `MainActivity.kt`: composición de los repositorios y cliente API.
- `data/remote/`: contratos Retrofit, modelos JSON, intérprete de estados y encabezados JWT.
- `data/security/TokenVault.kt`: almacenamiento cifrado de tokens.
- `data/repository/SyncCoordinator.kt`: sincronización ordenada.
- `data/database/AppDatabase.kt`: migración Room 3→4.
- `app/src/test/`: pruebas unitarias y MockWebServer.
- `app/src/androidTest/`: aislamiento multi-tenant y persistencia Room.

Consulta [`INTEGRACION_BACKEND.md`](INTEGRACION_BACKEND.md) para el inventario completo de endpoints, una matriz de pruebas y pasos de diagnóstico.
