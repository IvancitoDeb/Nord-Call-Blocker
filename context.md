# context.md — Nord Call Blocker

**Última actualización:** 2026-10-30  
**Destino:** continuar el desarrollo desde Visual Studio Code (VS Code)  
**Estado:** versión 1.6 con verificación explícita de contactos, interfaz renovada, logo personalizado e insets para barras del sistema. Corrige un cierre de 1.5 en OxygenOS al acceder al controlador de insets antes de crear `DecorView`; pendiente de validación física.  
**Dispositivo:** OnePlus Nord 3 · Android 16 · OxygenOS.  
**Proyecto base:** `NordCallBlocker_Android16.zip` (carpeta raíz `NordCallBlocker/`).

> **Instrucción para futuras sesiones/IA:** primero inspecciona los archivos reales del repositorio abierto en VS Code y compara con esta descripción. Este documento describe el ZIP inicial y las pruebas comunicadas por el usuario; **no presupone** que sus archivos locales sigan idénticos. No marques soluciones como validadas sin pruebas. Conserva el comportamiento ya comprobado y evita permisos innecesarios.

## 1. Objetivo del producto

Aplicación Android personal que rechaza automáticamente llamadas telefónicas convencionales de números **no guardados en contactos**, permite las de **contactos guardados** y mantiene un **historial local de bloqueos**. No requiere root, Internet ni reemplazar la aplicación telefónica. La versión 1.2 requiere `READ_CONTACTS` para realizar la distinción localmente. No filtra llamadas VoIP de WhatsApp/Telegram.

## 2. Evidencia real de pruebas (confirmada por el usuario)

| Prueba | Resultado observado | Conclusión |
|---|---|---|
| Instalar/ejecutar en OnePlus Nord 3, Android 16 | App probada en dispositivo | Compilación e instalación logradas por el usuario; no hay detalles de herramientas ni logs |
| Llamadas desde números no guardados, con el sistema desbloqueado | **2 rechazadas** | Bloqueo principal funciona en esos casos |
| Llamada de contacto guardado | **1 recibida normalmente** | No hubo falso positivo en esa prueba |
| Historial | **2 registros**, correspondientes a las llamadas bloqueadas | Registro funcional en esas pruebas |
| Reiniciar y recibir llamada **antes del primer desbloqueo** | **1 llamada entró** | Bloqueo no fue efectivo en ese estado |
| Desbloquear, abrir la app y repetir llamadas | Volvieron a bloquearse | Recuperación observada después de ambos pasos, sin poder aislar cuál fue determinante |
| Versión 1.1 con Direct Boot, contactos y desconocidos | **Todos fueron bloqueados antes y después del primer desbloqueo** | El dispositivo entrega también llamadas de contactos al servicio; bloquear cada callback no es seguro |

**Todavía sin probar/confirmar:** que baste con *desbloquear sin abrir* la app; si el servicio recibe `onScreenCall` en Direct Boot; si se mantiene seleccionado el rol de filtrado tras reiniciar; funcionalidad con pantalla apagada después de haber desbloqueado una vez; estado del interruptor justo tras el arranque; bloqueos anónimos; comportamiento de ON/OFF y persistencia después de otros reinicios. No inventar recuentos adicionales.

## 3. Diagnóstico de prioridad máxima: fallo antes del primer desbloqueo

La llamada que entró ocurrió **antes del primer desbloqueo después de reiniciar**. La hipótesis principal es la fase Android **Direct Boot**: el almacenamiento protegido por credenciales (*Credential Encrypted / CE*) aún no está disponible, y el servicio podría no poder ejecutarse o leer su configuración. **No está demostrada la causa.** Otras posibilidades son cómo OxygenOS/Telecom enlaza el servicio, la disponibilidad del rol de screening y la entrega/identificación de llamadas durante esa fase.

**Riesgo de falso positivo:** no se debe forzar el rechazo de toda llamada antes del desbloqueo sin asegurar que Android sigue diferenciando contactos guardados; podría bloquear familiares, servicios urgentes u otras llamadas legítimas.

### Corrección de seguridad implementada en 1.2 (todavía NO validada en dispositivo)

1. Se declara y solicita `READ_CONTACTS`. Después del desbloqueo, cada número se consulta localmente mediante `ContactsContract.PhoneLookup`.
2. Solo se bloquea si el interruptor está activo, el permiso está concedido y la consulta confirma `NOT_SAVED`.
3. Durante Direct Boot se permiten todas las llamadas. También se permite la llamada si falta el permiso o el número, si el proveedor devuelve un resultado incierto o si la consulta excede dos segundos.
4. `enabled` sigue en DE y el historial con números en CE. Los diagnósticos registran condiciones y motivo de decisión, pero nunca el número.
5. `directBootAware` se conserva para observar callbacks, no para bloquear antes del desbloqueo.
5. Verificar si `onScreenCall` se ejecuta **antes** del primer desbloqueo. Si no se ejecuta, no puede resolverse únicamente leyendo preferencias desde DE; investigar el rol/Telecom/OxygenOS y sus limitaciones. No implementar un servicio permanente ni un `BOOT_COMPLETED` receiver sin evidencia de necesidad.
6. Repetir pruebas con un número guardado y uno no guardado **antes del primer desbloqueo**, y después del primero, con registros de Logcat y estado del rol. Si no hay filtrado posible antes del desbloqueo, documentar la limitación con precisión.

## 4. Arquitectura del ZIP original (inspeccionada)

| Archivo | Contenido |
|---|---|
| `app/src/main/java/com/example/nordcallblocker/BlockerService.kt` | `CallScreeningService`; para llamadas entrantes consulta `BlockerStore.enabled()`, genera `CallResponse` con `setDisallowCall(block)` y `setRejectCall(block)`, responde rápidamente, y en caso de bloqueo abre un `Thread` para guardar el evento |
| `app/src/main/java/com/example/nordcallblocker/BlockerStore.kt` | `SharedPreferences` llamado `blocker_settings`, clave `enabled` (por defecto `true`), `blocked_history` como JSON (número, timestamp) limitado a 200 entradas |
| `app/src/main/java/com/example/nordcallblocker/ContactLookup.kt` | Consulta `PhoneLookup` con timeout de dos segundos y resultado seguro `UNKNOWN` ante errores |
| `app/src/main/java/com/example/nordcallblocker/MainActivity.kt` | UI Android nativa con tarjetas; un solo botón encadena `ROLE_CALL_SCREENING` y `READ_CONTACTS`, además del interruptor e historial |
| `app/src/main/AndroidManifest.xml` | Activity, servicio con `BIND_SCREENING_SERVICE`, `exported=true`, intent `android.telecom.CallScreeningService`; **no tiene** `directBootAware` en el ZIP |
| `app/build.gradle.kts` | `compileSdk=37`, `targetSdk=36`, `minSdk=29`, Java/Kotlin 17, `versionCode=7`, `versionName=1.6` |
| `README.md` | Instrucciones originales |
| `gradle/wrapper/gradle-wrapper.properties` | Configuración de distribución de Gradle |

**Identificador:** `com.example.nordcallblocker`. La copia actual incluye Gradle Wrapper 9.6.0 y la versión 1.6 compila mediante `./gradlew assembleDebug`. La compilación local no sustituye la prueba física del filtrado ni la revisión visual en distintas escalas de pantalla.

**Decisión de permisos revisada:** la prueba de 1.1 demostró que en este dispositivo no es seguro depender de que Telecom excluya contactos. La versión 1.2 solicita `READ_CONTACTS`, hace una consulta local y falla permitiendo la llamada ante cualquier incertidumbre. No hay uso de Compose ni Room.

## 5. Deuda técnica y riesgos (posteriores al fallo de Direct Boot)

- **Persistencia del historial:** en 1.1 primero se responde a Telecom y después se guarda de forma síncrona con `commit()`, antes de devolver el callback. El historial continúa limitado a 200 entradas.
- **Concurrencia:** en 1.1, lectura, adición y borrado del historial comparten sincronización.
- **Privacidad:** números y marcas temporales son información sensible para el usuario; no transferirlos ni escribirlos íntegros a logs. `allowBackup=false` en el manifiesto original.
- **Interfaz:** la versión 1.3 incorpora jerarquía visual, tarjetas, estados por color, historial rediseñado, icono y flujo unificado de permisos, todavía sin depender de Material 3.
- **Excepciones/whitelist:** todavía no implementadas.
- **Llamadas ocultas/privadas:** pueden no enviarse a este servicio; el bloqueo de números ocultos debe tratarse por separado en ajustes del teléfono/OxygenOS.
- **El rol puede reemplazar** un filtro de spam/identificador de llamadas previamente seleccionado.

## 6. Plan de desarrollo en VS Code

**P0 — Reproducir y aislar**

1. Abrir la carpeta real del proyecto y confirmar manifiesto, `BlockerService`, `BlockerStore` y versión.
2. Con teléfono conectado y depuración USB, comprobar permisos/rol y obtener logs seguros mediante `adb logcat` filtrando por una etiqueta propia, por ejemplo `NordCallBlocker`.
3. Reiniciar, **no desbloquear** y efectuar llamada de prueba. Guardar si se recibió el callback y sus condiciones. Luego desbloquear **sin abrir** la app y probar nuevamente. Finalmente abrir la app y probar una tercera vez. Estas tres fases diferencian Direct Boot de inicialización por Activity.
4. Probar un contacto guardado en la misma fase para detectar falsos positivos.

**P1 — Implementar corrección mínima**

1. ~~Agregar `directBootAware` al servicio y persistencia DE para el interruptor con migración segura de datos existentes.~~ Implementado en 1.1; falta validación física.
2. ~~Proteger accesos a CE al no estar desbloqueado; decidir tratamiento de historial antes de desbloquear.~~ Implementado: no se guarda historial durante Direct Boot.
3. Evitar bloqueo indiscriminado si no puede verificarse que las llamadas de contactos están excluidas.
4. Repetir pruebas reales. Si Telecom no enlaza el servicio antes del primer desbloqueo, documentar que la solución depende del sistema y no está garantizada.

**P2 — Robustez y entrega**

1. Endurecer escrituras de historial y sincronizar borrado/añadido.
2. ~~Completar Gradle Wrapper y documentar compilación desde la terminal de VS Code.~~ Completado con Gradle 9.6.0.
3. Incorporar pruebas unitarias/instrumentadas cuando sea práctico y revisar permisos/privacidad.
4. Actualizar README, versión y este `context.md` según resultados reales.

## 7. Criterios de aceptación de la próxima versión

- Mantener las funcionalidades ya validadas: bloquear números no guardados, dejar pasar contactos, registrar rechazos.
- Antes del primer desbloqueo se permitirán todas las llamadas; después, ninguna llamada de contacto guardado debe ser rechazada.
- El interruptor conservará el valor elegido después de reiniciar y de actualizar la aplicación.
- El rol de filtrado y la disponibilidad de `onScreenCall` deben poder diagnosticarse sin registrar información telefónica sensible.
- Historial existente preservado tras migraciones, salvo autorización explícita para borrarlo.
- No afirmar «Direct Boot solucionado» hasta que pruebas reales después del reinicio lo respalden.

## 8. Documentación técnica oficial

- CallScreeningService: https://developer.android.com/reference/android/telecom/CallScreeningService
- Direct Boot: https://developer.android.com/privacy-and-security/direct-boot
- Context.createDeviceProtectedStorageContext: https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()
- RoleManager: https://developer.android.com/reference/android/app/role/RoleManager

## 9. Encargo recomendado para el asistente en VS Code

> Examina el proyecto y `context.md`. Conserva la política segura de 1.2: permitir todas las llamadas durante Direct Boot y bloquear después del desbloqueo únicamente cuando `PhoneLookup` confirme que el número no está guardado. No registres números en Logcat, falla permitiendo llamadas ante incertidumbre y no marques el comportamiento como validado sin pruebas físicas con contacto y desconocido.
