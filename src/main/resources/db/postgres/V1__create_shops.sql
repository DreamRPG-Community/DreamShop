CREATE TABLE IF NOT EXISTS shops (
    id TEXT PRIMARY KEY,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    owner TEXT,
    admin_shop INTEGER NOT NULL,
    product TEXT NOT NULL,
    mythic_item_id TEXT,
    mythic_revision TEXT,
    price TEXT NOT NULL,
    shop_type TEXT NOT NULL,
    stock_mode TEXT NOT NULL,
    display_mode TEXT NOT NULL,
    status TEXT NOT NULL,
    invalid_reason TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS shops_world_position ON shops(world, x, y, z);

CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    shop_id TEXT NOT NULL,
    player TEXT NOT NULL,
    owner TEXT,
    shop_type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price TEXT NOT NULL,
    total_price TEXT NOT NULL,
    status TEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS transactions_shop ON transactions(shop_id, created_at);
