# Guía de Despliegue en Railway

Esta guía explica cómo desplegar la aplicación Spyzer en Railway.

## Requisitos Previos

1. Cuenta en [Railway](https://railway.app/)
2. CLI de Railway instalado (opcional): `npm i -g @railway/cli`
3. Repositorio Git conectado a Railway

## Servicios Necesarios

Tu proyecto en Railway debe tener los siguientes servicios:

1. **Aplicación Spring Boot** (servicio principal)
2. **MySQL** (base de datos)
3. **Redis** (caché)

## Configuración de Variables de Entorno

### Variables Automáticas (proporcionadas por Railway)

Cuando agregas los servicios MySQL y Redis, Railway configura automáticamente:

**MySQL:**
- `MYSQLHOST`
- `MYSQLPORT`
- `MYSQLDATABASE`
- `MYSQLUSER`
- `MYSQLPASSWORD`

**Redis:**
- `REDISHOST`
- `REDISPORT`
- `REDISPASSWORD`
- `REDISUSER`

**Railway:**
- `PORT` (puerto dinámico asignado por Railway)

### Variables que DEBES Configurar Manualmente

En el servicio de la aplicación Spring Boot, configura estas variables:

#### 1. Google OAuth2 (Autenticación)
```bash
GOOGLE_CLIENT_ID=tu-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=tu-client-secret
```

**Cómo obtenerlas:**
1. Ve a [Google Cloud Console](https://console.cloud.google.com/)
2. Crea un proyecto o usa uno existente
3. Habilita la API de Google OAuth2
4. Crea credenciales OAuth 2.0
5. Configura URI de redirección: `https://tu-app.railway.app/login/oauth2/code/google`

#### 2. JWT Secret Key
```bash
JWT_SECRET_KEY=genera-una-clave-secreta-muy-larga-y-segura-aqui
```

**Genera una clave segura:**
```bash
# Opción 1: Usando OpenSSL
openssl rand -base64 64

# Opción 2: Usando Node.js
node -e "console.log(require('crypto').randomBytes(64).toString('base64'))"
```

#### 3. Frontend URL
```bash
FRONTEND_URL=https://tu-frontend.vercel.app
```

#### 4. TwelveData API (Datos de mercado)
```bash
TWELVEDATA_API_KEY=tu-api-key-de-twelvedata
```

**Cómo obtenerla:**
1. Regístrate en [TwelveData](https://twelvedata.com/)
2. Ve a tu dashboard y copia tu API key

#### 5. Gmail SMTP (Envío de emails)
```bash
MAIL_USERNAME=tu-email@gmail.com
MAIL_PASSWORD=tu-contraseña-de-aplicacion
```

**Cómo obtener la contraseña de aplicación:**
1. Ve a tu cuenta de Google
2. Seguridad → Verificación en dos pasos (actívala si no está activa)
3. Contraseñas de aplicaciones
4. Genera una nueva contraseña para "Mail"

## Pasos de Despliegue

### 1. Crea un Nuevo Proyecto en Railway

```bash
# Opción A: Desde el dashboard web
1. Ve a railway.app
2. Click en "New Project"
3. Selecciona "Deploy from GitHub repo"
4. Conecta tu repositorio

# Opción B: Desde la CLI
railway init
railway link
```

### 2. Agrega el Servicio MySQL

```bash
# Desde el dashboard web:
1. Click en "+ New Service"
2. Selecciona "Database" → "MySQL"
3. Railway configurará automáticamente las variables de entorno

# Desde la CLI:
railway add --database mysql
```

### 3. Agrega el Servicio Redis

```bash
# Desde el dashboard web:
1. Click en "+ New Service"
2. Selecciona "Database" → "Redis"
3. Railway configurará automáticamente las variables de entorno

# Desde la CLI:
railway add --database redis
```

### 4. Configura las Variables de Entorno

Ve al servicio de tu aplicación Spring Boot y agrega todas las variables manuales mencionadas arriba:

```bash
# Desde la CLI (ejemplo):
railway variables set GOOGLE_CLIENT_ID="tu-client-id"
railway variables set GOOGLE_CLIENT_SECRET="tu-secret"
railway variables set JWT_SECRET_KEY="tu-jwt-secret"
railway variables set FRONTEND_URL="https://tu-frontend.com"
railway variables set TWELVEDATA_API_KEY="tu-api-key"
railway variables set MAIL_USERNAME="tu-email@gmail.com"
railway variables set MAIL_PASSWORD="tu-password-app"
```

### 5. Configura el Dominio (Opcional)

```bash
# Railway asigna automáticamente un dominio como: tu-app.up.railway.app
# Puedes configurar un dominio personalizado desde Settings → Domains
```

### 6. Despliega la Aplicación

```bash
# La aplicación se despliega automáticamente cuando haces push a main
git add .
git commit -m "Configure for Railway deployment"
git push origin main

# O desde la CLI:
railway up
```

## Verificación del Despliegue

### 1. Revisa los Logs

```bash
# Desde el dashboard web:
Click en tu servicio → Logs

# Desde la CLI:
railway logs
```

### 2. Verifica la Conexión a MySQL

Busca en los logs algo como:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

### 3. Verifica la Conexión a Redis

Busca en los logs:
```
Lettuce initialized
```

### 4. Prueba la Aplicación

```bash
curl https://tu-app.railway.app/actuator/health
```

## Troubleshooting

### Error: Connection refused (MySQL)

**Causa:** Las variables de entorno de MySQL no están configuradas o la aplicación no las está usando.

**Solución:**
1. Verifica que el servicio MySQL esté running
2. Verifica que las variables `MYSQLHOST`, `MYSQLPORT`, etc. estén configuradas
3. Revisa que el Dockerfile use el perfil `railway`

### Error: Connection refused (Redis)

**Causa:** Las variables de entorno de Redis no están configuradas.

**Solución:**
1. Verifica que el servicio Redis esté running
2. Verifica que las variables `REDISHOST`, `REDISPORT`, `REDISPASSWORD` estén configuradas

### Error: Application failed to start (OAuth2)

**Causa:** Variables de Google OAuth2 no configuradas.

**Solución:**
1. Verifica que `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` estén configuradas
2. Verifica que la URI de redirección en Google Cloud Console sea correcta

### Error: JWT Secret Key not found

**Causa:** Variable `JWT_SECRET_KEY` no configurada.

**Solución:**
```bash
railway variables set JWT_SECRET_KEY="$(openssl rand -base64 64)"
```

## Arquitectura en Railway

```
┌─────────────────────────────────────────┐
│         Railway Project                 │
│                                         │
│  ┌─────────────────┐                   │
│  │  Spring Boot    │                   │
│  │  Application    │                   │
│  │  (Port: $PORT)  │                   │
│  └────────┬────────┘                   │
│           │                             │
│           ├─────────┐                   │
│           │         │                   │
│  ┌────────▼──────┐ ┌▼─────────────┐   │
│  │     MySQL     │ │    Redis      │   │
│  │  (Port: 3306) │ │ (Port: 6379)  │   │
│  └───────────────┘ └───────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

## Configuración del Frontend

Asegúrate de configurar tu frontend para apuntar a la URL de Railway:

```javascript
// .env.production (Vercel, Netlify, etc.)
REACT_APP_API_URL=https://tu-app.railway.app
NEXT_PUBLIC_API_URL=https://tu-app.railway.app
```

## Monitoreo y Mantenimiento

### Ver Métricas
```bash
# Desde el dashboard web:
Metrics tab → Ver CPU, RAM, Network

# Desde la CLI:
railway status
```

### Ver Costos
```bash
# Desde el dashboard web:
Project → Usage
```

### Reiniciar Servicio
```bash
# Desde el dashboard web:
Service → Settings → Restart

# Desde la CLI:
railway restart
```

## Recursos Adicionales

- [Documentación de Railway](https://docs.railway.app/)
- [Railway Discord Community](https://discord.gg/railway)
- [Guía de Spring Boot en Railway](https://docs.railway.app/guides/spring-boot)

## Checklist de Despliegue

- [ ] Proyecto creado en Railway
- [ ] Servicio MySQL agregado y running
- [ ] Servicio Redis agregado y running
- [ ] Variable `GOOGLE_CLIENT_ID` configurada
- [ ] Variable `GOOGLE_CLIENT_SECRET` configurada
- [ ] Variable `JWT_SECRET_KEY` configurada
- [ ] Variable `FRONTEND_URL` configurada
- [ ] Variable `TWELVEDATA_API_KEY` configurada
- [ ] Variable `MAIL_USERNAME` configurada
- [ ] Variable `MAIL_PASSWORD` configurada
- [ ] URI de redirección OAuth configurada en Google Cloud Console
- [ ] Dockerfile usa perfil `railway` (✓ Ya configurado)
- [ ] Aplicación desplegada exitosamente
- [ ] Logs muestran conexión exitosa a MySQL
- [ ] Logs muestran conexión exitosa a Redis
- [ ] Endpoint de salud responde: `/actuator/health`
- [ ] Frontend configurado con URL de Railway
