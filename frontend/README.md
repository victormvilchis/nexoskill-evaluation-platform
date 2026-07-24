# Frontend

React + TypeScript + Vite.

## Desarrollo

```powershell
npm install
npm run dev
```

Abrir:

```text
http://localhost:5173/evaluaciones/
```

Vite redirige `/api` al backend en `http://127.0.0.1:8080`.

## Compilación para XAMPP

```powershell
npm run build
```

El resultado se genera en `dist/`. Copia todo su contenido a:

```text
C:\xampp\htdocs\evaluaciones
```

También puedes ejecutar:

```powershell
..\scripts\deploy-frontend-xampp.ps1
```

La aplicación se abrirá en:

```text
http://localhost/evaluaciones/
```
