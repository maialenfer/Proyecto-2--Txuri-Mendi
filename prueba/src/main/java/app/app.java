package app;

import static spark.Spark.*;
import java.sql.*;

public class app {
    // Credenciales mantenidas tal cual las pasaste
    private static final String DB_URL = "jdbc:mysql://mysql-8001.dinaserver.com/txurimendi?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "txurimendi";
    private static final String DB_PASS = "Pmnu9Y,0.[41"; 

    public static void main(String[] args) {
        port(4567);

        staticFiles.location("/public");

        get("/", (req, res) -> {
            res.redirect("/login.html");
            return null;
        });

        post("/login", (req, res) -> {
            String user = req.queryParams("usuario");
            String pass = req.queryParams("contrasena");

            Integer tipo = obtenerTipoUsuario(user, pass);

            if (tipo == null || tipo == 0) {
                res.status(401);
                return "Usuario o contraseña incorrectos";
            }

            if (tipo == 1) {
                res.redirect("/datos.html");
            } else if (tipo == 2) {
                res.redirect("/index.html");
            } else {
                return "Usuario reconocido pero sin permisos asignados.";
            }
            return null;
        });
    }

    private static Integer obtenerTipoUsuario(String usuario, String contrasena) {
        // SQL Corregido: Busca en USUARIOS y detecta si está en EMPLEADO (1) o CLIENTES (2)
        // He mantenido "contraseña" con ñ porque así está en tu script de CREATE TABLE
        String sql = "SELECT u.id_usuario, " +
                     "CASE WHEN e.id_usuario IS NOT NULL THEN 1 " +
                     "     WHEN c.id_usuario IS NOT NULL THEN 2 " +
                     "     ELSE 0 END as tipo_calculado " +
                     "FROM USUARIOS u " +
                     "LEFT JOIN EMPLEADO e ON u.id_usuario = e.id_usuario " +
                     "LEFT JOIN CLIENTES c ON u.id_usuario = c.id_usuario " +
                     "WHERE u.usuario = ? AND u.contraseña = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, usuario);
            ps.setString(2, contrasena);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("tipo_calculado");
                }
            }
        } catch (SQLException e) {
            // Imprime el error por si falla la conexión a Dinahosting
            System.err.println("Error SQL: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}