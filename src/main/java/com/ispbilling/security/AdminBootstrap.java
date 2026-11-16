package com.ispbilling.security;

import com.ispbilling.common.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/** Hiç kullanıcı yoksa ilk yöneticiyi oluşturur. */
@Component
@org.springframework.core.annotation.Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final BillingProperties props;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder, BillingProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String username = props.admin() != null && StringUtils.hasText(props.admin().username())
                ? props.admin().username() : "admin";
        String password = props.admin() != null ? props.admin().password() : null;
        boolean generated = !StringUtils.hasText(password);
        if (generated) {
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        users.save(new AppUser(username, encoder.encode(password), "Yönetici", AppUser.Role.ADMIN));
        if (generated) {
            log.warn("İlk yönetici oluşturuldu. Kullanıcı: {}  Şifre: {}  (BILLING_ADMIN_PASSWORD ile belirleyebilirsiniz)",
                    username, password);
        } else {
            log.info("İlk yönetici oluşturuldu: {}", username);
        }
    }
}
