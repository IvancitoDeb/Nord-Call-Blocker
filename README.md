# Nord Call Blocker

Aplicación Kotlin para Android 16 (OnePlus Nord 3), sin root ni Internet. Usa acceso local de solo lectura a contactos para distinguir números guardados de desconocidos.

## Cómo abrir y compilar

1. Instala Android Studio con JDK 17 y Android SDK 36.
2. Descomprime el ZIP y abre la carpeta `NordCallBlocker` mediante **Open**.
3. Sincroniza Gradle. El wrapper de Gradle 9.6.0 está incluido; desde una terminal también puedes ejecutar `./gradlew assembleDebug`.
4. Conecta el OnePlus por USB, habilita depuración USB y pulsa Run; o Build > Build APK(s).
5. Abre la aplicación y pulsa **Configurar protección**. El mismo botón solicita primero el rol de filtrado y después el acceso a contactos.
6. Acepta ambos diálogos. Sin acceso a contactos, la aplicación permite todas las llamadas por seguridad.
7. Activa el interruptor y realiza pruebas con un número guardado y otro que no tengas guardado.

## Cómo funciona

En el OnePlus probado, Telecom/OxygenOS entregó al servicio tanto números desconocidos como contactos. Desde la versión 1.2 se solicita `READ_CONTACTS` y se consulta `ContactsContract.PhoneLookup` localmente. Solo se rechaza cuando el teléfono está desbloqueado, el interruptor está activo, el permiso está concedido y la consulta confirma que el número no está guardado. Cualquier error, ausencia de número o consulta incierta permite la llamada.

El servicio sigue marcado como compatible con Direct Boot para poder diagnosticar su invocación. Sin embargo, los contactos no se consideran disponibles de forma fiable antes del primer desbloqueo, por lo que durante esa fase se permiten todas las llamadas. El interruptor permanece en almacenamiento DE y el historial con números permanece en CE.

## Restricciones

- Android no remite normalmente números ocultos, privados o indisponibles al servicio. Bloquéalos también desde la app Teléfono/OxygenOS.
- El historial de la app es independiente del historial del teléfono. Guarda como máximo 200 registros en almacenamiento privado de la aplicación. No se copia a la nube.
- Algunas llamadas (Wi-Fi, VoIP, fabricantes, operadores) pueden comportarse de forma diferente. Debe probarse físicamente en OnePlus Nord 3 Android 16.
- Android solo admite un servicio seleccionado de filtrado de llamadas a la vez; este puede sustituir la detección de spam previa.
- La app no filtra WhatsApp/Telegram ni otros servicios de voz por Internet.
- La respuesta puede ser aceptada por el sistema aunque el operador proporcione un tono de ocupado o contestador distinto al esperado.
- El estado del interruptor y los registros están en SharedPreferences; para una versión posterior con búsquedas, exportaciones y grandes volúmenes de historial se recomienda Room.
- Antes del primer desbloqueo no se bloquea ninguna llamada: es una limitación de seguridad necesaria para no rechazar contactos legítimos.

## Prueba de Direct Boot

1. Instala la versión 1.6, ábrela con el teléfono desbloqueado, pulsa **Configurar protección**, acepta ambos diálogos y deja activado el interruptor.
2. Ejecuta `adb logcat -c` y después `adb logcat -s NordCallBlocker:I`.
3. Reinicia el teléfono y, sin desbloquear, llama desde un número no guardado y un contacto: ambos deben permitirse.
4. Desbloquea sin abrir la app y repite: el desconocido debe bloquearse y el contacto debe permitirse.
5. Abre la app y repite una tercera vez. Los mensajes indican `userUnlocked`, `roleHeld`, permiso y motivo de decisión, pero nunca incluyen el número.

La consulta de contactos tiene un límite interno de dos segundos para respetar los cinco segundos de Telecom. Un timeout permite la llamada.

## Estructura

- `BlockerService.kt`: decisión y rechazo de llamada.
- `ContactLookup.kt`: consulta acotada y segura del número en contactos.
- `BlockerStore.kt`: interruptor e historial local.
- `MainActivity.kt`: interfaz y solicitud de rol del sistema.
- `AndroidManifest.xml`: servicio protegido mediante BIND_SCREENING_SERVICE.

## Referencias

- https://developer.android.com/reference/android/telecom/CallScreeningService
- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/reference/android/provider/ContactsContract.PhoneLookup
