-- Serialize local seed runs so two backends starting together do not add duplicates.
LOCK TABLE restaurants IN SHARE ROW EXCLUSIVE MODE;

WITH closed_restaurant AS (
    INSERT INTO restaurants (name, accepts_orders)
    SELECT 'MTOGO Closed Restaurant', FALSE
    WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE name = 'MTOGO Closed Restaurant')
    RETURNING id
), closed_menu AS (
    INSERT INTO menus (restaurant_id, name)
    SELECT id, 'Menu' FROM closed_restaurant
    RETURNING id
)
INSERT INTO menu_items (menu_id, name, price, image_url)
SELECT id, 'Closed restaurant meal', 89.00, '/images/restaurant/menu/restaurant-1-item-1.jpg'
FROM closed_menu;

WITH kebab_menu AS (
    SELECT menus.id
    FROM menus JOIN restaurants ON restaurants.id = menus.restaurant_id
    WHERE restaurants.name = 'MTOGO Kebab' AND menus.name = 'Menu'
    ORDER BY menus.id
    LIMIT 1
)
INSERT INTO menu_items (menu_id, name, price, image_url, is_available)
SELECT id, 'Sold-out kebab', 89.00, '/images/restaurant/menu/restaurant-1-item-1.jpg', FALSE
FROM kebab_menu
WHERE NOT EXISTS (
    SELECT 1 FROM menu_items
    WHERE menu_items.menu_id = kebab_menu.id AND menu_items.name = 'Sold-out kebab'
);
