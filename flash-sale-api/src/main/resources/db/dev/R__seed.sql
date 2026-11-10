-- Development only. Apply via Flyway to a dedicated, idle database after migrations.
-- Source: local Citus snapshot. Password hashes are intentionally omitted.

INSERT INTO public.users (user_id, username, created_at) VALUES
    (1, 'Alice123', '2026-10-25 01:09:26.135070'),
    (2, 'bob', '2026-10-25 01:09:26.135070'),
    (3, 'charlie', '2026-10-25 01:09:26.135070'),
    (4, 'diana', '2026-10-25 01:09:26.135070'),
    (5, 'eve', '2026-10-25 01:09:26.135070')
ON CONFLICT DO NOTHING;

-- The source has no total_inventory column; 0 is a placeholder, not copied data.
INSERT INTO public.products (product_id, name, total_inventory, unit_price) VALUES
    (101, 'Wireless Headphones', 90, 149),
    (102, 'Smart Watch', 190, 199),
    (103, 'Mechanical Keyboard', 150, 99)
ON CONFLICT DO NOTHING;

INSERT INTO public.flash_sale_events (event_id, name, start_at, end_at, status, created_at) VALUES
    (1, 'Morning Flash Sale', '2026-10-25 00:09:31.605670', '2026-10-25 03:09:31.605670', 'RUNNING', '2026-10-25 01:09:31.605670'),
    (2, 'Evening Flash Sale', '2026-10-25 04:09:31.605670', '2026-10-25 07:09:31.605670', 'UPCOMING', '2026-10-25 01:09:31.605670')
ON CONFLICT DO NOTHING;

INSERT INTO public.flash_sale_event_products
    (event_product_id, event_id, product_id, allocated_quantity, sold_quantity, flash_price,
     per_user_limit, created_at, updated_at, reserved_quantity) VALUES
    (1001, 1, 101, 10, 0, 49.99, 1, '2026-10-25 01:09:31.634251', '2026-10-25 01:09:31.634251', 0),
    (1002, 1, 102, 5, 0, 79.99, 1, '2026-10-25 01:09:31.634251', '2026-10-25 01:09:31.634251', 0),
    (1003, 2, 101, 20, 0, 45.99, 1, '2026-10-25 01:09:31.634251', '2026-10-25 01:09:31.634251', 0),
    (1004, 2, 103, 15, 0, 59.99, 1, '2026-10-25 01:09:31.634251', '2026-10-25 01:09:31.634251', 0)
ON CONFLICT DO NOTHING;

-- Run only while application writers are stopped; do not move the sequence backward.
SELECT setval('public.orders_order_id_seq',
              GREATEST((SELECT last_value FROM public.orders_order_id_seq),
                       COALESCE((SELECT MAX(order_id) FROM public.orders), 1)), true);
