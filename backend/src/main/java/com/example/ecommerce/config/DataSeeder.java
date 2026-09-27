package com.example.ecommerce.config;

import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.Role;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds the database with a development admin, two customers, and a product
 * catalog so the API is immediately usable after `docker compose up` or
 * `mvn spring-boot:run`. Only runs if the users table is empty, so it is
 * safe to leave enabled across restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed data already present, skipping seeding.");
            return;
        }

        log.info("Seeding development data...");

        User admin = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin@ecommerce.com")
                .password(passwordEncoder.encode("Admin@123"))
                .role(Role.ADMIN)
                .build());
        cartRepository.save(Cart.builder().user(admin).build());

        User customer1 = userRepository.save(User.builder()
                .name("Asha Verma")
                .email("asha@example.com")
                .password(passwordEncoder.encode("Customer@123"))
                .role(Role.CUSTOMER)
                .build());
        cartRepository.save(Cart.builder().user(customer1).build());

        User customer2 = userRepository.save(User.builder()
                .name("Rahul Mehta")
                .email("rahul@example.com")
                .password(passwordEncoder.encode("Customer@123"))
                .role(Role.CUSTOMER)
                .build());
        cartRepository.save(Cart.builder().user(customer2).build());

        productRepository.saveAll(java.util.List.of(
                product("Wireless Mouse", "Ergonomic 2.4GHz wireless mouse", "1299", 50, "Electronics"),
                product("Mechanical Keyboard", "RGB backlit mechanical keyboard, blue switches", "3499", 30, "Electronics"),
                product("USB-C Hub", "7-in-1 USB-C hub with HDMI and SD card reader", "1899", 40, "Electronics"),
                product("Noise Cancelling Headphones", "Over-ear ANC headphones, 30h battery", "6999", 20, "Electronics"),
                product("Men's Running Shoes", "Lightweight breathable running shoes", "2499", 60, "Footwear"),
                product("Women's Yoga Pants", "High-waist stretch yoga leggings", "1199", 80, "Apparel"),
                product("Cotton T-Shirt", "100% cotton crew-neck t-shirt", "499", 150, "Apparel"),
                product("Stainless Steel Water Bottle", "1L insulated water bottle", "799", 100, "Home & Kitchen"),
                product("Non-Stick Frying Pan", "28cm non-stick frying pan", "1099", 45, "Home & Kitchen"),
                product("Novel: The Silent Ocean", "Bestselling fiction novel, paperback", "399", 200, "Books"),
                product("Desk Lamp", "LED desk lamp with adjustable brightness", "899", 70, "Home & Kitchen"),
                product("Backpack 30L", "Water-resistant travel and laptop backpack", "1799", 55, "Bags")
        ));

        log.info("Seed data created: 1 admin, 2 customers, 12 products.");
        log.info("Admin login    -> email: admin@ecommerce.com   password: Admin@123");
        log.info("Customer login -> email: asha@example.com      password: Customer@123");
        log.info("Customer login -> email: rahul@example.com     password: Customer@123");
    }

    private Product product(String name, String description, String price, int stock, String category) {
        return Product.builder()
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .stockQuantity(stock)
                .category(category)
                .imageUrl(null)
                .build();
    }
}
