# Validación realizada

- Archivos generados: 108
- `pom.xml` validado como XML.
- Archivos JSON validados.
- Rutas de paquetes Java verificadas contra sus declaraciones `package`.
- Balance estructural básico de fuentes Java revisado.
- Imports relativos de TypeScript verificados.
- Fuentes TypeScript/TSX analizadas con el parser de TypeScript sin errores
  sintácticos.
- No se ejecutó una compilación Maven completa ni una prueba contra Oracle
  porque el entorno de generación no dispone de Maven ni de una instancia
  Oracle accesible.

Antes de promover a producción se debe ejecutar:

```powershell
cd backend
mvn clean test

cd ..rontend
npm install
npm run build
```

y levantar el backend contra una instancia Oracle de desarrollo.
