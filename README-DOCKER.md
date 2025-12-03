# Despliegue de la Aplicación Spyzer (Spring Boot + MySQL + Redis)

Este proyecto utiliza Docker Compose para orquestar la aplicación de Spring Boot, MySQL y Redis. Se definen dos configuraciones distintas: una para **Desarrollo** y otra para **Producción**, siguiendo el principio DevOps de mantener la misma estructura pero diferentes configuraciones.

---

## 🚀 Uso en Desarrollo (Pruebas Locales)

El archivo `docker-compose.dev.yml` es para pruebas rápidas en tu máquina local.

### Características

| Característica | Propósito |
|:---|:---|
| **MySQL** | Puerto 3306 expuesto, contraseña simple (`admin`) |
| **Redis** | Puerto 6379 expuesto, sin contraseña |
| **Volúmenes** | Persistencia básica local |
| **Puertos** | MySQL (3306), Redis (6379) y App (8080) expuestos a `localhost` |
| **Restart Policy** | No configurado (los contenedores no se reinician automáticamente) |

### Comando de Ejecución

**Paso 1:** Compilar el JAR de la aplicacion
```
./mvnw clean package -DskipTests
```

**Paso 2:** Levantar los servicios
```bash
docker compose -f docker-compose.dev.yml up --build -d
```

**Paso 3:** Ver logs (opcional)
```bash
docker compose -f docker-compose.dev.yml logs -f app
```

**Paso 4:** Detener los servicios
```bash
docker compose -f docker-compose.dev.yml down
```

---

## 🔒 Uso en Producción (Entorno Real)

El archivo `docker-compose.prod.yml` aplica capas de seguridad y resiliencia.

### Características

| Característica | Propósito |
|:---|:---|
| **Contraseñas** | **OBLIGATORIAS.** MySQL y Redis requieren contraseñas seguras |
| **Puertos** | MySQL (3306) y Redis (6379) **NO** están mapeados al host (inaccesibles desde fuera de la red Docker) |
| **Restart Policy** | `restart: always` garantiza que todos los servicios se recuperen automáticamente de fallos |
| **Variables de Entorno** | Todas las credenciales y configuraciones sensibles se inyectan desde variables de entorno |
| **Volúmenes** | Volúmenes nombrados para producción con persistencia estricta |

### Comando de Ejecución

**Paso 1:** Crear archivo `.env` con las variables de entorno

Copia el archivo `.env.example` y renómbralo a `.env`:
```bash
cp .env.example .env
```

Edita el archivo `.env` y configura todas las variables con valores seguros:
```bash
# Ejemplo de .env
MYSQL_ROOT_PASSWORD=una_clave_mysql_muy_segura_de_32_caracteres
REDIS_PASSWORD=una_clave_redis_muy_segura_de_20_caracteres
FRONTEND_URL=https://tudominio.com
JWT_SECRET_KEY=tu_clave_jwt_secreta_de_64_caracteres_hexadecimal
TWELVEDATA_API_KEY=tu_api_key_de_twelvedata
MAIL_USERNAME=tu_email@gmail.com
MAIL_PASSWORD=tu_contraseña_de_aplicacion_gmail
GOOGLE_CLIENT_ID=tu_google_client_id
GOOGLE_CLIENT_SECRET=tu_google_client_secret
```

**Paso 2:** Compilar el JAR de la aplicación
```bash
./mvnw clean package -DskipTests
```

**Paso 3:** Levantar los servicios en producción
```bash
docker compose -f docker-compose.prod.yml up --build -d
```

**Paso 4:** Verificar el estado de los servicios
```bash
docker compose -f docker-compose.prod.yml ps
```

**Paso 5:** Ver logs (opcional)
```bash
docker compose -f docker-compose.prod.yml logs -f app
```

**Paso 6:** Detener los servicios
```bash
docker compose -f docker-compose.prod.yml down
```

---

## 📋 Diferencias Clave entre Desarrollo y Producción

| Aspecto | Desarrollo | Producción |
|:---|:---|:---|
| **Seguridad** | Sin contraseñas o contraseñas simples | Contraseñas obligatorias desde variables de entorno |
| **Puertos** | Todos los puertos expuestos | Solo el puerto 8080 de la app expuesto |
| **Restart Policy** | No configurado | `restart: always` en todos los servicios |
| **Volúmenes** | Volúmenes anónimos | Volúmenes nombrados para mejor gestión |
| **Variables de Entorno** | Valores hardcodeados | Inyección desde archivo `.env` |
| **Healthchecks** | Configurados | Configurados con contraseñas |

---

## 🛠️ Comandos Útiles

### Ver logs de un servicio específico
```bash
docker compose -f docker-compose.dev.yml logs -f mysql
docker compose -f docker-compose.dev.yml logs -f redis
docker compose -f docker-compose.dev.yml logs -f app
```

### Acceder a la consola de MySQL
```bash
# Desarrollo
docker exec -it spyzer_mysql_dev mysql -uroot -padmin

# Producción (requiere la contraseña del .env)
docker exec -it spyzer_mysql_prod mysql -uroot -p
```

### Acceder a la consola de Redis
```bash
# Desarrollo
docker exec -it spyzer_redis_dev redis-cli

# Producción (requiere la contraseña del .env)
docker exec -it spyzer_redis_prod redis-cli -a tu_contraseña_redis
```

### Reconstruir solo la aplicación
```bash
docker compose -f docker-compose.dev.yml up --build app
```

### Eliminar volúmenes (⚠️ CUIDADO: Borra todos los datos)
```bash
docker compose -f docker-compose.dev.yml down -v
```

---

## 📝 Notas Importantes

1. **Archivo `.env`**: Nunca subas el archivo `.env` a Git. Está incluido en `.gitignore` por seguridad.

2. **Compilación del JAR**: Asegúrate de compilar el JAR antes de ejecutar `docker compose up --build`. El Dockerfile espera encontrar el JAR en `target/spyzer-0.0.1-SNAPSHOT.jar`.

3. **Perfiles de Spring**: La aplicación usa el perfil `docker` cuando se ejecuta en contenedores. Asegúrate de tener un archivo `application-docker.properties` si necesitas configuraciones específicas para Docker.

4. **Healthchecks**: Los servicios tienen healthchecks configurados para garantizar que MySQL y Redis estén completamente operativos antes de que la aplicación intente conectarse.

5. **Reverse Proxy**: En producción, se recomienda usar un reverse proxy (como Nginx o Traefik) delante de la aplicación para manejar SSL/TLS y balanceo de carga.

---

## 🐳 Arquitectura de Contenedores

```
┌─────────────────────────────────────────┐
│         Docker Network (default)        │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌───────┐ │
│  │  MySQL   │  │  Redis   │  │  App  │ │
│  │  :3306   │  │  :6379   │  │ :8080 │ │
│  └──────────┘  └──────────┘  └───────┘ │
│       │             │            │      │
│       └─────────────┴────────────┘      │
│              (Internal Network)         │
└─────────────────────────────────────────┘
                    │
              (Port Mapping)
                    │
            ┌───────┴────────┐
            │   Host Machine │
            │   localhost    │
            └────────────────┘
```

---

## 🔐 Generación de Claves Seguras

### Contraseña MySQL/Redis (32 caracteres)
```bash
openssl rand -base64 32
```

### JWT Secret Key (64 caracteres hexadecimal)
```bash
openssl rand -hex 32
```

---

## 📚 Referencias

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [MySQL Docker Hub](https://hub.docker.com/_/mysql)
- [Redis Docker Hub](https://hub.docker.com/_/redis)
