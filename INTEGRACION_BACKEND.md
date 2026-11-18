# Integración backend / Android — ficha de implementación

**Fuentes:** código fuente del Android original y de `TratoHecho_Backend_Fix02.zip` (contratos Kotlin/Spring Boot). No se inventaron rutas para órdenes de compra, supervisores ni unidades.

## Endpoints Retrofit

| Microservicio | Operaciones incluidas |
|---|---|
| Identity 8081 | `POST /api/v1/auth/login`, `POST /refresh`, `POST /logout`, `GET /me`, `GET/POST/PUT/DELETE /api/v1/usuarios` |
| Customers 8082 | `GET/POST /api/v1/clientes`, `GET/PUT/DELETE /api/v1/clientes/{id}` |
| Commercial 8083 | `GET/POST /api/v1/cotizaciones`, `GET/PUT/DELETE /api/v1/cotizaciones/{id}`, `PATCH /{id}/estado`, `GET/POST /{id}/seguimientos`, `GET/POST/PUT/DELETE /api/v1/catalogos`, `GET/POST/PUT/DELETE /api/v1/impuestos` |
| Billing 8084 | `GET/POST /api/v1/facturas`, `GET/PUT/DELETE /api/v1/facturas/{id}`, `PATCH /{id}/estado` |

La dirección predeterminada de los servicios en **Android** es `http://10.0.2.2:<puerto>/`, no `localhost`. Se personaliza la IP con `-PtratoHechoBackendHost=...` y se recompila. El TLS de producción debe configurarse por separado.

## Flujo de datos
1. Login llama a Identity, no existe contraseña fija de prueba. El JWT trae `tenantId` firmado por el backend; Android solo lo lee para identificar su caché local (NO autoriza operaciones basándose en él).
2. El access token se envía en `Authorization: Bearer ...`; ante HTTP 401, `RefreshAuthenticator` intenta `/auth/refresh` con un cliente independiente y reintenta **una vez** la petición original. Si el refresh devuelve error de autenticación, se eliminan los tokens locales.
3. DataStore guarda el perfil y la referencia local del tenant; Android Keystore cifra access/refresh tokens en `auth_secure`.
4. Al iniciar sesión o reconectar, `SyncCoordinator` realiza descargas/subidas. Un error de red deja el registro con estado `ERROR` sin atribuir una sincronización que no ocurrió. Clientes y cotizaciones incluyen `Idempotency-Key` estable para los POST.
5. Room usa `Long` como clave primaria de UI y `remoteId: String?` para enlazar UUID del servidor. La migración 3→4 preserva la base del proyecto original.
6. Billing solo permite facturar cotizaciones `ACEPTADA` con UUID remoto y crea el folio en Spring Boot. El botón de emisión/pago respeta `InvoiceStatusRules`.

## Matriz de pruebas incorporada

| Archivo | Cobertura |
|---|---|
| `InvoiceTransitionsTest.kt` | Transiciones admitidas y estados terminales |
| `QuoteMappingTest.kt` | Impuesto, descuento, catálogo, unidad, vigencia y estado de cotización |
| `JwtAndIdempotencyTest.kt` | Extracción de tenant y claves idempotentes por tenant/recurso |
| `BackendHttpContractTest.kt` | Rutas, JSON, `Authorization`, idempotencia, refresh JWT 401 con MockWebServer |
| `RoomTenantIsolationTest.kt` (instrumentada) | Aislamiento por tenant, UUID remoto y persistencia en Room |

Comandos en PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Verificación en entorno de entrega

- **Realizado:** comprobación de la sintaxis y lógica del núcleo Kotlin con `kotlinc` y un programa de smoke-tests (estados, precio con descuento, impuestos, catálogo, vencimiento, idempotencia), resultado `SMOKE_TESTS_OK`.
- **No realizado aquí:** compilación APK, pruebas JUnit/MockWebServer de Gradle, ejecución instrumentada y pruebas HTTP contra Docker. Este entorno no tiene Android SDK ni acceso a los servicios de Docker del usuario; deben ejecutarse en Android Studio/Windows.
- **No incluido:** APK resultante, contraseña ni configuración personalizada del backend. El ZIP contiene fuente, recursos, Gradle wrapper, pruebas y documentación.

## Checklist de validación manual

1. Abrir `TratoHecho` en Android Studio. Comprobar que Docker responde en los puertos 8081–8084.
2. Con el emulador, iniciar sesión con un usuario real de Identity. Verificar rol correcto. Reiniciar app y verificar restauración de sesión.
3. Crear un cliente con RUT, observar `SINCRONIZADO` y comprobar que aparece en Customers Service.
4. Crear cotización con ítem, impuesto y cliente sincronizado. Verificar UUID y total en Commercial Service.
5. Registrar un seguimiento y pasar de `BORRADOR` a `ENVIADA`, luego `ACEPTADA` si procede; comprobar en backend.
6. Desde un ADMIN o FACTURACION, generar factura, emitirla y marcarla `PAGADA`. Consultar de nuevo tras reiniciar.
7. Desconectar red, crear un cliente o borrador de cotización, comprobar `PENDIENTE`; reconectar y sincronizar; comprobar `SINCRONIZADO`.
8. Verificar acceso con otra empresa/tenant y confirmar que no se mezclan datos.
9. Validar errores 400/401/403/409 y comprobar que las pantallas no muestren éxito remoto cuando falla la operación.
10. Ejecutar `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` y guardar sus resultados.

## Observaciones técnicas importantes

- La gestión de supervisores, órdenes de compra y tipos de unidad debe recibir endpoints adicionales para soportar sincronización real.
- La API de Commercial Service no incluye `createdBy`/`ownerEmail` en `QuoteResponse`; el filtrado de cotizaciones por propietario solo es fiable para borradores creados localmente. El servidor debe hacer cumplir las políticas de acceso.
- El backend no tiene columnas explícitas para descuentos, unidades ni tasa cambiaria en `QuoteRequest`. Android transforma el descuento al precio y guarda la unidad como descripción; se preserva la representación más cercana compatible con el contrato.
- `InvoiceService` puede crear varias facturas por la misma cotización si se lo pide; Android verifica si existe una factura no anulada para evitar reintentos duplicados en uso normal, pero la solución fuerte requiere unicidad/idempotencia del lado del servidor.
- Tras una actualización Android desde la app demo, no se borra Room; el tenant real se identifica por UUID y los registros de demo pueden quedar como datos locales históricos invisibles para la nueva sesión real.
