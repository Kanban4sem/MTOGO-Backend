INSERT INTO restaurants (name, accepts_orders)
VALUES ('MTOGO Kebab', TRUE), ('MTOGO Bakery', TRUE);

INSERT INTO menus (restaurant_id, name, description)
SELECT id, 'Menu', 'Vores udvalg' FROM restaurants
WHERE name IN ('MTOGO Kebab', 'MTOGO Bakery');

INSERT INTO menu_items (menu_id, name, description, price, image_url)
SELECT menus.id, 'Kebabmenu', 'Kebab med pommes frites og sodavand', 89.00,
       '/images/restaurant/menu/restaurant-1-item-1.jpg'
FROM menus JOIN restaurants ON restaurants.id = menus.restaurant_id
WHERE restaurants.name = 'MTOGO Kebab';

INSERT INTO menu_items (menu_id, name, description, price, image_url)
SELECT menus.id, 'Croissant', 'Friskbagt croissant', 25.00,
       '/images/restaurant/menu/restaurant-2-item-1.jpg'
FROM menus JOIN restaurants ON restaurants.id = menus.restaurant_id
WHERE restaurants.name = 'MTOGO Bakery';
