# ✅ Checklist de Despliegue a Producción - Spyzer

## 📋 Estado Actual

### ✅ Completado:
- ✅ Docker Compose de producción configurado (`docker-compose.prod.yml`)
- ✅ Archivo `.env.prod` creado con contraseñas seguras
- ✅ Redis configurado con contraseña
- ✅ MySQL configurado con contraseña segura
- ✅ JWT con clave segura de producción
- ✅ Caché de Redis funcionando (market_data + rankings)
- ✅ Servicios sin puertos expuestos (solo app en 8080)

### ⚠️ Pendiente de Configurar:

#### 1. **Completar `.env.prod`** (en el servidor de producción)

Edita el archivo `.env.prod` y completa:

```bash
# FRONTEND_URL - Tu dominio real
FRONTEND_URL=https://tu-dominio-frontend.com

# EMAIL - Configuración real de Gmail
MAIL_USERNAME=notificaciones@tudominio.com
MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx  # Contraseña de aplicación de Gmail

# GOOGLE OAUTH - Credenciales de producción
GOOGLE_CLIENT_ID=xxxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxx

# API KEY - Verificar si necesitas una key diferente para producción
TWELVEDATA_API_KEY=tu_api_key
```

#### 2. **Crear Contraseña de Aplicación de Gmail**

1. Ve a: https://myaccount.google.com/apppasswords
2. Crea una contraseña para "Spyzer Backend"
3. Copia la contraseña generada (formato: xxxx-xxxx-xxxx-xxxx)
4. Pégala en `MAIL_PASSWORD` en `.env.prod`

#### 3. **Configurar Google OAuth para Producción**

1. Ve a: https://console.cloud.google.com/apis/credentials
2. Crea nuevas credenciales OAuth 2.0 para PRODUCCIÓN
3. Configura URLs autorizadas:
   - **Authorized JavaScript origins**: `https://tu-dominio.com`
   - **Authorized redirect URIs**: `https://tu-dominio.com/login/oauth2/code/google`
4. Copia Client ID y Client Secret a `.env.prod`

⚠️ **IMPORTANTE**: NO uses las credenciales de desarrollo en producción

#### 4. **Configurar Dominio y HTTPS**

- [ ] Configurar dominio (DNS)
- [ ] Certificado SSL/TLS (Let's Encrypt con Certbot)
- [ ] Reverse proxy (Nginx/Traefik) si es necesario
- [ ] Actualizar `FRONTEND_URL` en `.env.prod`

---

## 🚀 Comandos de Despliegue

### En el Servidor de Producción:

```bash
# 1. Clonar el repositorio (o hacer pull de la última versión)
git clone https://github.com/tu-repo/spyzer.git
cd spyzer/spyzer

# 2. Crear y editar .env.prod (si no existe ya)
nano .env.prod
# Completar todos los valores pendientes

# 3. Compilar el proyecto
mvn clean package -DskipTests

# 4. Levantar servicios en producción
docker compose -f docker-compose.prod.yml up --build -d

# 5. Verificar que todo está corriendo
docker compose -f docker-compose.prod.yml ps

# 6. Ver logs en tiempo real
docker compose -f docker-compose.prod.yml logs -f app
```

### Verificar Servicios:

```bash
# Verificar salud de los contenedores
docker ps

# Verificar logs de la aplicación
docker logs spyzer_backend_prod --tail 50

# Verificar Redis (con contraseña)
docker exec spyzer_redis_prod redis-cli -a "Rd!s_Pr0d_7K9mN2pQ8xL4wB6vZ3hY5jC" PING

# Verificar MySQL
docker exec spyzer_mysql_prod mysqladmin ping -h localhost -uroot -p
```

---

## 🔒 Seguridad

### ✅ Implementado:
- ✅ Contraseñas fuertes generadas para MySQL y Redis
- ✅ JWT con clave segura de 128+ caracteres
- ✅ MySQL y Redis SIN puertos expuestos externamente
- ✅ Variables de entorno separadas (dev/prod)
- ✅ `.env*` protegido en `.gitignore`

### 📝 Recomendaciones Adicionales:
- [ ] Configurar firewall en el servidor (solo permitir 80/443/8080)
- [ ] Usar HTTPS para todas las comunicaciones
- [ ] Configurar backup automático de MySQL
- [ ] Implementar rate limiting en el reverse proxy
- [ ] Monitoreo con logs centralizados (opcional)

---

## 📊 Verificación Post-Despliegue

### 1. Verificar API:
```bash
# Health check
curl https://tu-dominio.com/actuator/health

# Verificar market data
curl https://tu-dominio.com/api/market-data/indices
```

### 2. Verificar Caché de Redis:
```bash
# Ver keys de caché
docker exec spyzer_redis_prod redis-cli -a "Rd!s_Pr0d_7K9mN2pQ8xL4wB6vZ3hY5jC" KEYS "*"

# Verificar rankings
docker exec spyzer_redis_prod redis-cli -a "Rd!s_Pr0d_7K9mN2pQ8xL4wB6vZ3hY5jC" KEYS "rankings::*"

# Verificar market data
docker exec spyzer_redis_prod redis-cli -a "Rd!s_Pr0d_7K9mN2pQ8xL4wB6vZ3hY5jC" KEYS "premiumPrices::*"
```

### 3. Verificar Funcionalidades:
- [ ] Login/Register
- [ ] Google OAuth
- [ ] Rankings (debe cachear después de primera carga)
- [ ] Cotizaciones (debe cachear market data)
- [ ] Trading (compra/venta)
- [ ] Notificaciones por email

---

## 🛠️ Comandos Útiles de Mantenimiento

```bash
# Ver logs en tiempo real
docker compose -f docker-compose.prod.yml logs -f app

# Reiniciar solo la aplicación
docker compose -f docker-compose.prod.yml restart app

# Limpiar caché de Redis (si es necesario)
docker exec spyzer_redis_prod redis-cli -a "Rd!s_Pr0d_7K9mN2pQ8xL4wB6vZ3hY5jC" FLUSHALL

# Backup de MySQL
docker exec spyzer_mysql_prod mysqldump -uroot -p spyzerifnotexist > backup_$(date +%Y%m%d).sql

# Actualizar código (nuevo commit)
git pull
mvn clean package -DskipTests
docker compose -f docker-compose.prod.yml up --build -d
```

---

## 📞 Soporte

Si encuentras problemas durante el despliegue:
1. Revisa los logs: `docker logs spyzer_backend_prod`
2. Verifica que `.env.prod` esté completo
3. Asegúrate de que los puertos 8080 estén libres
4. Verifica la conectividad de red entre contenedores

---

**Última actualización**: 2025-12-01
**Versión**: 1.0
