package app;

import static spark.Spark.*;
import java.sql.*;
import java.util.ArrayList;

public class app {
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
            if (rol == null) return "<h1>Error</h1><a href='/login.html'>Volver</a>";
            req.session().attribute("user", user);
            req.session().attribute("rol", rol);
            res.redirect(rol.equalsIgnoreCase("admin") ? "/admin-dashboard" : "/index.html");
            return null;
        });

        post("/registrar-usuario", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "INSERT INTO USUARIOS (nombre, usuario, `contraseña`, telefono, dni, ciudad, rol) VALUES (?, ?, ?, ?, ?, ?, 'cliente')";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, req.queryParams("nombre")); ps.setString(2, req.queryParams("usuario"));
                ps.setString(3, req.queryParams("contrasena")); ps.setInt(4, Integer.parseInt(req.queryParams("telefono")));
                ps.setString(5, req.queryParams("dni")); ps.setString(6, req.queryParams("ciudad"));
                ps.executeUpdate();
                res.redirect("/index.html"); return null;
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        });

      
        get("/admin-dashboard", (req, res) -> {
            if (!"admin".equals(req.session().attribute("rol"))) { res.redirect("/login.html"); return null; }
            
            StringBuilder h = new StringBuilder("<html><head><style>");
            h.append("body{font-family:Arial; padding:20px; background:#f4f7f6;}");
            h.append("table{width:100%; border-collapse:collapse; background:white;}");
            h.append("th,td{padding:10px; border:1px solid #ddd; text-align:left;}");
            h.append("th{background:#2c3e50; color:white;} .sec{background:#3498db; color:white; font-weight:bold;}");
            h.append("</style></head><body>");
            h.append("<h1>Panel de Auditoría: 31 Consultas Ejecutadas</h1>");
            h.append("<table><tr><th>ID</th><th>Enunciado</th><th>Resultado en Tiempo Real</th></tr>");

            try {
            
                h.append("<tr class='sec'><td colspan='3'>ESTADÍSTICAS DE NEGOCIO</td></tr>");
                h.append("<tr><td>N1</td><td>Ingresos totales hoy</td><td>").append(qV("SELECT SUM(T.precio) FROM RESERVA R JOIN HABITACIONES HAB ON R.id_reserva = HAB.id_reserva JOIN TIPOS T ON HAB.tipo = T.tipo WHERE R.fecha_reserva = CURDATE()")).append(" €</td></tr>");
                h.append("<tr><td>N2</td><td>Habitaciones libres</td><td>").append(qL("SELECT tipo FROM TIPOS WHERE tipo NOT IN (SELECT tipo FROM HABITACIONES HAB JOIN RESERVA R ON HAB.id_reserva = R.id_reserva WHERE R.fecha_reserva = CURDATE())")).append("</td></tr>");
                h.append("<tr><td>N3</td><td>Ingresos año 2026</td><td>").append(qV("SELECT SUM(T.precio) FROM RESERVA R JOIN HABITACIONES HAB ON R.id_reserva = HAB.id_reserva JOIN TIPOS T ON HAB.tipo = T.tipo WHERE R.fecha_reserva BETWEEN '2026-01-01' AND '2026-12-31'")).append(" €</td></tr>");
                h.append("<tr><td>N4</td><td>% Ocupación hoy</td><td>").append(qV("SELECT (COUNT(*) / 20.0) * 100 FROM HABITACIONES HAB JOIN RESERVA R ON HAB.id_reserva = R.id_reserva WHERE R.fecha_reserva = CURDATE()")).append(" %</td></tr>");
                h.append("<tr><td>N5</td><td>Clientes en restaurante</td><td>").append(qV("SELECT COUNT(DISTINCT id_usuario) FROM RESERVA R JOIN RESTAURANTE REST ON R.id_reserva = REST.id_reserva")).append("</td></tr>");
                h.append("<tr><td>N6</td><td>Media uso restaurante</td><td>").append(qV("SELECT (SELECT COUNT(*) FROM RESTAURANTE) / (SELECT COUNT(DISTINCT id_usuario) FROM RESERVA R JOIN RESTAURANTE REST ON R.id_reserva = REST.id_reserva)")).append(" veces</td></tr>");

              
                h.append("<tr class='sec'><td colspan='3'>CONSULTAS DE SISTEMA (Q1 - Q25)</td></tr>");
                h.append("<tr><td>Q1</td><td>Suma precios habitaciones</td><td>").append(qV("SELECT SUM(precio) FROM TIPOS")).append("</td></tr>");
                h.append("<tr><td>Q2</td><td>Lista clientes</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE rol='cliente'")).append("</td></tr>");
                h.append("<tr><td>Q3</td><td>Todos los usuarios</td><td>").append(qL("SELECT nombre FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q4</td><td>Sede Donostia</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE ciudad='Donostia'")).append("</td></tr>");
                h.append("<tr><td>Q5</td><td>Orden Alfabético</td><td>").append(qL("SELECT nombre FROM USUARIOS ORDER BY nombre ASC")).append("</td></tr>");
                h.append("<tr><td>Q6</td><td>Total de registros</td><td>").append(qV("SELECT COUNT(*) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q7</td><td>Búsqueda DNI (12345678Z)</td><td>").append(qV("SELECT nombre FROM USUARIOS WHERE dni='12345678Z'")).append("</td></tr>");
                h.append("<tr><td>Q8</td><td>Ciudades distintas</td><td>").append(qL("SELECT DISTINCT ciudad FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q9</td><td>Empiezan por 'A'</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE nombre LIKE 'A%'")).append("</td></tr>");
                h.append("<tr><td>Q10</td><td>Administradores</td><td>").append(qL("SELECT usuario FROM USUARIOS WHERE rol='admin'")).append("</td></tr>");
                h.append("<tr><td>Q11</td><td>Sin teléfono</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE telefono IS NULL")).append("</td></tr>");
                h.append("<tr><td>Q12</td><td>Top 5 usuarios</td><td>").append(qL("SELECT nombre FROM USUARIOS LIMIT 5")).append("</td></tr>");
                h.append("<tr><td>Q13</td><td>ID más alto</td><td>").append(qV("SELECT MAX(id_usuario) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q14</td><td>Bilbao o Vitoria</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE ciudad IN ('Bilbao', 'Vitoria')")).append("</td></tr>");
                h.append("<tr><td>Q15</td><td>Nombres en Mayúsculas</td><td>").append(qL("SELECT UPPER(nombre) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q16</td><td>Conteo de ciudades</td><td>").append(qV("SELECT COUNT(DISTINCT ciudad) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q17</td><td>DNI termina en 'Z'</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE dni LIKE '%Z'")).append("</td></tr>");
                h.append("<tr><td>Q18</td><td>ID más bajo</td><td>").append(qV("SELECT MIN(id_usuario) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q19</td><td>Nombres largos (>10)</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE LENGTH(nombre) > 10")).append("</td></tr>");
                h.append("<tr><td>Q20</td><td>Usuario y Ciudad</td><td>").append(qL("SELECT CONCAT(usuario, ' - ', ciudad) FROM USUARIOS")).append("</td></tr>");
                h.append("<tr><td>Q21</td><td>No son de Madrid</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE ciudad <> 'Madrid'")).append("</td></tr>");
                h.append("<tr><td>Q22</td><td>Tienen usuario</td><td>").append(qL("SELECT nombre FROM USUARIOS WHERE usuario IS NOT NULL")).append("</td></tr>");
                h.append("<tr><td>Q23</td><td>Acción: VIP</td><td>").append(qA("UPDATE USUARIOS SET rol='vip' WHERE usuario='admin'")).append("</td></tr>");
                h.append("<tr><td>Q24</td><td>Acción: Borrar Test</td><td>").append(qA("DELETE FROM USUARIOS WHERE nombre='test'")).append("</td></tr>");
                h.append("<tr><td>Q25</td><td>Media IDs</td><td>").append(qV("SELECT AVG(id_usuario) FROM USUARIOS")).append("</td></tr>");

            } catch (Exception e) { h.append("<tr><td colspan='3'>Error: ").append(e.getMessage()).append("</td></tr>"); }

            h.append("</table><br><a href='/login.html'>Cerrar Sesión</a></body></html>");
            return h.toString();
        });
    }

    // --- MÉTODOS DE APOYO ---
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
        } return r.isEmpty() ? "Vacío" : r.toString();
    }

    private static String qV(String s) throws Exception {
        try(Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            ResultSet rs = c.createStatement().executeQuery(s)){
            return rs.next() ? (rs.getString(1) != null ? rs.getString(1) : "0") : "0";
        }
    }

    private static String qA(String s) throws Exception {
        try(Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)){
            return "Filas afectadas: " + c.createStatement().executeUpdate(s);
        }
    }
}
// http://localhost:4567/