# Deploy DiscographyX: Render + Neon + Vercel

The deployment uses the existing GitHub repository. No CLI installation or plugin is required. Keep Commerce Platform's resources independent.

## 1. Neon

Create the `discographyx` project in AWS US East 2 (Ohio), with only Postgres enabled. The default database `neondb` and role `neondb_owner` are suitable for this separate project.

In **Connect**, disable **Connection pooling** and retain the direct hostname, database, role and password. Supply these only through Render's environment settings. Do not commit the connection string or password.

## 2. Render backend

Create a **Web Service** from `cesararamayo3068/discographyx`:

| Setting           | Value                                                    |
| ----------------- | -------------------------------------------------------- |
| Name              | `discographyx-backend`                                   |
| Branch            | `feature/bands-and-albums` until the release is merged   |
| Region            | Ohio                                                     |
| Runtime           | Docker                                                   |
| Root directory    | Leave empty                                              |
| Dockerfile path   | `./Dockerfile`                                           |
| Health check path | `/management/health`                                     |
| Instance          | Free for the portfolio demo, if available in the account |

Set these environment variables:

| Variable                                             | Value                                                              |
| ---------------------------------------------------- | ------------------------------------------------------------------ |
| `SPRING_PROFILES_ACTIVE`                             | `prod,render`                                                      |
| `SPRING_DATASOURCE_URL`                              | `jdbc:postgresql://<NEON_DIRECT_HOST>:5432/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME`                         | `neondb_owner`                                                     |
| `SPRING_DATASOURCE_PASSWORD`                         | The password from Neon                                             |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | A new random Base64 secret with at least 64 decoded bytes          |
| `FRONTEND_URL`                                       | The exact Vercel production origin, without a trailing slash       |

If Vercel has not assigned the frontend URL yet, initially set `FRONTEND_URL` to `http://localhost:14201`. Replace it with the exact Vercel production origin before testing the hosted frontend.

Use the actual host shown by Neon, without `-pooler`. Spring Boot expects the JDBC format shown above, with the username and password in their separate variables. Render supplies `PORT`; the application listens on that port.

The image builds the backend independently of Node and Angular, runs as a non-root user and activates no sample-secret profile. The Render Liquibase context deactivates the generated `admin` and `user` sample accounts. Local development accounts are unchanged. Migration completion is required before the service becomes healthy.

Account activation and password-reset email require an SMTP service before advertising registration as a usable hosted feature. Public band searches and discographies do not require an account. Do not enable the generated sample-secret profile or use default sample credentials in production.

## 3. Vercel frontend

Import the same GitHub repository as an Angular project. Use the code from the release branch and configure that branch as the production branch while `main` still contains the old application.

`vercel.json` supplies the installation command (`npm ci`), production build (`npm run webapp:build:prod`), output directory (`target/classes/static`) and Angular route fallback. Select Node.js 24 to match the project's engine requirement.

Set `SERVER_API_URL` to the actual Render origin, for example `https://<YOUR_BACKEND>.onrender.com/`. Do not append `/api`: the Angular services already include it. This URL is public configuration compiled into the frontend, not a secret.

Once Vercel assigns the final production URL, update Render's `FRONTEND_URL` to exactly that origin and redeploy Render. The API permits that origin, rather than arbitrary Vercel previews. Changing `SERVER_API_URL` requires rebuilding the Vercel deployment.

## 4. Verify

1. Render's `/management/health` returns `UP`.
2. Search for Malevolent Creation in the Vercel app; one match opens its discography.
3. Open an album and verify its cover and tracks.
4. Reload `/bandas/<mbid>` directly; Vercel must serve Angular instead of a 404.
5. Check that the generated sample accounts cannot log in.

Then add the live Vercel URL and a real screenshot to the portfolio. The existing LinkedIn featured link points to the portfolio's `/linkedin` page; update that page's Open Graph image and refresh its preview after the portfolio deployment. This is separate from the LinkedIn profile's cover photo.
