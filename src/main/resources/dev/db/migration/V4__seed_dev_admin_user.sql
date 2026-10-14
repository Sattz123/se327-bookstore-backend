-- Development Seed Data: Provision development Admin and Customer accounts
-- Admin Credentials:
--   Email:    admin@bookstore.com
--   Password: AdminPassword123!
--   Role:     ADMIN
-- Customer Credentials:
--   Email:    customer@bookstore.com
--   Password: CustomerPassword123!
--   Role:     CUSTOMER

INSERT INTO users (id, username, email, password_hash, phone, role) VALUES
(1, 'Admin User', 'admin@bookstore.com', '$2a$10$ojP6cO1LJ0zXf2cmCOhhmOhu.o8sESO002S6RrDxyTBN3tsm5vTr6', '0812345678', 'ADMIN'),
(2, 'John Smith', 'customer@bookstore.com', '$2a$10$Y0JoZPNNXt/bnuItn/Ajm.M4xyq0F4RZeqPbSdphu2dqeNRzQck5e', '0898765432', 'CUSTOMER')
ON CONFLICT (email) DO NOTHING;

-- Advance PostgreSQL identity sequence past explicit IDs
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT max(id) FROM users), 1));
