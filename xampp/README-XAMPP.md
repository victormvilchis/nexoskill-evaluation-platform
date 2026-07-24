# Levantar el proyecto con XAMPP y Spring Boot

## Distribución de responsabilidades

```text
Navegador
   |
   v
Apache de XAMPP :80
   |-- /evaluaciones/  -> frontend estático React
   `-- /api/           -> proxy hacia Spring Boot :8080
                                  |
                                  `-> Oracle Database :1521
```

XAMPP no ejecuta Java ni instala Oracle. Apache sirve el frontend y actúa como
proxy. Spring Boot se ejecuta como un proceso independiente.

## 1. Habilitar módulos de Apache

Abre:

```text
C:\xampp\apache\conf\httpd.conf
```

Verifica que estas líneas no tengan `#` al inicio:

```apache
LoadModule rewrite_module modules/mod_rewrite.so
LoadModule proxy_module modules/mod_proxy.so
LoadModule proxy_http_module modules/mod_proxy_http.so
LoadModule headers_module modules/mod_headers.so
```

## 2. Agregar el proxy hacia Spring Boot

Al final de `httpd.conf`, agrega el contenido de:

```text
xampp/apache/evaluation-platform.conf
```

También puedes incluirlo así:

```apache
Include "conf/extra/evaluation-platform.conf"
```

y copiar el archivo a:

```text
C:\xampp\apache\conf\extra\evaluation-platform.conf
```

## 3. Permitir `.htaccess`

En la sección correspondiente a `htdocs`, utiliza:

```apache
<Directory "C:/xampp/htdocs">
    Options Indexes FollowSymLinks Includes ExecCGI
    AllowOverride All
    Require all granted
</Directory>
```

Esto permite que React resuelva rutas como `/evaluaciones/dashboard`.

## 4. Compilar y copiar el frontend

Desde la carpeta `frontend`:

```powershell
npm install
npm run build
```

Copia el contenido de `frontend\dist` a:

```text
C:\xampp\htdocs\evaluaciones
```

O ejecuta desde la raíz:

```powershell
.\scripts\deploy-frontend-xampp.ps1
```

## 5. Levantar el backend

Desde `backend`:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

También puedes compilar y ejecutar:

```powershell
mvn clean package
java -jar target\evaluation-platform-backend-0.1.0.jar --spring.profiles.active=local
```

## 6. Reiniciar Apache

En el panel de XAMPP:

1. Detén Apache.
2. Inicia Apache nuevamente.
3. Revisa los logs si Apache no inicia:

```text
C:\xampp\apache\logs\error.log
```

## 7. Abrir la aplicación

```text
http://localhost/evaluaciones/
```

## Pruebas rápidas

Estado del backend a través de Apache:

```text
http://localhost/actuator/health
```

La API no debe abrir el panel directamente. Para probar login utiliza la
aplicación web.

## Errores comunes

### Apache no inicia

Normalmente existe un error de sintaxis o el puerto 80 está ocupado. Ejecuta:

```powershell
C:\xampp\apache\bin\httpd.exe -t
```

### La aplicación muestra 404 al actualizar

Confirma que:

- `mod_rewrite` está habilitado.
- `AllowOverride All` está configurado.
- `.htaccess` existe en `htdocs\evaluaciones`.

### Error 503 en `/api`

El backend Spring Boot no está ejecutándose en el puerto 8080.

### Error de conexión Oracle

XAMPP no administra Oracle. Confirma:

- Oracle está iniciado.
- El listener escucha en el puerto 1521.
- El PDB está abierto.
- La URL JDBC y credenciales son correctas.
