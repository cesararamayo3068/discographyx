# DiscographyX 🎸

**DiscographyX** es una aplicación web en desarrollo para explorar bandas, álbumes y discografías de heavy metal y sus subgéneros (death, thrash, black y doom metal).

> **Estado actual:** explorador musical funcional con búsqueda acotada, fichas de bandas, discografías, tapas y temas desde MusicBrainz, identidad visual propia y entorno Docker. Los favoritos todavía están pendientes.

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Generador | JHipster 9.4.0 (monolito) |
| Frontend | Angular 22, TypeScript, SCSS |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| Base de datos | PostgreSQL 17, Liquibase |
| Autenticación | JWT (base generada por JHipster) |
| Desarrollo | Docker y Docker Compose |

## Funcionalidades

**Implementadas**

- Página de inicio y navegación con estética metalera, diseño oscuro y secciones exploratorias.
- Autenticación y administración provistas por JHipster.
- Comunicación Angular → Spring Boot → PostgreSQL en el entorno de desarrollo.
- Buscador público con hasta seis coincidencias pertinentes por nombre; una única coincidencia abre su discografía automáticamente.
- Discografía cronológica con tapas de Cover Art Archive y filtros para estudio, EPs, singles, en vivo y compilaciones.
- Ficha de álbum con año original, temas, duración y cantidad de canciones de una edición documentada.
- Enlaces de streaming cuando existen y búsqueda de audio por tema en YouTube Music.
- Identidad DiscographyX en navegación, título, ícono, carga y pie de página.

**Planeadas**

- Favoritos por usuario autenticado en PostgreSQL, con MBID único por usuario.

El recorrido es buscar una banda → elegirla si hay homónimos → explorar sus discos → abrir un álbum y consultar sus temas. MusicBrainz no sirve audio: los enlaces abren el proveedor externo. No hay reproductor integrado ni muestras simuladas. Las fichas resumen datos documentados; no son reseñas editoriales inventadas.

### Búsqueda musical

`GET /api/music/artists?query=Malevolent%20Creation` es público y devuelve hasta seis coincidencias. Busca la frase completa y prioriza nombres exactos, conservando homónimos. No busca cada palabra por separado. `total` cuenta las coincidencias mostradas; solo se admite `offset=0`. El nombre debe tener entre 2 y 100 caracteres.

`GET /api/music/artists/{mbid}?offset=0` devuelve la banda, géneros y hasta 100 release groups por página. La interfaz empieza por los álbumes de estudio y permite cargar más cuando el catálogo supera esa cantidad. Se ordenan cronológicamente los discos cargados.

`GET /api/music/albums/{release-group-mbid}` devuelve los temas de una edición disponible, priorizando una oficial y la más temprana entre las devueltas por MusicBrainz. La lista asociada de ediciones puede estar limitada por la API: no se afirma que sea la primera edición mundial. La cantidad de temas, duración, fecha y país corresponden a esa edición; el año original pertenece al release group. Los campos ausentes son `null`. Las portadas tienen un reemplazo visual cuando no están disponibles.

El backend identifica la aplicación con User-Agent, espacia las consultas externas al menos 1,1 segundos por instancia, aplica tiempos de espera (3 segundos de conexión y 8 por solicitud) y guarda hasta 500 respuestas de metadatos durante 10 minutos en Caffeine. Las fichas comparten ese límite y esperan su turno. Ausencia de artista o disco devuelve 404; indisponibilidad externa, 503; timeout, 504; respuesta inválida, 502. No hay reintentos automáticos. Para varias réplicas deberá compartirse el límite antes de escalar.

Se eligieron DTOs externos en lugar de generar un CRUD de bandas y álbumes: MusicBrainz conserva la información musical, y PostgreSQL almacenará los favoritos y datos propios. `discographyx.jdl` y `.jhipster/Band.json` / `Album.json` quedan como propuesta inicial sin ejecutar; no se necesitan para el buscador y no deben generarse automáticamente. Los álbumes usan MBID de release-group para evitar duplicar ediciones.

Para probar: abrir `http://localhost:14201`, ir a «Explorar el archivo» y buscar `Death` o `Iron Maiden`, sin iniciar sesión.

### Verificación en Docker

Los archivos locales modificados deben estar sincronizados al volumen de desarrollo antes de estos comandos:

```cmd
docker exec discographyx-backend ./mvnw -ntp -Dskip.installnodenpm -Dskip.npm -Dtest=MusicBrainzServiceTest,MusicBrainzResourceTest,TechnicalStructureTest test
docker exec discographyx-frontend npx ng test --watch=false --coverage=false --include=src/main/webapp/app/home/home.spec.ts --include=src/main/webapp/app/discography/discography.spec.ts
docker exec discographyx-frontend npm run webapp:build:dev
```

Las pruebas del buscador simulan MusicBrainz y no requieren acceso al servicio externo.

## Organización del código

```text
src/main/java/                  Backend Java / Spring Boot
src/main/resources/             Configuración, Liquibase y recursos
src/main/webapp/app/            Aplicación Angular
src/main/webapp/app/home/       Portada de DiscographyX
src/main/webapp/app/layouts/    Navegación y estructura de la UI
src/main/webapp/content/scss/   Estilos globales
src/main/docker/                Configuraciones auxiliares de JHipster
compose.yaml                    Orquestación local de tres servicios
```

## Desarrollo local con Docker

El proyecto se trabaja en **Windows + Docker Desktop**, sin instalar Java, Node.js ni Maven globalmente en Windows.

### Puertos

| Servicio | Dirección local |
| --- | --- |
| Angular | `http://localhost:14201` |
| Spring Boot | `http://localhost:18081` |
| PostgreSQL | `127.0.0.1:55433` |

### Preparación de la configuración

1. Copiar `.env.example` a `.env` en la raíz del proyecto.
2. Completar `DISCOGRAPHYX_DB_PASSWORD` con la contraseña correspondiente a **tu base de datos local**.
3. Mantener `.env` fuera del control de versiones. No subir contraseñas ni secretos JWT a GitHub.

### Iniciar y detener

Desde la carpeta del proyecto:

```cmd
docker compose up -d
```

```cmd
docker compose ps
```

```cmd
docker compose stop
```

Para volver a iniciar los servicios detenidos:

```cmd
docker compose start
```

### Importante: configuración de desarrollo existente

El archivo `compose.yaml` actual **no es una instalación autocontenida desde cero**. Reutiliza volúmenes Docker externos ya preparados:

- `discographyx-generator-work`: código generado, dependencias y entorno de trabajo de JHipster.
- `discographyx-postgres-data`: datos persistentes de PostgreSQL.

También utiliza la red externa `discographyx-network`. Esos recursos deben existir previamente y contener la aplicación/configuración esperada. **Clonar este repositorio y ejecutar `docker compose up -d` en otra computadora no alcanza para reproducir el entorno**; queda pendiente crear una configuración de instalación limpia y portable.

Además, los contenedores leen el código del volumen `discographyx-generator-work`, **no directamente de la carpeta Windows**. Si se cambian archivos locales, hay que sincronizarlos con el volumen o modificar la estrategia de montaje. Para actualizar la interfaz en el entorno actual:

```cmd
docker cp src/main/webapp/. discographyx-frontend:/home/jhipster/app/src/main/webapp/
```

El frontend de desarrollo puede recompilar automáticamente al detectar los cambios.

El backend omite las tareas Maven de instalación y compilación de Node (`-Dskip.installnodenpm -Dskip.npm`), ya que Angular se ejecuta en su propio contenedor con las dependencias del volumen existente.

## Flujo de trabajo

Los menús y pantallas de cuenta admiten **Español / English**. Español es el idioma inicial y el selector de la barra permite cambiarlo. La elección se conserva al recargar dentro de la sesión del navegador y al guardar la configuración de cuenta. Los textos propios de portada y discografía siguen en castellano; los nombres y descripciones musicales se muestran como llegan de MusicBrainz.

- Cambios agrupados por etapas, habitualmente distribuidos como archivos ZIP.
- Versionado con Git y mensajes de commit en inglés.
- Dependencias y herramientas de compilación ejecutadas dentro de Docker.
- El archivo `.env` y cualquier otro secreto deben permanecer fuera del repositorio.

## Próximos pasos

1. Enriquecer fichas con reseñas de una fuente editorial identificada.
2. Evaluar un proveedor autorizado para reproducción integrada.
3. Persistir favoritos protegidos por JWT.
4. Crear un Docker Compose reproducible desde un clon limpio.
5. Añadir pruebas, capturas y documentación de despliegue.

## Referencias

- Documentación de JHipster 9.4.0: https://www.jhipster.tech/documentation-archive/v9.4.0
- Angular: https://angular.dev/
- Spring Boot: https://spring.io/projects/spring-boot

**Nota:** el entorno descrito es de desarrollo local; no está preparado para despliegue de producción.
