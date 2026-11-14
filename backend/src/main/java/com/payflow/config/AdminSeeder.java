package com.payflow.config;

import com.payflow.model.Role;
import com.payflow.model.User;
import com.payflow.model.Wallet;
import com.payflow.repository.UserRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the admin account on first start so the monitoring dashboard is usable immediately. */
@Component
@org.springframework.core.annotation.Order(1)
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, WalletRepository wallets, PasswordEncoder encoder,
                       @Value("${app.admin.email}") String email,
                       @Value("${app.admin.password}") String password) {
        this.users = users;
        this.wallets = wallets;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!users.existsByEmail(email)) {
            User admin = users.save(new User(email, encoder.encode(password), "PayFlow Admin", Role.ADMIN));
            wallets.save(new Wallet(admin));
            log.info("Seeded admin account {}", email);
        }
    }
}
