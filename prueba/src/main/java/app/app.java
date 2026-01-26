package app;

import static spark.Spark.*;
import java.sql.*;

public class app {
    private static final String DB_URL = "jdbc:mysql://mysql-8001.dinaserver.com/txurimendi?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "txurimendi";
    private static final String DB_PASS = "Pmnu9Y,0.[41"; 

    public static void main(String[] args) {
        port(4567);
        staticFiles.location("/public");

        // --- LOGIN ---
        post("/login", (req, res) -> {
            String user = req.queryParams("usuario");
            String pass = req.queryParams("contrasena");
            Integer tipo = obtenerTipoUsuario(user, pass);
            
            if (tipo == null || tipo == 0) {
                res.status(401);
                return "Usuario o contraseña incorrectos. <a href='/login.html'>Volver</a>";
            }
            req.session().attribute("usuario", user);
            res.redirect("/index.html");
            return null;
        });

        // --- RESERVAR MESA (RESTAURANTE) ---
        post("/reservar-mesa", (req, res) -> {
            String user = req.session().attribute("usuario");
            if (user == null) { res.redirect("/login.html"); return null; }

            String personas = req.queryParams("personas");
            String fecha = req.queryParams("dia");
            String hora = req.queryParams("hora");
            String detalle = "Mesa para " + personas + " personas a las " + hora;

            guardarReserva(user, "Restaurante", fecha, detalle);
            res.redirect("/mis-reservas");
            return null;
        });

        // --- RESERVAR HABITACIÓN (HOTEL) ---
        post("/reservar-hotel", (req, res) -> {
            String user = req.session().attribute("usuario");
            if (user == null) { res.redirect("/login.html"); return null; }

            String habitacion = req.queryParams("habitacion");
            String entrada = req.queryParams("entrada");
            String detalle = "Habitación: " + habitacion + " (Check-in)";

            guardarReserva(user, "Hotel", entrada, detalle);
            res.redirect("/mis-reservas");
            return null;
        });

        // --- VER MIS RESERVAS ---
        get("/mis-reservas", (req, res) -> {
            String user = req.session().attribute("usuario");
            if (user == null) { res.redirect("/login.html"); return null; }

            StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'>");
            html.append("<style>body{font-family:sans-serif;padding:40px;background:#f4f4f4;}table{width:100%;border-collapse:collapse;background:white;}th,td{padding:12px;border:1px solid #ddd;text-align:left;}th{background:#333;color:white;}</style></head><body>");
            html.append("<h1>Mis Reservas, ").append(user).append("</h1>");
            html.append("<table><tr><th>Servicio</th><th>Fecha</th><th>Detalle</th></tr>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement("SELECT nombre_servicio, fecha_reserva, detalle FROM INSCRIPCIONES i JOIN USUARIOS u ON i.id_usuario = u.id_usuario WHERE u.usuario = ?")) {
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("nombre_servicio"))
                        .append("</td><td>").append(rs.getString("fecha_reserva"))
                        .append("</td><td>").append(rs.getString("detalle")).append("</td></tr>");
                }
            } catch (SQLException e) { return "Error: " + e.getMessage(); }

            html.append("</table><br><a href='/index.html'>Volver al inicio</a></body></html>");
            return html.toString();
        });
    }

    private static void guardarReserva(String usuario, String servicio, String fecha, String detalle) throws SQLException {
        String sql = "INSERT INTO INSCRIPCIONES (id_usuario, nombre_servicio, fecha_reserva, detalle) VALUES ((SELECT id_usuario FROM USUARIOS WHERE usuario = ?), ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, servicio);
            ps.setString(3, fecha);
            ps.setString(4, detalle);
            ps.executeUpdate();
        }
    }

    private static Integer obtenerTipoUsuario(String usuario, String contrasena) {
        String sql = "SELECT u.id_usuario, CASE WHEN e.id_usuario IS NOT NULL THEN 1 WHEN c.id_usuario IS NOT NULL THEN 2 ELSE 0 END as tipo FROM USUARIOS u LEFT JOIN EMPLEADO e ON u.id_usuario = e.id_usuario LEFT JOIN CLIENTES c ON u.id_usuario = c.id_usuario WHERE u.usuario = ? AND u.contrasena = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario); ps.setString(2, contrasena);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("tipo");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
}