-- Initial schema based on the coordinator's local business tables. Citus distribution is
-- intentionally absent: none of these tables is distributed or a reference table.

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password_hash varchar(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT users_pkey PRIMARY KEY (user_id)
);

CREATE TABLE public.products (
    product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    total_inventory integer NOT NULL,
    unit_price real NOT NULL DEFAULT 0.0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT products_total_inventory_check CHECK (total_inventory >= 0),
    CONSTRAINT products_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT products_pkey PRIMARY KEY (product_id)
);

CREATE TABLE public.flash_sale_events (
    event_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    start_at timestamp without time zone NOT NULL,
    end_at timestamp without time zone NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT flash_sale_events_check CHECK (end_at > start_at),
    CONSTRAINT flash_sale_events_status_check CHECK (status::text = ANY (ARRAY['UPCOMING'::character varying, 'RUNNING'::character varying, 'ENDED'::character varying, 'CANCELED'::character varying]::text[])),
    CONSTRAINT flash_sale_events_pkey PRIMARY KEY (event_id)
);

CREATE TABLE public.flash_sale_event_products (
    event_product_id bigint NOT NULL,
    event_id bigint NOT NULL,
    product_id bigint NOT NULL,
    allocated_quantity integer NOT NULL,
    sold_quantity integer DEFAULT 0 NOT NULL,
    flash_price numeric(10,2) NOT NULL,
    per_user_limit integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    reserved_quantity integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_reserved_le_allocated CHECK (reserved_quantity <= allocated_quantity),
    CONSTRAINT chk_sold_plus_reserved_le_allocated CHECK (sold_quantity + reserved_quantity <= allocated_quantity),
    CONSTRAINT flash_sale_event_products_allocated_quantity_check CHECK (allocated_quantity > 0),
    CONSTRAINT flash_sale_event_products_check CHECK (sold_quantity <= allocated_quantity),
    CONSTRAINT flash_sale_event_products_per_user_limit_check CHECK (per_user_limit >= 1),
    CONSTRAINT flash_sale_event_products_reserved_quantity_check CHECK (reserved_quantity >= 0),
    CONSTRAINT flash_sale_event_products_sold_quantity_check CHECK (sold_quantity >= 0),
    CONSTRAINT flash_sale_event_products_pkey PRIMARY KEY (event_product_id),
    CONSTRAINT flash_sale_event_products_event_id_product_id_key UNIQUE (event_id, product_id),
    CONSTRAINT flash_sale_event_products_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.flash_sale_events(event_id) ON DELETE RESTRICT,
    CONSTRAINT flash_sale_event_products_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT
);

CREATE TABLE public.orders (
    order_id bigint NOT NULL,
    user_id bigint NOT NULL,
    event_id bigint,
    event_product_id bigint,
    status character varying(20) NOT NULL,
    idempotency_key character varying(128),
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT orders_status_check CHECK (status::text = ANY (ARRAY['PENDING_PAYMENT'::character varying, 'PAID'::character varying, 'CANCELED'::character varying, 'EXPIRED'::character varying, 'FAILED'::character varying]::text[])),
    CONSTRAINT orders_pkey PRIMARY KEY (order_id),
    CONSTRAINT orders_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT orders_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.flash_sale_events(event_id) ON DELETE RESTRICT,
    CONSTRAINT orders_event_product_id_fkey FOREIGN KEY (event_product_id) REFERENCES public.flash_sale_event_products(event_product_id) ON DELETE RESTRICT,
    CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE RESTRICT
);

CREATE SEQUENCE public.orders_order_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.orders_order_id_seq OWNED BY public.orders.order_id;
ALTER TABLE ONLY public.orders ALTER COLUMN order_id SET DEFAULT nextval('public.orders_order_id_seq'::regclass);

CREATE TABLE public.order_items (
    order_item_id bigint NOT NULL,
    order_id bigint NOT NULL,
    product_id bigint NOT NULL,
    event_product_id bigint,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price_snapshot numeric(10,2) NOT NULL,
    CONSTRAINT order_items_quantity_check CHECK (quantity > 0),
    CONSTRAINT order_items_unit_price_snapshot_check CHECK (unit_price_snapshot >= 0::numeric),
    CONSTRAINT order_items_pkey PRIMARY KEY (order_item_id),
    CONSTRAINT order_items_event_product_id_fkey FOREIGN KEY (event_product_id) REFERENCES public.flash_sale_event_products(event_product_id) ON DELETE RESTRICT,
    CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE CASCADE,
    CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE RESTRICT
);

CREATE SEQUENCE public.order_items_order_item_id_seq AS bigint;
ALTER SEQUENCE public.order_items_order_item_id_seq OWNED BY public.order_items.order_item_id;
ALTER TABLE ONLY public.order_items ALTER COLUMN order_item_id
    SET DEFAULT nextval('public.order_items_order_item_id_seq'::regclass);

CREATE TABLE public.order_payment (
    payment_id bigint NOT NULL,
    order_id bigint NOT NULL,
    amount numeric(10,2) NOT NULL,
    payment_method character varying(30),
    status character varying(20) NOT NULL,
    transaction_id character varying(128),
    created_at timestamp without time zone DEFAULT now(),
    paid_at timestamp without time zone,
    CONSTRAINT order_payment_amount_check CHECK (amount >= 0::numeric),
    CONSTRAINT order_payment_status_check CHECK (status::text = ANY (ARRAY['INIT'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying]::text[])),
    CONSTRAINT order_payment_pkey PRIMARY KEY (payment_id),
    CONSTRAINT order_payment_transaction_id_key UNIQUE (transaction_id),
    CONSTRAINT order_payment_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(order_id) ON DELETE RESTRICT
);
