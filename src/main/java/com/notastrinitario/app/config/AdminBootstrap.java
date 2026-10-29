package com.notastrinitario.app.config;

import com.notastrinitario.app.entity.Role;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.RoleRepository;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.security.PasswordSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Crea los roles y, si todavia no existe ningun ADMIN, un usuario administrador inicial.
 *
 * - Usuario: variable ADMIN_USERNAME (por defecto "admin").
 * - Clave:   variable ADMIN_PASSWORD; si no existe se genera una aleatoria y se
 *            imprime UNA SOLA VEZ en los logs. Nunca queda escrita en el codigo.
 * Si ya existe un ADMIN no hace nada, asi que es seguro en cada reinicio.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminBootstrap(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String nombre : new String[] { "ADMIN", "TEACHER", "PARENT" }) {
                if (roleRepository.findByName(nombre) == null) {
                    Role r = new Role();
                    r.setName(nombre);
                    roleRepository.save(r);
                }
            }

            if (!userRepository.findByRoleName("ADMIN").isEmpty()) {
                return;
            }

            String username = envOr("ADMIN_USERNAME", "admin");
            String email = envOr("ADMIN_EMAIL", "admin@notastrinitario.local");
            String password = System.getenv("ADMIN_PASSWORD");
            boolean generada = false;
            if (password == null || password.length() < 8) {
                password = generarClave(16);
                generada = true;
            }

            User admin = new User();
            admin.setName("Administrador");
            admin.setSurname("Sistema");
            admin.setUsername(username);
            admin.setEmail(email);
            admin.setPassword(PasswordSecurity.hash(password));
            admin.setRole(roleRepository.findByName("ADMIN"));
            admin.setEnable(true);
            userRepository.save(admin);

            log.warn("==================================================================");
            log.warn(" ADMINISTRADOR INICIAL CREADO");
            log.warn(" Usuario: {}", username);
            if (generada) {
                log.warn(" Clave:   {}   (anotala ahora: no se vuelve a mostrar)", password);
            } else {
                log.warn(" Clave:   la definida en ADMIN_PASSWORD");
            }
            log.warn("==================================================================");
        } catch (Exception e) {
            log.error("No se pudo crear el administrador inicial: {}", e.getMessage(), e);
        }
    }

    private static String envOr(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return (v == null || v.isBlank()) ? porDefecto : v.trim();
    }

    private static String generarClave(int largo) {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            sb.append(ALFABETO.charAt(rnd.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
