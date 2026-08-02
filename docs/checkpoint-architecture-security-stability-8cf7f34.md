# Checkpoint integral de arquitectura, seguridad, calidad y estabilidad

## Base revisada

- Repositorio interno: `nexoskill-evaluation-platform`
- Rama de trabajo: `feature/multitenancy-core`
- Commit base: `8cf7f34f7944b1fba3dbf00d1d87dd574586a446`
- Fecha del checkpoint: 2026-08-01

Este documento registra únicamente verificaciones efectuadas sobre el árbol del commit base y las correcciones incluidas en esta actualización. No declara invulnerabilidad ni sustituye las pruebas integrales en Oracle y Maven del entorno local.

## Componentes revisados

### Backend

- Arquitectura y reglas ArchUnit.
- Autenticación de usuarios internos y colaboradores.
- Cookies, sesiones, expiración, cierre de sesión, suspensión, baja y cambio de contraseña.
- Resolución de `TenantContext`, contexto GLOBAL y aislamiento por organización.
- Usuarios internos, organizaciones, permisos y licenciamiento configurado.
- Colaboradores, administración, eliminación lógica, sesiones y acceso.
- Certificaciones, ciclos, intentos, recertificaciones e históricos.
- Importación XLSX, filas válidas, vista previa, conflictos, confirmación y reimportación.
- Banco de preguntas, categorías, tecnologías, etiquetas, colecciones y archivos multimedia.
- Formularios, colecciones de aprendizaje y gobierno de contenido global.
- Paginación, búsquedas, filtros, ordenamientos y conteos.
- Persistencia JPA/JDBC, restricciones, bloqueos y transacciones.
- Manejo uniforme de errores, auditoría y configuración de producción.

### Frontend

- Rutas protegidas y permisos.
- Acceso interno y acceso de colaboradores.
- Organizaciones, usuarios, colaboradores, certificaciones e importaciones.
- Preguntas, categorías, colecciones, formularios y resultados visibles.
- Paginación, filtros, ordenamiento, bloqueo de doble envío y manejo de errores.
- Modales y notificaciones integradas.
- Identidad visible de Valtieris Talent Platform.

## Resumen de hallazgos y correcciones

| ID | Prioridad | Hallazgo | Corrección | Estado |
|---|---|---|---|---|
| CP-01 | Alta | La acción de eliminación de colaboradores ejecutaba borrado físico de certificaciones, intentos, resultados, sesiones e históricos. | La acción conserva el endpoint por compatibilidad, pero ahora realiza eliminación lógica, revoca sesiones y mantiene toda la información histórica. | Corregido en código y cubierto por prueba unitaria. |
| CP-02 | Alta | Los archivos multimedia de preguntas podían consultarse por identificador sin validar organización ni disponibilidad del contenido que los utilizaba. | Se incorporó propiedad `CONTENT_SCOPE`/`OWNER_ORGANIZATION_ID`, política central de acceso, validación al adjuntar y lectura con semántica de recurso no encontrado fuera del alcance autorizado. | Corregido en código y cubierto por pruebas de aislamiento. |
| CP-03 | Alta | La exclusión mutua de la confirmación de importaciones dependía únicamente de memoria local; un reinicio o varios nodos podían permitir una segunda confirmación. | Se agregó reserva persistente por token de confirmación, estados `PROCESSING`, `COMPLETED` y `FAILED`, y cierre idempotente en transacciones independientes. | Corregido en código, persistencia y pruebas. |
| CP-04 | Alta | El rol `ADMINISTRATOR` conservaba permisos y caminos de servicio para administrar colaboradores comerciales, contradiciendo la separación de GLOBAL. | Se revocan permisos `STUDENT_%`, se invalidan sesiones activas del rol y los servicios rechazan GLOBAL aunque el cliente manipule identificadores. | Corregido en backend, frontend y migración. |
| CP-05 | Alta | La numeración de intentos usaba `MAX(ATTEMPT_NUMBER)+1` sin serialización por ciclo. Dos solicitudes concurrentes podían calcular el mismo número. | Se bloquea el ciclo con `FOR UPDATE` antes de calcular el siguiente intento y se agrega un índice único por ciclo/número. | Corregido en servicio y persistencia. |
| CP-06 | Alta | La configuración de producción podía depender de valores de desarrollo para base de datos y orígenes permitidos. | El perfil `prod` exige variables de entorno para URL, usuario, contraseña y orígenes; mantiene cookies seguras y seeding deshabilitado. | Corregido en configuración. |
| CP-07 | Media | La revisión transversal de contenido global cargaba todos los registros y paginaba en memoria; además utilizaba tamaño predeterminado 20. | La consulta ahora filtra, cuenta, ordena y pagina en Oracle; el contrato se homologa a 10/25/50/100. | Corregido en puerto, servicio, persistencia y controlador. |
| CP-08 | Media | El listado heredado de colecciones de preguntas aceptaba tamaños arbitrarios y comenzaba en 20. | Se aplica el validador homogéneo y tamaño predeterminado 10. | Corregido. |
| CP-09 | Media | El manejador global registraba mensajes completos de excepciones de JSON, multipart y acceso denegado; estos mensajes pueden incorporar fragmentos de entrada. | Los registros conservan ruta y clase raíz del error, sin registrar el contenido recibido. | Corregido. |
| CP-10 | Media | La eliminación de categorías borraba físicamente el registro y su historial de estados aunque el modelo ya soportaba `DELETED`. | Las categorías se eliminan lógicamente y conservan su historial. | Corregido. |

No se identificaron hallazgos clasificados como críticos en la revisión estática. Los hallazgos altos anteriores fueron corregidos en el código entregado; su validación integral de ejecución debe completarse con los comandos Maven, Flyway y frontend indicados para el entorno local.

## Seguridad y aislamiento

### Autenticación y sesiones

- Los tokens de sesión se generan con aleatoriedad criptográfica y se persisten mediante hash.
- Las contraseñas utilizan BCrypt con costo 12.
- Las cookies son `HttpOnly`, `SameSite=Strict` y `Secure` en producción.
- Los filtros vuelven a comprobar sesión, usuario, organización, membresía, estado y vigencia en cada solicitud.
- Cierre de sesión, cambio/restablecimiento de contraseña, suspensión, baja e inactivación revocan el acceso correspondiente.
- La respuesta al cliente no expone tokens, hashes, consultas ni trazas internas.

### Autorización

Se revisaron 139 métodos HTTP mapeados. 135 tienen autorización a nivel de método. Los cuatro restantes corresponden a:

- Inicio de sesión interno, público por diseño.
- Inicio de sesión de colaboradores, público por diseño.
- Cierre de sesión, cubierto por la regla global `authenticated()`.
- Consulta del contexto organizacional, cubierta por la regla global `authenticated()`.

Las operaciones sensibles conservan validación en backend aunque la acción no esté visible en frontend.

### Aislamiento organizacional

- Los servicios de colaboradores derivan la organización del `TenantContext` autenticado.
- GLOBAL y el Administrador global no pueden administrar colaboradores comerciales.
- Las consultas, altas, actualizaciones, bajas, importaciones y certificaciones verifican la organización en backend.
- Las decisiones de importación y sus comprobantes incluyen organización y actor.
- Las imágenes de preguntas se autorizan por alcance, propietario y disponibilidad de la pregunta.
- Los accesos fuera del tenant autorizado utilizan semántica de recurso inexistente para no revelar información.

## Persistencia, transacciones e idempotencia

- Las entidades mutables principales utilizan versión optimista o bloqueos explícitos.
- Las actualizaciones sensibles de colaboradores, organizaciones, preguntas y colecciones bloquean o verifican versión.
- La numeración de intentos queda serializada por ciclo y protegida por índice único.
- La creación de códigos de colaborador incluye el identificador persistido y mantiene restricción única en base de datos.
- Correos, códigos organizacionales y usuarios corporativos tienen validación funcional y restricciones persistentes.
- La confirmación de importaciones queda reservada persistentemente antes de aplicar filas.
- La importación mantiene atomicidad por fila de forma deliberada: una fila inválida no revierte filas válidas, conforme a la regla funcional vigente. El comprobante final registra totales y errores.
- La distribución de contenido global mantiene transacciones independientes por organización destino y conserva resultados individuales.
- Eliminaciones de organizaciones, usuarios, colaboradores, preguntas y categorías con historial se manejan lógicamente.

## Importación de colaboradores

Se revisó el flujo completo:

- Validación de archivo XLSX y límites de tamaño/filas/columnas.
- Protección contra rutas ZIP relativas, DTD y entidades externas.
- Procesamiento de la primera hoja y detección de encabezados.
- Exclusión de filas sin `NOMBRE EXTERNO`.
- Normalización de nombres, fechas, estados y valores numéricos.
- Clasificación de nuevos, cambios, bajas, conflictos, advertencias y errores.
- Captura manual de correo, código manual y usuario corporativo para altas nuevas.
- Conservación de identificadores manuales de colaboradores existentes.
- Reutilización de decisiones de conflicto mediante firma exacta.
- Reserva persistente de confirmación para impedir doble aplicación.
- Aplicación aislada por fila y resultado final controlado.

## Inyección y contenido no confiable

- Las consultas revisadas enlazan valores mediante parámetros JPA/JDBC.
- Los fragmentos SQL dinámicos provienen de enumeraciones, tablas internas o mapas cerrados, no de texto enviado libremente.
- Los campos de ordenamiento aceptan únicamente criterios permitidos.
- React presenta texto mediante escape predeterminado; no se encontraron usos de `dangerouslySetInnerHTML`, asignaciones a `innerHTML` ni `eval`.
- No se encontraron llamadas a `alert`, `confirm` o `prompt` nativos en el frontend.
- Los nombres de archivos multimedia se neutralizan y las rutas de almacenamiento se normalizan y validan contra el directorio raíz.

## Paginación y rendimiento

- Los listados principales utilizan paginación en backend con tamaños 10, 25, 50 y 100.
- La revisión transversal de contenido global dejó de cargar el catálogo completo y ahora pagina en Oracle.
- Los ordenamientos se traducen mediante listas permitidas y mantienen un criterio estable.
- Los conteos organizacionales se realizan con consultas agregadas y sin mezclar tenants.
- Se mantienen pendientes de una futura homologación independiente dos tablas secundarias que reciben listas completas y paginan en cliente: historial/sesiones dentro de la administración individual de usuarios y la tabla de valores de catálogos. No constituyen un hallazgo alto, pero pueden crecer y deberán migrarse a contratos paginados de backend antes de manejar volúmenes elevados.

## Riesgos y limitaciones pendientes

| Prioridad | Riesgo pendiente | Tratamiento recomendado |
|---|---|---|
| Media | La vista previa de importación permanece en memoria y se pierde al reiniciar el backend. El comprobante persistente evita doble confirmación, pero una vista no confirmada debe regenerarse. | Persistir la vista previa completa solo si en una fase futura se requiere continuidad después de reinicios. |
| Media | Historial y sesiones del detalle de usuario, y valores de catálogos, todavía se paginan en cliente. | Crear endpoints paginados específicos en una entrega posterior, sin alterar esta corrección de seguridad. |
| Baja | Los scripts locales y `application.yml` conservan credenciales predeterminadas exclusivamente para el entorno local documentado. | Mantenerlos fuera de producción; el perfil `prod` ya exige secretos externos. |
| Operativa | No se ejecutó un escaneo de vulnerabilidades de dependencias con fuentes remotas en este entorno sin acceso de red. | Ejecutar OWASP Dependency-Check/SCA en CI con acceso actualizado a bases de vulnerabilidades. |

## Evidencia de validación ejecutada en esta entrega

- Compilación de las fuentes Java principales con Java 21 y el classpath real extraído del backend: **satisfactoria**.
- Comprobación TypeScript mediante `tsc -b`: **satisfactoria**.
- Revisión estática de autorización de controladores: **139 rutas mapeadas; 135 con `@PreAuthorize`; 4 cubiertas por reglas públicas/autenticadas explícitas**.
- Búsqueda de diálogos nativos y sumideros XSS en frontend: **sin coincidencias funcionales**.
- Búsqueda de secretos: solo credenciales locales conocidas; el perfil de producción quedó endurecido.
- Revisión de diferencias con `git diff --check`: debe ejecutarse nuevamente al cerrar el paquete y queda registrada en la validación estructural del instalador.

### Pruebas agregadas o actualizadas

- Eliminación lógica de colaboradores y preservación de históricos.
- Rechazo del contexto GLOBAL en colaboradores e importaciones.
- Reserva, cierre y consulta tenant-aware del comprobante de importación.
- Aislamiento de archivos multimedia entre organizaciones.
- Validación del contrato homogéneo y delegación de paginación de contenido global.
- Regresiones de servicio y controlador de colaboradores adaptadas al tenant organizacional.

### Validaciones que deben ejecutarse en el entorno local

Este entorno de construcción no dispone de Maven ni de una instancia Oracle accesible. Por ello no se declara como ejecutado:

```text
mvn clean test
mvn clean package -DskipTests
inicio completo con Flyway V052 sobre Oracle 21c
npm.cmd run build
```

El chequeo TypeScript sí se ejecutó. El empaquetado Vite completo debe repetirse en Windows con las dependencias nativas instaladas por `npm.cmd`.

## Conclusión

La actualización corrige los hallazgos altos identificados de integridad histórica, aislamiento organizacional, idempotencia de importación, permisos GLOBAL, concurrencia de intentos y configuración de producción. También homologa paginación y reduce exposición de entradas en registros. No se agregan módulos ni reglas de negocio nuevas.

La base queda preparada para continuar una vez que las pruebas Maven, la migración Flyway y el build frontend concluyan satisfactoriamente en el entorno local indicado.
