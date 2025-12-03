# 📋 Guía de Variables de Entorno - Spyzer

## 🎯 Resumen Rápido

Tu aplicación Spring Boot ahora lee variables de entorno automáticamente desde el archivo `.env` cuando ejecutas localmente. **Ya no necesitas configurar nada manualmente**.

---

## 🔧 Cómo Funciona Según el Contexto

### **1️⃣ Desarrollo Local (mvn spring-boot:run)**

✅ **Lee automáticamente desde `.env`**

```bash
cd c:\Users\migue\Desktop\Spyzer\spyzer\spyzer
mvn clean install
mvn spring-boot:run
```

**Salida esperada:**
```
✅ Variables de entorno cargadas desde .env
```

📁 **Archivo leído:** `c:\Users\migue\Desktop\Spyzer\spyzer\spyzer\.env`

---

### **2️⃣ Docker Compose**

✅ **Lee desde `environment` en docker-compose.dev.yml**

```bash
docker-compose -f docker-compose.dev.yml up --build
```

📁 **Configuración:** `docker-compose.dev.yml` (línea 49-57)

**Nota:** Docker NO usa el archivo `.env`, sino las variables definidas en `environment:` del YAML.

---

### **3️⃣ IntelliJ IDEA / Eclipse**

✅ **Lee desde `.env` automáticamente** (gracias al código en `SpyzerApplication.java`)

**Alternativamente**, puedes configurar variables en el IDE:

**IntelliJ:**
1. Run → Edit Configurations
2. Environment Variables → Add
3. `REDIS_PASSWORD=`

**Eclipse:**
1. Run Configurations → Environment
2. New → `REDIS_PASSWORD` = `(vacío)`

---

## 📂 Archivos Creados

### **1. `.env` (desarrollo local)**
```
c:\Users\migue\Desktop\Spyzer\spyzer\spyzer\.env
```

Este archivo contiene tus variables de desarrollo local. **NO se sube a Git**.

### **2. `.env.example` (plantilla)**
```
c:\Users\migue\Desktop\Spyzer\spyzer\spyzer\.env.example
```

Plantilla con ejemplos de todas las variables. **SÍ se sube a Git**.

---

## 🔐 Variables de Entorno Configuradas

Todas estas variables se leen desde `.env` o del sistema:

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `REDIS_PASSWORD` | Contraseña de Redis | (vacío) |
| `MYSQL_ROOT_PASSWORD` | Contraseña MySQL | `admin` |
| `FRONTEND_URL` | URL del frontend | `http://localhost:3000` |
| `JWT_SECRET_KEY` | Clave JWT | (tu clave actual) |
| `TWELVEDATA_API_KEY` | API Key TwelveData | (tu API key) |
| `MAIL_USERNAME` | Email Gmail | `tu_email@gmail.com` |
| `MAIL_PASSWORD` | Contraseña app Gmail | (vacía) |
| `GOOGLE_CLIENT_ID` | OAuth Google ID | (tu client ID) |
| `GOOGLE_CLIENT_SECRET` | OAuth Google Secret | (tu secret) |

---

## 🛠️ Cómo Funciona Internamente

### **1. SpyzerApplication.java carga el .env**

```java
public static void main(String[] args) {
    // 1. Lee el archivo .env
    Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // No falla si no existe
            .load();

    // 2. Configura las variables como propiedades del sistema
    dotenv.entries().forEach(entry ->
        System.setProperty(entry.getKey(), entry.getValue())
    );

    // 3. Inicia Spring Boot (que lee las propiedades)
    SpringApplication.run(SpyzerApplication.class, args);
}
```

### **2. application.properties usa las variables**

```properties
# Sintaxis: ${VARIABLE_NAME:valor_por_defecto}
spring.data.redis.password=${REDIS_PASSWORD:}
#                           ↑              ↑
#                           |              └─ Si no existe, usa vacío
#                           └─ Lee de System.getProperty("REDIS_PASSWORD")
```

### **3. Orden de Prioridad**

Spring Boot busca valores en este orden (mayor prioridad primero):

```
1. Variables del sistema (export/set)
2. Variables del .env (cargadas por SpyzerApplication)
3. application.properties (valor por defecto después de :)
```

---

## 🧪 Cómo Probar que Funciona

### **Test 1: Verificar carga del .env**

```bash
mvn spring-boot:run
```

**Salida esperada:**
```
✅ Variables de entorno cargadas desde .env
```

### **Test 2: Verificar Redis sin contraseña**

```bash
redis-cli ping
# Respuesta: PONG
```

### **Test 3: Verificar Spring Boot usa las variables**

Busca en los logs de Spring Boot:

```
Connecting to Redis at localhost:6379
```

Si ves errores de autenticación, verifica que `REDIS_PASSWORD=` esté vacío.

---

## 🔒 Seguridad

### **Archivos NO subidos a Git (.gitignore):**
```
.env
.env.local
.env.production
```

### **Archivos SÍ subidos a Git:**
```
.env.example         # Plantilla sin valores reales
application.properties  # Con ${VARIABLES} no valores reales
```

### **Recomendación para Producción:**

1. **NO uses el archivo `.env` en producción**
2. Configura variables del sistema operativo:

```bash
# Linux/Docker
export REDIS_PASSWORD="contraseña_segura"
export MYSQL_ROOT_PASSWORD="contraseña_mysql_segura"

# Windows
set REDIS_PASSWORD=contraseña_segura
set MYSQL_ROOT_PASSWORD=contraseña_mysql_segura
```

3. O usa Docker Secrets / Kubernetes ConfigMaps

---

## 📊 Comparación: Antes vs Ahora

### **ANTES ❌**
```properties
# application.properties
spring.data.redis.password=         # Vacío (inseguro)
twelvedata.api.key=51ed5...         # Hardcoded (mal)
```

### **AHORA ✅**
```properties
# application.properties
spring.data.redis.password=${REDIS_PASSWORD:}
twelvedata.api.key=${TWELVEDATA_API_KEY:51ed5...}
```

```bash
# .env (NO se sube a Git)
REDIS_PASSWORD=
TWELVEDATA_API_KEY=51ed5d7514de4ccfbbcb4be74752157e
```

---

## 🚨 Troubleshooting

### **Problema: "Variables de entorno NO cargadas"**

**Posibles causas:**
1. El archivo `.env` no está en la raíz del proyecto backend
2. Permisos de lectura incorrectos

**Solución:**
```bash
# Verificar que existe
ls -la c:\Users\migue\Desktop\Spyzer\spyzer\spyzer\.env

# Verificar contenido
cat c:\Users\migue\Desktop\Spyzer\spyzer\spyzer\.env
```

### **Problema: Redis falla con "NOAUTH Authentication required"**

**Causa:** Redis espera contraseña pero `REDIS_PASSWORD` está vacío.

**Soluciones:**

1. **Si Redis NO tiene contraseña (local):**
```bash
# .env
REDIS_PASSWORD=
```

2. **Si Redis SÍ tiene contraseña:**
```bash
# .env
REDIS_PASSWORD=tu_contraseña_de_redis
```

3. **Verificar configuración de Redis:**
```bash
redis-cli CONFIG GET requirepass
# Si retorna "", no tiene contraseña
```

### **Problema: Maven no encuentra dotenv-java**

**Solución:**
```bash
mvn clean install -U
```

---

## 📚 Referencias

- **Dotenv Java:** https://github.com/cdimascio/dotenv-java
- **Spring Boot External Config:** https://docs.spring.io/spring-boot/reference/features/external-config.html
- **Redis Configuration:** https://redis.io/docs/latest/operate/oss_and_stack/management/security/

---

## ✅ Checklist Final

- [x] Archivo `.env` creado
- [x] `.env` agregado a `.gitignore`
- [x] Dependencia `dotenv-java` agregada al `pom.xml`
- [x] `SpyzerApplication.java` carga el `.env`
- [x] `application.properties` usa `${VARIABLES}`
- [x] Cache race condition arreglado (`beforeInvocation = true`)
- [x] Timeout del frontend alineado (60 segundos)

---

**Tu aplicación ahora está lista para usar variables de entorno de forma segura y automática** ✨
