package app;

import static spark.Spark.*;

import java.net.PasswordAuthentication;
import java.sql.*;
import java.util.ArrayList;


public class App {
    private static final String DB_URL = "jdbc:mysql://mysql-8001.dinaserver.com/txurimendi?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "txurimendi";
    private static final String DB_PASS = "Pmnu9Y,0.[41"; 

    public static void main(String[] args) {
        port(4567);
        staticFiles.location("/public");

        get("/", (req, res) -> { res.redirect("/login.html"); return null; });

        // --- 1. LOGIN Y REGISTRO ---
        post("/login", (req, res) -> {
            String user = req.queryParams("usuario");
            String pass = req.queryParams("contrasena");
            String rol = validarUsuario(user, pass);
            if (rol == null) return "<h1>Error de credenciales</h1><a href='/login.html'>Volver a intentar</a>";
            req.session().attribute("user", user);
            req.session().attribute("rol", rol);
            res.redirect(rol.equalsIgnoreCase("admin") ? "/admin-dashboard" : "/index.html");
            return null;
        });

        post("/registrar-usuario", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sqlUser = "INSERT INTO USUARIOS (nombre, usuario, contrasena, telefono, dni, ciudad, rol) VALUES (?, ?, ?, ?, ?, ?, 'cliente')";
                PreparedStatement ps1 = conn.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS);
                ps1.setString(1, req.queryParams("nombre"));
                ps1.setString(2, req.queryParams("usuario"));
                ps1.setString(3, req.queryParams("contrasena"));
                ps1.setInt(4, Integer.parseInt(req.queryParams("telefono")));
                ps1.setString(5, req.queryParams("dni"));
                ps1.setString(6, req.queryParams("ciudad"));
                ps1.executeUpdate();

 //                   ResultSet rs = ps1.getGeneratedKeys();
 //               if (rs.next()) {
 //                   int newId = rs.getInt(1);
 //                   String sqlCliente = "INSERT INTO CLIENTES (id_usuario) VALUES (?)";
 //                   PreparedStatement ps2 = conn.prepareStatement(sqlCliente);
 //                   ps2.setInt(1, newId);
 //                   ps2.executeUpdate();
 //               }
                res.redirect("/login.html"); 
                return null;
                
            } catch (Exception e) { return "Error en registro: " + e.getMessage(); }
        });

        // --- 2. RUTAS DE RESERVA ---
        post("/reservar-habitaciones", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String userActual = req.session().attribute("user");
                if (userActual == null) return "Error: Sesión expirada.";

                int idUsuario = obtenerIdUsuario(userActual);
                String sql = "INSERT INTO HABITACIONES (id_usuario, tipo, fecha_entrada, fecha_salida) VALUES (?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, idUsuario);

                ps.setString(2, req.queryParams("tipo"));
                ps.setString(3, req.queryParams("entrada"));
                ps.setString(4, req.queryParams("salida"));
                
                //String tipo = req.queryParams("tipo");            
                //ps.setString(2, tipo);
                //String fechaEntrada = req.queryParams("fecha_entrada");            
                //ps.setString(3, fechaEntrada);
                //String fechaSalida = req.queryParams("fecha_salida");            
                //ps.setString(4, fechaSalida);
                //LO QUE TENIA
                //ps.setString(3, req.queryParams("fecha_entrada"));
                //ps.setString(4, req.queryParams("fecha_salida"));
                ps.executeUpdate();

                res.redirect("/index.html");
                return null;
            } catch (Exception e) { return "Error hotel: " + e.getMessage(); }
        });

        post("/reservar-mesa", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String userActual = req.session().attribute("user");
                if (userActual == null) return "Error: Sesión expirada.";

                int idUsuario = obtenerIdUsuario(userActual);
                int nComensales = ("mas".equals(req.queryParams("comensales"))) ? 
                                  Integer.parseInt(req.queryParams("cantidad_exacta")) : 
                                  Integer.parseInt(req.queryParams("comensales"));
                
                String zona = req.queryParams("zona");
                if (zona == null) zona = "Salón Principal";

                String sql = "INSERT INTO RESTAURANTE (id_usuario, fecha_reserva, hora_inicio, comensales, zona) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, idUsuario);
                ps.setString(2, req.queryParams("dia"));
                ps.setString(3, req.queryParams("hora"));
                ps.setInt(4, nComensales);
                ps.setString(5, zona);
                ps.executeUpdate();

                res.redirect("/index.html");
                return null;
            } catch (Exception e) { return "Error mesa: " + e.getMessage(); }
        });
        
        post("/contactar", (req, res) -> {
            // 1. Usamos "user", que es como lo guardas en tu login
            String userActual = req.session().attribute("user"); 
            
            // 2. Buscamos el ID real usando tu método auxiliar
            int idUsuarioFinal = 0;
            if (userActual != null) {
                idUsuarioFinal = obtenerIdUsuario(userActual);
            }
            
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String mensaje = req.queryParams("mensaje");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "INSERT INTO CONTACTO (id_usuario, nombre, email, mensaje) VALUES (?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                
                // Si el usuario existe lo ponemos, si no, ponemos NULL (tu BD lo permite)
                if (idUsuarioFinal > 0) {
                    ps.setInt(1, idUsuarioFinal);
                } else {
                    ps.setNull(1, java.sql.Types.INTEGER);
                }
                
                ps.setString(2, nombre);
                ps.setString(3, email);
                ps.setString(4, mensaje);
                ps.executeUpdate();
                
                return "<h1>Mensaje enviado</h1><p>Gracias " + nombre + ", te responderemos pronto.</p><a href='/index.html'>Volver</a>";
            } catch (Exception e) {
                e.printStackTrace();
                return "Error al guardar el mensaje: " + e.getMessage();
            }
        });


        // --- 3. PANEL DE ADMINISTRACIÓN (DASHBOARD AMPLIADO) ---
        get("/admin-dashboard", (req, res) -> {
            if (!"admin".equals(req.session().attribute("rol"))) { res.redirect("/login.html"); return null; }
            
            StringBuilder h = new StringBuilder("<html><head><style>");
            h.append("body{font-family:'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding:40px; background:#f0f2f5; color:#333;}");
            h.append("table{width:100%; border-collapse:collapse; background:white; border-radius:8px; overflow:hidden; box-shadow:0 4px 6px rgba(0,0,0,0.1);}");
            h.append("th,td{padding:15px; border-bottom:1px solid #eee; text-align:left;}");
            h.append("th{background:#2c3e50; color:white; text-transform:uppercase; font-size:14px;}");
            h.append(".sec{background:#3498db; color:white; font-weight:bold; font-size:16px;}");
            h.append("b{color:#2980b9;}");
            h.append("</style></head><body>");
            h.append("<h1>Panel de Auditoría Avanzada - Gestión Txurimendi</h1>");
            h.append("<table><tr><th>ID</th><th>Descripción Detallada del Enunciado</th><th>Resultado en Tiempo Real</th></tr>");

            try {
                // SECCIÓN N
                h.append("<tr class='sec'><td colspan='3'>MÉTRICAS CLAVE DE RENDIMIENTO (KPIs)</td></tr>");
                h.append("<tr><td>N1</td><td><b>Volumen de ventas hoy:</b> Ingresos brutos generados por las habitaciones con entrada en la fecha actual</td><td>").append(qV("SELECT SUM(T.precio) FROM HABITACIONES H JOIN TIPOS T ON H.tipo = T.tipo WHERE H.fecha_entrada = CURDATE()")).append(" €</td></tr>");
                h.append("<tr><td>N2</td><td><b>Disponibilidad:</b> Tipos de habitaciones que no cuentan con ninguna reserva activa para el día de hoy</td><td>").append(qL("SELECT tipo FROM TIPOS WHERE tipo NOT IN (SELECT tipo FROM HABITACIONES WHERE fecha_entrada = CURDATE())")).append("</td></tr>");
                h.append("<tr><td>N3</td><td><b>Proyección Anual 2026:</b> Estimación total de ingresos por alojamiento para el presente año fiscal</td><td>").append(qV("SELECT SUM(T.precio) FROM HABITACIONES H JOIN TIPOS T ON H.tipo = T.tipo WHERE H.fecha_entrada LIKE '2026%'")).append(" €</td></tr>");
                h.append("<tr><td>N4</td><td><b>Tasa de Ocupación:</b> Porcentaje de uso del inventario de habitaciones (sobre un total de 20 unidades)</td><td>").append(qV("SELECT (COUNT(*)/20.0)*100 FROM HABITACIONES WHERE fecha_entrada = CURDATE()")).append(" % de ocupación</td></tr>");
                h.append("<tr><td>N5</td><td><b>Afluencia Restaurante:</b> Cantidad de clientes distintos que han interactuado con el servicio de comidas</td><td>").append(qV("SELECT COUNT(DISTINCT id_usuario) FROM RESTAURANTE")).append(" Clientes registrados</td></tr>");
                h.append("<tr><td>N6</td><td><b>Fidelidad:</b> Media de reservas de mesa realizadas por cada cliente registrado en el sistema</td><td>").append(qV("SELECT COUNT(*)/COUNT(DISTINCT id_usuario) FROM RESTAURANTE")).append(" Reservas por cliente</td></tr>");

                // SECCIÓN Q (25 Consultas de Auditoría)
                h.append("<tr class='sec'><td colspan='3'>AUDITORÍA DE REGISTROS Y SISTEMA (Q1 - Q25)</td></tr>");
                h.append("<tr><td>Q1</td><td><b>Valor del Catálogo:</b> Suma de los precios base de todas las categorías de habitaciones</td><td>").append(qV("SELECT SUM(precio) FROM TIPOS")).append(" €</td></tr>");
                h.append("<tr><td>Q2</td><td><b>Segmento Clientes:</b> Listado de nombres de usuarios registrados con el rol de cliente</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE rol='cliente'")).append("</td></tr>");
                h.append("<tr><td>Q3</td><td><b>Censo Total:</b> Listado de todas las personas físicas dadas de alta en la plataforma</td><td>").append(qL("SELECT nombre FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q4</td><td><b>Geolocalización:</b> Clientes que han declarado su residencia en la ciudad de Madrid</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE ciudad='Madrid'")).append("</td></tr>");
                h.append("<tr><td>Q5</td><td><b>Verificación DNI:</b> Documento de identidad del usuario registrado bajo el nombre de 'Juan'</td><td>").append(qV("SELECT dni FROM USUARIOS WHERE nombre='Juan'")).append("</td></tr>");
                h.append("<tr><td>Q8</td><td><b>Diversidad Territorial:</b> Ciudades de origen de nuestros huéspedes sin duplicados</td><td>").append(qL("SELECT DISTINCT ciudad FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q12</td><td><b>Densidad de Mesa:</b> Promedio de personas por reserva en el área de restauración</td><td>").append(qV("SELECT AVG(comensales) FROM RESTAURANTE")).append(" Personas</td></tr>");
                h.append("<tr><td>Q13</td><td><b>Índice de Registro:</b> Identificador numérico más reciente asignado a un nuevo usuario</td><td>").append(qV("SELECT MAX(id_usuario) FROM USUARIOS")).append(" (Último ID)</td></tr>");
                h.append("<tr><td>Q19</td><td><b>Volumen de Cubiertos:</b> Suma total de comensales atendidos en la historia del restaurante</td><td>").append(qV("SELECT SUM(comensales) FROM RESTAURANTE")).append(" Comensales</td></tr>");
                h.append("<tr><td>Q25</td><td><b>Métrica de Control:</b> Valor promedio de los identificadores de usuario del sistema</td><td>").append(qV("SELECT AVG(id_usuario) FROM USUARIOS")).append(" (Media técnica)</td></tr>");

                // SECCIÓN A (Inteligencia de Negocio)
                h.append("<tr class='sec'><td colspan='3'>INTELIGENCIA DE NEGOCIO Y ANÁLISIS DE MERCADO</td></tr>");
                h.append("<tr><td>A1</td><td><b>Máximo Inversor:</b> Nombre del cliente que ha generado la mayor facturación total por alojamiento</td><td>").append(qV("SELECT U.nombre FROM USUARIOS U JOIN HABITACIONES H ON U.id_usuario = H.id_usuario JOIN TIPOS T ON H.tipo = T.tipo GROUP BY U.id_usuario ORDER BY SUM(T.precio) DESC LIMIT 1")).append(" (VIP)</td></tr>");
                h.append("<tr><td>A2</td><td><b>Producto Estrella:</b> Tipo de habitación con mayor número de reservas acumuladas</td><td>").append(qV("SELECT tipo FROM HABITACIONES GROUP BY tipo ORDER BY COUNT(*) DESC LIMIT 1")).append("</td></tr>");
                h.append("<tr><td>A3</td><td><b>Mercado Principal:</b> Ciudad de origen que reporta el mayor beneficio económico al hotel</td><td>").append(qV("SELECT U.ciudad FROM USUARIOS U JOIN HABITACIONES H ON U.id_usuario = H.id_usuario JOIN TIPOS T ON H.tipo = T.tipo GROUP BY U.ciudad ORDER BY SUM(T.precio) DESC LIMIT 1")).append("</td></tr>");
                h.append("<tr><td>A4</td><td><b>Pico de Demanda:</b> Fecha histórica con mayor volumen de reservas en el restaurante</td><td>").append(qV("SELECT fecha_reserva FROM RESTAURANTE GROUP BY fecha_reserva ORDER BY COUNT(*) DESC LIMIT 1")).append("</td></tr>");
                h.append("<tr><td>A5</td><td><b>Zona VIP:</b> Área del restaurante con mayor afluencia de público acumulada</td><td>").append(qV("SELECT zona FROM RESTAURANTE GROUP BY zona ORDER BY SUM(comensales) DESC LIMIT 1")).append("</td></tr>");
                h.append("<tr><td>A6</td><td><b>Ticket Medio:</b> Gasto promedio estimado de un cliente por cada estancia reservada</td><td>").append(qV("SELECT AVG(total) FROM (SELECT SUM(T.precio) as total FROM HABITACIONES H JOIN TIPOS T ON H.tipo = T.tipo GROUP BY H.id_usuario) as sub")).append(" € por estancia</td></tr>");
                h.append("<tr><td>C1</td><td><b>Buzón de Sugerencias:</b> Últimos mensajes recibidos desde el formulario de contacto</td><td>").append(qL("SELECT CONCAT(nombre, ': ', mensaje) FROM CONTACTO ORDER BY fecha DESC LIMIT 3")).append("</td></tr>");

            } catch (Exception e) { 
                h.append("<tr><td colspan='3' style='color:red;'><b>Error de ejecución:</b> ").append(e.getMessage()).append("</td></tr>"); 
            }

            h.append("</table><br><a href='/index.html' style='text-decoration:none; color:#2c3e50; font-weight:bold;'>← Volver al Panel de Control</a></body></html>");
            return h.toString();
        });
    }

    // --- MÉTODOS AUXILIARES ---
    private static int obtenerIdUsuario(String usuario) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement("SELECT id_usuario FROM USUARIOS WHERE usuario = ?")) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id_usuario") : 0;
        }
    }

    private static String validarUsuario(String u, String p) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement("SELECT rol FROM USUARIOS WHERE usuario=? AND contrasena=?")) {
            ps.setString(1, u); ps.setString(2, p);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("rol") : null;
        } catch (Exception e) { return null; }
    }

    private static String qL(String s) throws Exception {
        ArrayList<String> r = new ArrayList<>();
        try(Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            ResultSet rs = c.createStatement().executeQuery(s)){
            while(rs.next()) r.add(rs.getString(1));
        } return r.isEmpty() ? "<i>Sin registros</i>" : r.toString();
    }

    private static String qV(String s) throws Exception {
        try(Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            ResultSet rs = c.createStatement().executeQuery(s)){
            return rs.next() ? (rs.getString(1) != null ? rs.getString(1) : "0") : "0";
        }
    }
}

// http://localhost:4567/