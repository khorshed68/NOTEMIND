package com.notemind.config;

import com.notemind.entity.Enums.Role;
import com.notemind.entity.User;
import com.notemind.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/** Public registration can only create students, so the first admin is provisioned from the environment. */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    ApplicationRunner seedAdmin(AppProperties properties, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            AppProperties.Admin admin = properties.admin();
            if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
                return;
            }
            String email = admin.email().trim().toLowerCase(Locale.ROOT);
            if (users.existsByEmail(email)) {
                return;
            }
            User user = new User();
            user.setEmail(email);
            user.setName(StringUtils.hasText(admin.name()) ? admin.name() : "Administrator");
            user.setPasswordHash(encoder.encode(admin.password()));
            user.setRole(Role.ADMIN);
            users.save(user);
            log.info("Created initial admin account");
        };
    }
}
