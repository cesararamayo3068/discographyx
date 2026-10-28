@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 exit /b 1
if not exist .git (
  echo ERROR: Ejecutar dentro del repositorio DiscographyX.
  exit /b 1
)
docker run --rm -it --name discographyx-generator -v "%cd%:/home/jhipster/app" -w /home/jhipster/app jhipster/jhipster:latest jhipster app --base-name discographyx --application-type monolith --package-name com.discographyx.app --auth jwt --dev-database-type postgresql --db postgresql --client-framework angular --build maven --cache-provider caffeine --database-migration liquibase --skip-git
endlocal
