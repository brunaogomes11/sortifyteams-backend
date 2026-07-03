package com.gomesdev.sortifyteams.config;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.RoleEnum;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante a existência do usuário ADMIN do painel (/admin) a partir do .env
 * (ADMIN_USERNAME / ADMIN_PASSWORD). Não faz nada se as variáveis não
 * estiverem definidas ou se o usuário já existir.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminSeeder(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.username:}") String username,
                       @Value("${app.admin.password:}") String password,
                       @Value("${app.admin.email:admin@racha.local}") String email) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("AdminSeeder: ADMIN_USERNAME/ADMIN_PASSWORD não definidos — nenhum admin criado.");
            return;
        }
        if (usuarioRepository.existsByUsername(username) || usuarioRepository.existsByEmail(email)) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNomeCompleto("Administrador");
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setSenha(passwordEncoder.encode(password));
        admin.setRole(RoleEnum.ADMIN);
        admin.setStatus(StatusUsuarioEnum.APROVADO);
        usuarioRepository.save(admin);
        log.info("AdminSeeder: usuário admin '{}' criado.", username);
    }
}
