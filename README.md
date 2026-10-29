# DiscographyX 🎸

**DiscographyX** es una aplicación web en desarrollo para explorar bandas, álbumes y discografías de heavy metal y sus subgéneros (death, thrash, black y doom metal).

> **Estado actual:** prototipo funcional con interfaz personalizada, autenticación y entorno de desarrollo Docker. El catálogo de bandas y álbumes, las búsquedas reales y la integración con MusicBrainz **todavía no están implementados**.

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

**Planeadas**

- Entidades y operaciones CRUD para bandas y álbumes.
- Discografía cronológica por banda.
- Búsqueda y filtros por género, país y año.
- Consulta de información musical desde MusicBrainz.

> Las tarjetas de bandas y álbumes de la portada son por ahora elementos de presentación, no un catálogo funcional.

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

## Flujo de trabajo

- Cambios agrupados por etapas, habitualmente distribuidos como archivos ZIP.
- Versionado con Git y mensajes de commit en inglés.
- Dependencias y herramientas de compilación ejecutadas dentro de Docker.
- El archivo `.env` y cualquier otro secreto deben permanecer fuera del repositorio.

## Próximos pasos

1. Modelar bandas y álbumes con JHipster.
2. Conectar la portada con datos reales.
3. Incorporar MusicBrainz.
4. Crear un Docker Compose reproducible desde un clon limpio.
5. Añadir pruebas, capturas y documentación de despliegue.

## Referencias

- Documentación de JHipster 9.4.0: https://www.jhipster.tech/documentation-archive/v9.4.0
- Angular: https://angular.dev/
- Spring Boot: https://spring.io/projects/spring-boot

**Nota:** el entorno descrito es de desarrollo local; no está preparado para despliegue de producción.
