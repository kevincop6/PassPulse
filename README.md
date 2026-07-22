# PassPulse

PassPulse es una aplicación nativa para Android que genera, copia y protege contraseñas localmente. Está diseñada para Android 12 o superior, con una interfaz basada en Material Design 3 y enfoque en privacidad.

## Características

- Generación criptográficamente segura mediante `SecureRandom`.
- Longitud configurable entre 8 y 32 caracteres.
- Configuración independiente de:
  - Minúsculas.
  - Mayúsculas.
  - Números.
  - Símbolos.
- Distribución uniforme inicial de caracteres según la longitud seleccionada.
- Regeneración automática cada 30 segundos.
- Copiado al tocar la contraseña, el texto o el icono de copiar.
- Solo se guardan las contraseñas que el usuario copia.
- Vista previa de las últimas 5 contraseñas en el generador.
- Historial local de hasta 30 contraseñas.
- Eliminación automática de claves con más de 7 días.
- Autenticación biométrica o credencial del dispositivo para acceder a las claves guardadas.
- Sin Firebase, nube ni servidores externos.

## Seguridad

Las contraseñas se almacenan en el almacenamiento privado de la aplicación y se cifran mediante:

```text
Android Keystore
        ↓
Clave AES-256 protegida por el dispositivo
        ↓
AES-GCM con nonce único
        ↓
Archivo local cifrado
```

AES-GCM se utiliza para cifrar y descifrar la información. La clave AES no se muestra al usuario ni se guarda en texto plano. El acceso a las contraseñas guardadas requiere autenticación del dispositivo.

## Arquitectura

- `MainActivity.kt`: contenedor principal y navegación inferior.
- `GeneratorFragment.kt`: generador y vista previa del historial.
- `KeysFragment.kt`: historial protegido de contraseñas.
- `SettingsFragment.kt`: preferencias de seguridad y mantenimiento.
- `GeneratorViewModel.kt`: estado y lógica de generación.
- `SecurityRepository.kt`: cifrado y almacenamiento local.
- `CleanupWorker.kt`: eliminación periódica de claves antiguas.

La navegación utiliza Jetpack Navigation Component y la interfaz se construye con layouts XML y Material Design 3.

## Requisitos

- Android Studio.
- Android SDK API 36 para compilar.
- Android 12 / API 31 como versión mínima.
- JDK compatible con la versión de Android Studio instalada.

## Compilar

Desde la raíz del proyecto:

```bash
gradlew assembleDebug
```

El APK se genera en:

```text
app/build/outputs/apk/debug/
```

El nombre utiliza la versión y el número de compilación:

```text
PassPulse-v1(1).apk
```

El contador de compilación se guarda en `build-number.properties` y aumenta después de cada compilación exitosa.

## Estructura principal

```text
PassPulse/
├── app/
│   └── src/main/
│       ├── java/com/ulpro/passpulse/
│       ├── res/layout/
│       ├── res/navigation/
│       ├── res/menu/
│       └── AndroidManifest.xml
├── logo/
│   └── Android/
├── build.gradle.kts
└── README.md
```

## Privacidad

PassPulse no sincroniza contraseñas, no utiliza cuentas y no envía datos fuera del dispositivo. El usuario controla cuándo una contraseña se copia y cuándo se guarda en el historial.

## Licencia

Consulta el archivo `LICENSE` incluido en este repositorio.
